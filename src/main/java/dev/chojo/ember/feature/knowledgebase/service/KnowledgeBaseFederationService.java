/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationShare;
import dev.chojo.ember.feature.federation.entity.ShareScope;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationDisplayNames;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.KbAccessGrant;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileSummary;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
import dev.chojo.ember.feature.knowledgebase.entity.KbFolder;
import dev.chojo.ember.feature.knowledgebase.repository.KbCommentRepository;
import dev.chojo.ember.feature.knowledgebase.route.RemoteKnowledgeBaseRoutes;
import dev.chojo.ember.feature.knowledgebase.route.RemoteKnowledgeBaseRoutes.RemoteKbFile;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Federated access to knowledge-base content: browsing and searching what partners share, reading
 * a single partner file, copying it into the local station, serving this station's shared files to
 * a requesting partner, and proxying comments across the partnership.
 * <p>
 * Partner resolution and the local/remote split live here so route handlers never branch on
 * {@link FederationPartner#isRemote()} themselves.
 */
@Singleton
public class KnowledgeBaseFederationService {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseFederationService.class);

    private final KnowledgeBaseService knowledgeBaseService;
    private final KbContentService contentService;
    private final KbSearchService searchService;
    private final FederationService federationService;
    private final FederationRepository federationRepository;
    private final FederationHttpClient httpClient;
    private final StationRepository stationRepository;
    private final KbCommentRepository commentRepository;
    private final EventFederationRepository eventFederationRepository;
    private final MemberNameResolver memberNameResolver;
    private final FederationFanout fanout;
    private final FederationEntityResolver entityResolver;
    private final KbPdfExportService pdfExportService;
    private final KbAccessService accessService;

    @Inject
    public KnowledgeBaseFederationService(
            KnowledgeBaseService knowledgeBaseService,
            KbContentService contentService,
            KbSearchService searchService,
            FederationService federationService,
            FederationRepository federationRepository,
            FederationHttpClient httpClient,
            StationRepository stationRepository,
            KbCommentRepository commentRepository,
            EventFederationRepository eventFederationRepository,
            MemberNameResolver memberNameResolver,
            FederationFanout fanout,
            FederationEntityResolver entityResolver,
            KbPdfExportService pdfExportService,
            KbAccessService accessService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.contentService = contentService;
        this.searchService = searchService;
        this.federationService = federationService;
        this.federationRepository = federationRepository;
        this.httpClient = httpClient;
        this.stationRepository = stationRepository;
        this.commentRepository = commentRepository;
        this.eventFederationRepository = eventFederationRepository;
        this.memberNameResolver = memberNameResolver;
        this.fanout = fanout;
        this.entityResolver = entityResolver;
        this.pdfExportService = pdfExportService;
        this.accessService = accessService;
    }

    /**
     * Browses every file shared with a station, enriched with the name of the station that serves
     * it. Local partners are read from the database, remote partners over signed HTTP, all in
     * parallel.
     *
     * @param stationId the browsing station ID
     * @return the shared files of all partners that answered
     */
    public FederatedKbBrowse browseFederatedKb(int stationId, StationUserType readerUserType) {
        var gathered = browseSharedKb(stationId);
        return new FederatedKbBrowse(
                named(gathered.folders()).stream()
                        .filter(folder -> mayRead(folder.userTypes(), readerUserType))
                        .toList(),
                namedFiles(gathered.files()).stream()
                        .filter(file -> mayRead(file.userTypes(), readerUserType))
                        .toList(),
                List.of());
    }

    /**
     * Whether a reader's own user type is one an entry names.
     *
     * <p>An entry that names none is for everybody. One that names some is for the readers of those types
     * at the stations it reached, which is the whole of what a user type means across a share: the type is
     * the reader's, held at their own station.
     */
    private static boolean mayRead(List<String> userTypes, StationUserType readerUserType) {
        if (userTypes == null || userTypes.isEmpty()) return true;
        return readerUserType != null && userTypes.contains(readerUserType.name());
    }

    /**
     * What is inside one folder a partner shares.
     *
     * @param stationId         the reading station
     * @param partnerStationUid the partner serving the folder
     * @param folderId          the folder being opened
     */
    public FederatedKbBrowse browseFederatedKbFolder(
            int stationId, UUID partnerStationUid, int folderId, StationUserType readerUserType) {
        var level = entityResolver.resolve(
                stationId,
                partnerStationUid,
                RemoteKnowledgeBaseRoutes.BROWSE_KB_FOLDER.at(folderId),
                RemoteKbBrowse.class,
                "folder",
                partner -> folderLevel(partnerStationId(partner), folderId, servingSideId(partner)));
        var partner = federationRepository
                .findPartnerByStationAndRemoteUid(stationId, partnerStationUid)
                .orElseThrow(NotFoundResponse::new);
        String stationName = FederationDisplayNames.partnerName(stationRepository, partner, "Unknown");
        String uid = partnerStationUid.toString();
        return new FederatedKbBrowse(
                level.folders().stream()
                        .filter(folder -> mayRead(folder.userTypes(), readerUserType))
                        .map(folder -> new FederatedKbFolder(
                                folder.id(),
                                folder.name(),
                                folder.description(),
                                stationName,
                                uid,
                                partner.id(),
                                folder.userTypes()))
                        .toList(),
                level.files().stream()
                        .filter(file -> mayRead(file.userTypes(), readerUserType))
                        .map(file -> new FederatedKbItem(
                                file.id(),
                                file.name(),
                                file.description(),
                                stationName,
                                uid,
                                partner.id(),
                                file.userTypes()))
                        .toList(),
                level.trail().stream()
                        .map(step -> new FederatedKbFolder(
                                step.id(),
                                step.name(),
                                step.description(),
                                stationName,
                                uid,
                                partner.id(),
                                step.userTypes()))
                        .toList());
    }

    private List<FederatedKbFolder> named(List<SharedKbFolder> folders) {
        return folders.stream()
                .map(folder -> {
                    var partner = federationRepository
                            .findPartnerById(folder.partnerId())
                            .orElse(null);
                    return new FederatedKbFolder(
                            folder.id(),
                            folder.name(),
                            folder.description(),
                            FederationDisplayNames.partnerName(stationRepository, partner, "Unknown"),
                            partner != null ? partner.partnerStationId().toString() : null,
                            folder.partnerId(),
                            folder.userTypes());
                })
                .toList();
    }

    private List<FederatedKbItem> namedFiles(List<SharedKbItem> items) {
        return items.stream()
                .map(item -> {
                    var partner = federationRepository
                            .findPartnerById(item.partnerId())
                            .orElse(null);
                    String stationName = FederationDisplayNames.partnerName(stationRepository, partner, "Unknown");
                    return new FederatedKbItem(
                            item.file().id(),
                            item.file().name(),
                            item.file().description() != null ? item.file().description() : "",
                            stationName,
                            partner != null ? partner.partnerStationId().toString() : null,
                            item.partnerId(),
                            item.userTypes());
                })
                .toList();
    }

    /**
     * Collects the files every active partner shares with a station.
     *
     * @param stationId the browsing station ID
     * @return the shared files with their owning station and partnership
     */
    public SharedKbLevel browseSharedKb(int stationId) {
        var levels = fanout.fanOut(
                sharedKbPartners(stationId),
                partner -> List.of(browseSharedKbDirect(partnerStationId(partner), partner)),
                partner -> List.of(browseSharedKbViaHttp(stationId, partner, partnerStationId(partner))));
        return new SharedKbLevel(
                levels.stream().flatMap(level -> level.folders().stream()).toList(),
                levels.stream().flatMap(level -> level.files().stream()).toList());
    }

    /**
     * Runs a search across every active partner sharing knowledge-base content.
     *
     * @param stationId the searching station ID
     * @param query     the search terms
     * @return the matches of all partners that answered
     */
    public List<FederatedSearchResult> searchFederatedKb(int stationId, String query) {
        return fanout.fanOut(
                sharedKbPartners(stationId),
                partner -> searchKbDirect(partner, query),
                partner -> searchKbViaHttp(stationId, partner, query));
    }

    /**
     * Fetches a single knowledge-base file from a federated partner, transparently handling
     * partners on this instance and on another one.
     */
    public RemoteKbFile getFederatedKbFile(int localStationId, UUID partnerStationUid, int fileId) {
        return entityResolver.resolve(
                localStationId,
                partnerStationUid,
                RemoteKnowledgeBaseRoutes.GET_FILE.at(fileId),
                RemoteKbFile.class,
                "file",
                partner -> RemoteKbFile.of(requirePartnerFile(fileId, partner), partnerStationUid));
    }

    /**
     * Renders a partner's knowledge-base file as a PDF, headed with the partner's name.
     *
     * @param localStationId    the reading station ID
     * @param partnerStationUid the partner station UUID
     * @param fileId            the file to render
     * @param generatedBy       the name of the person requesting the export
     * @return the rendered PDF
     * @throws BadRequestResponse when the file has no written body to render
     */
    public RenderedPdf renderFederatedKbFilePdf(
            int localStationId, UUID partnerStationUid, int fileId, String generatedBy)
            throws IOException, InterruptedException {
        var file = getFederatedKbFile(localStationId, partnerStationUid, fileId);
        if (!KbPdfExportService.isExportable(file.fileType())) {
            throw new BadRequestResponse("Only markdown and text files can be rendered as PDF");
        }
        String content = getFederatedKbFileContent(localStationId, partnerStationUid, fileId);
        var partner = federationRepository
                .findPartnerByStationAndRemoteUid(localStationId, partnerStationUid)
                .orElse(null);
        byte[] pdf = pdfExportService.renderFederated(
                new KbPdfExportService.ExportSource(
                        file.name(), file.description(), content, file.fileType() == KbFileType.MARKDOWN),
                FederationDisplayNames.partnerName(stationRepository, partner, "?"),
                stationRepository.findById(localStationId).orElse(null),
                generatedBy);
        return new RenderedPdf(file.name() + ".pdf", pdf);
    }

    /**
     * A rendered document together with the name it should be saved under.
     */
    public record RenderedPdf(String fileName, byte[] data) {}

    /**
     * Loads a file for a requesting partner in the shape the federation contract publishes, refusing
     * files that belong to another station.
     */
    public RemoteKbFile remoteFileForPartner(FederationPartner partner, int fileId) {
        var file = fileForPartner(partner, fileId);
        return RemoteKbFile.of(file, stationRepository.resolveUid(file.stationId()));
    }

    /**
     * Fetches the text content of a knowledge-base file from a federated partner, transparently
     * handling partners on this instance and on another one.
     */
    public String getFederatedKbFileContent(int localStationId, UUID partnerStationUid, int fileId) {
        return entityResolver.resolve(
                localStationId,
                partnerStationUid,
                partner -> {
                    var file = requirePartnerFile(fileId, partner);
                    return contentService.getMarkdownContent(file.id()).orElse("");
                },
                partner -> fetchKbFileContent(
                        partner.remoteHost(),
                        partner.partnerStationId(),
                        fileId,
                        localStationId,
                        privateKey(localStationId)));
    }

    /**
     * Copies a partner's knowledge-base file into the target station, keeping a source reference
     * and carrying over the caller's favourite marking.
     *
     * @param fileId          the source file ID
     * @param targetStationId the station receiving the copy
     * @param createdBy       the member performing the copy
     * @return the created copy
     */
    public KbFile copyKbFile(int fileId, int targetStationId, int createdBy) {
        var source = knowledgeBaseService.findFile(fileId).orElseThrow();
        String content;
        var partner = findPartnerForStation(targetStationId, source.stationId());
        if (partner != null && partner.isRemote()) {
            content = fetchKbFileContent(
                    partner.remoteHost(),
                    partner.partnerStationId(),
                    fileId,
                    targetStationId,
                    privateKey(targetStationId));
        } else {
            content = contentService.getMarkdownContent(fileId).orElse("");
        }
        var copied = knowledgeBaseService.createMarkdownFile(
                targetStationId, null, source.name(), source.description(), content, createdBy);
        knowledgeBaseService.setSourceReference(copied.id(), source.id(), source.stationId());
        if (knowledgeBaseService.isFavourite(createdBy, fileId)) {
            knowledgeBaseService.addFavourite(createdBy, copied.id());
        }
        log.info(
                "KB file {} copied from file {} (station {}) into station {} by member {}",
                copied.id(),
                source.id(),
                source.stationId(),
                targetStationId,
                createdBy);
        return knowledgeBaseService.findFile(copied.id()).orElseThrow();
    }

    /**
     * Lists the files this station shares with a requesting partner.
     *
     * @param partner the verified requesting partner
     * @return the shared files in their list representation
     */
    public RemoteKbBrowse browseForPartner(FederationPartner partner) {
        return servedLevel(partner.stationId(), partner.id());
    }

    /**
     * Shares a knowledge folder or article, with everybody or with named stations.
     *
     * <p>Naming stations on a folder names them for everything under it, and an entry inside may narrow
     * that set but not widen it: a folder for two stations holding an article for a third is a
     * contradiction, and the answer to it is a refusal rather than a guess about which one wins.
     *
     * @param stationId  the station sharing
     * @param fileId     the article, or {@code null} when sharing a folder
     * @param folderId   the folder, or {@code null} when sharing an article
     * @param scope      everybody, or the named stations
     * @param partnerIds the partnerships it is for, read only when the scope names stations
     */
    public FederationShare shareEntry(
            int stationId, Integer fileId, Integer folderId, ShareScope scope, List<Integer> partnerIds) {
        Integer parent = fileId != null
                ? knowledgeBaseService.findFile(fileId).map(KbFile::folderId).orElse(null)
                : knowledgeBaseService
                        .findFolder(folderId)
                        .map(KbFolder::parentId)
                        .orElse(null);
        var reachable = inheritedAim(stationId, parent);
        if (reachable != null) {
            if (scope != ShareScope.SPECIFIC) {
                throw new BadRequestResponse("The folder above this is shared with named stations only");
            }
            var widened =
                    partnerIds.stream().filter(id -> !reachable.contains(id)).toList();
            if (!widened.isEmpty()) {
                throw new BadRequestResponse("The folder above this does not reach every station named");
            }
        }
        return federationService.createKbShare(stationId, fileId, folderId, scope, partnerIds);
    }

    /**
     * The stations the nearest shared folder above reaches, or {@code null} when nothing above narrows
     * anything: either no folder above is shared, or one is shared with everybody.
     */
    private Set<Integer> inheritedAim(int stationId, Integer folderId) {
        var shares = federationRepository.findKbShares(stationId);
        for (Integer id = folderId; id != null; ) {
            for (var share : shares) {
                if (!Objects.equals(share.folderId(), id)) continue;
                if (share.shareScope() != ShareScope.SPECIFIC) return null;
                return Set.copyOf(federationRepository.findKbShareTargets(share.id()));
            }
            var folder = knowledgeBaseService.findFolder(id).orElse(null);
            if (folder == null) return null;
            id = folder.parentId();
        }
        return null;
    }

    /**
     * Which stations each entry of one station's wiki is for.
     *
     * @param stationId the station whose shares these are
     * @return one entry per share, with the stations it names
     */
    public List<EntryAudience> findAudiences(int stationId) {
        return federationRepository.findKbShares(stationId).stream()
                .map(share -> new EntryAudience(
                        share.id(),
                        share.fileId(),
                        share.folderId(),
                        share.shareScope(),
                        federationRepository.findKbShareTargets(share.id())))
                .toList();
    }

    /**
     * Says which stations one entry is for, replacing whatever it said before.
     *
     * <p>The old share goes only once the new one exists. A refusal in between, which the folder rule can
     * raise, would otherwise leave the entry shared with nobody: not what anybody asked for, and invisible
     * until somebody at a station notices an article has gone.
     *
     * @param stationId  the station sharing
     * @param fileId     the article, or {@code null} when it is a folder
     * @param folderId   the folder, or {@code null} when it is an article
     * @param scope      everybody, or the stations named
     * @param partnerIds the stations named, as the partnerships that address them
     */
    public void setAudience(
            int stationId, Integer fileId, Integer folderId, ShareScope scope, List<Integer> partnerIds) {
        setAudience(stationId, fileId, folderId, true, scope, partnerIds);
    }

    /**
     * Says who one entry is for, replacing whatever it said before, including saying nobody.
     *
     * <p>Not being shared at all is a state of its own and the one most entries of a station are in. It is
     * not the same as being shared with an empty list of stations, and a screen that could only choose
     * between everybody and a chosen few would quietly share everything it saved.
     *
     * @param shared whether the entry leaves this station at all
     */
    public void setAudience(
            int stationId,
            Integer fileId,
            Integer folderId,
            boolean shared,
            ShareScope scope,
            List<Integer> partnerIds) {
        if ((fileId == null) == (folderId == null)) {
            throw new BadRequestResponse("Name either an article or a folder");
        }
        var existing = federationRepository.findKbShares(stationId).stream()
                .filter(share -> fileId != null
                        ? Objects.equals(share.fileId(), fileId)
                        : Objects.equals(share.folderId(), folderId))
                .toList();

        if (shared) shareEntry(stationId, fileId, folderId, scope, partnerIds);
        for (var share : existing) {
            federationRepository.deleteKbShare(share.id(), stationId);
        }
    }

    /**
     * The entries this station shares with named stations rather than with every partner.
     *
     * <p>An entry shared with everybody reaches past this station but says nothing about who: it is
     * simply out there, which is not the same thing as being aimed. Only an aimed one is marked.
     *
     * @param stationId the station whose shares these are
     * @param folders   whether to answer about folders rather than articles
     */
    public Set<Integer> narrowlyShared(int stationId, boolean folders) {
        return sharedIds(stationId, folders, true);
    }

    /**
     * The entries this station shares with every one of its partners.
     *
     * <p>Distinct from sharing with a chosen few, and distinct again from keeping something to this
     * station. Without it the two ends of that look the same on a tile: an entry the whole federation
     * reads carried no mark at all, exactly like one nobody outside can see.
     *
     * @param stationId the station whose shares these are
     * @param folders   whether to answer about folders rather than articles
     */
    public Set<Integer> broadlyShared(int stationId, boolean folders) {
        return sharedIds(stationId, folders, false);
    }

    private Set<Integer> sharedIds(int stationId, boolean folders, boolean aimed) {
        return federationRepository.findKbShares(stationId).stream()
                .filter(share -> (share.shareScope() == ShareScope.SPECIFIC) == aimed)
                .map(share -> folders ? share.folderId() : share.fileId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** One share of a wiki entry, with the stations it names. */
    public record EntryAudience(int id, Integer fileId, Integer folderId, ShareScope scope, List<Integer> partnerIds) {}

    /**
     * The shares of one station that reach one reader.
     *
     * <p>A share for everybody reaches every partner. A share naming stations reaches the ones named, which
     * is how an association says an entry is for some of its stations and not the rest.
     *
     * @param servingStationId  the station whose shares these are
     * @param readingPartnerId  the partnership the reader arrives on, or {@code null} to ignore the aim
     */
    private List<FederationShare> sharesReaching(int servingStationId, Integer readingPartnerId) {
        return federationRepository.findKbShares(servingStationId).stream()
                .filter(share -> reaches(share, readingPartnerId))
                .toList();
    }

    private boolean reaches(FederationShare share, Integer readingPartnerId) {
        if (share.shareScope() != ShareScope.SPECIFIC) return true;
        if (readingPartnerId == null) return true;
        return federationRepository.findKbShareTargets(share.id()).contains(readingPartnerId);
    }

    /**
     * The top of what one station shares: its shared folders, and the articles shared in their own right.
     *
     * <p>Keyed by the serving station rather than by a partnership, because a partnership row means the
     * opposite thing on each side of it: serving a partner, {@code stationId} is this station, and reading
     * a partner it is the one doing the reading.
     */
    private RemoteKbBrowse servedLevel(int servingStationId, Integer readingPartnerId) {
        var shares = sharesReaching(servingStationId, readingPartnerId);
        var sharedFolders = sharedFolderIds(servingStationId, readingPartnerId);

        // A folder inside a shared folder is reached by opening the one above it, not by standing on its own
        // at the top. Only the outermost shared folders belong here.
        var folders = shares.stream()
                .map(FederationShare::folderId)
                .filter(Objects::nonNull)
                .distinct()
                .flatMap(id -> knowledgeBaseService.findFolder(id).stream())
                .filter(folder -> folder.stationId() == servingStationId)
                .filter(folder -> !isInsideAnyOf(folder.parentId(), sharedFolders))
                .map(this::summary)
                .toList();

        // An article whose folder is shared arrives inside that folder, so offering it here as well would
        // show it twice, once loose and once where it belongs.
        var files = shares.stream()
                .map(FederationShare::fileId)
                .filter(Objects::nonNull)
                .distinct()
                .flatMap(id -> knowledgeBaseService.findFile(id).stream())
                .filter(file -> file.stationId() == servingStationId)
                .filter(file -> !isInsideAnyOf(file.folderId(), sharedFolders))
                .map(this::summary)
                .toList();

        return new RemoteKbBrowse(folders, files, List.of());
    }

    /**
     * What is inside one shared folder, for a partner that may open it.
     *
     * @param partner  the requesting partner
     * @param folderId the folder being opened
     * @return its subfolders and its articles
     */
    public RemoteKbBrowse folderForPartner(FederationPartner partner, int folderId) {
        return folderLevel(partner.stationId(), folderId, partner.id());
    }

    /** What is inside one shared folder of a serving station, refused unless a share reaching the reader covers it. */
    private RemoteKbBrowse folderLevel(int servingStationId, int folderId, Integer readingPartnerId) {
        var folder = knowledgeBaseService.findFolder(folderId).orElseThrow(NotFoundResponse::new);
        if (folder.stationId() != servingStationId || !isFolderShared(servingStationId, folderId, readingPartnerId)) {
            throw new NotFoundResponse();
        }
        return new RemoteKbBrowse(
                knowledgeBaseService.findFolders(servingStationId, folderId).stream()
                        .map(this::summary)
                        .toList(),
                knowledgeBaseService.findFiles(servingStationId, folderId).stream()
                        .map(this::summary)
                        .toList(),
                trailTo(servingStationId, folder, readingPartnerId));
    }

    /**
     * The way back out of a shared folder: the outermost folder the reader was given, down to the one they
     * are standing in.
     *
     * <p>It stops at the outermost share rather than at the owning station's root. What lies above that is
     * not shared, and naming it in a trail would say more about the other station's wiki than it agreed to.
     */
    private List<RemoteKbFolderSummary> trailTo(int servingStationId, KbFolder folder, Integer readingPartnerId) {
        var trail = new ArrayList<RemoteKbFolderSummary>();
        for (KbFolder step = folder; step != null; ) {
            trail.addFirst(summary(step));
            Integer parentId = step.parentId();
            if (parentId == null || !isFolderShared(servingStationId, parentId, readingPartnerId)) break;
            step = knowledgeBaseService.findFolder(parentId).orElse(null);
        }
        return trail;
    }

    /** Whether a folder, or anything above it, is one of the given folders. */
    private boolean isInsideAnyOf(Integer folderId, Set<Integer> folders) {
        if (folders.isEmpty()) return false;
        for (Integer id = folderId; id != null; ) {
            if (folders.contains(id)) return true;
            var folder = knowledgeBaseService.findFolder(id).orElse(null);
            if (folder == null) return false;
            id = folder.parentId();
        }
        return false;
    }

    private RemoteKbFolderSummary summary(KbFolder folder) {
        return new RemoteKbFolderSummary(
                folder.id(),
                folder.name(),
                folder.description() != null ? folder.description() : "",
                travellingUserTypes(folder.id(), null));
    }

    private RemoteKbFileSummary summary(KbFile file) {
        return new RemoteKbFileSummary(
                file.id(),
                file.name(),
                file.description() != null ? file.description() : "",
                file.fileType().name(),
                file.updatedAt().toString(),
                travellingUserTypes(null, file.id()));
    }

    /**
     * The user types an entry is restricted to, which travel with it to the station reading it.
     *
     * <p>The station serving an entry never learns who is reading it: it is handed a partnership, which is
     * a station, and the person behind the request does not cross the boundary. So the audience goes over
     * and the receiving station, where the reader is known, drops what does not match.
     *
     * <p>Only user types travel. A member group and a user tag are one station's own rows with no
     * counterpart at the next, so the receiving station could not apply them.
     *
     * @return the user types named, or empty when the entry names none and is therefore for everybody
     */
    private List<String> travellingUserTypes(Integer folderId, Integer fileId) {
        return accessService.findRestrictions(folderId, fileId).stream()
                .map(KbAccessGrant::userType)
                .filter(Objects::nonNull)
                .map(Enum::name)
                .distinct()
                .toList();
    }

    /**
     * Searches the files this station shares with a requesting partner. Matches on files that are
     * not shared with that partner are dropped.
     *
     * @param partner the verified requesting partner
     * @param query   the search terms
     * @return the matching shared files
     */
    public List<RemoteKbSearchResultItem> searchForPartner(FederationPartner partner, String query) {
        if (query == null || query.isBlank()) return List.of();
        var results = searchService.searchWithSnippets(partner.stationId(), query);
        var sharedFileIds = federationRepository.findKbShares(partner.stationId()).stream()
                .map(FederationShare::fileId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return results.stream()
                .filter(result -> sharedFileIds.contains(result.file().id()))
                .map(result -> new RemoteKbSearchResultItem(
                        result.file().id(),
                        result.file().name(),
                        result.file().description() != null ? result.file().description() : "",
                        result.snippet() != null ? result.snippet() : ""))
                .toList();
    }

    /**
     * Loads a file for a requesting partner, refusing everything the station has not shared.
     *
     * <p>Belonging to the station the partner is paired with is not the same as being shared with
     * it: file ids are sequential, so a check on ownership alone hands a partner the whole
     * knowledge base by counting. A file counts as shared when it is shared itself or sits in a
     * shared folder, which is what the same-instance browse treats as shared too.
     */
    public KbFile fileForPartner(FederationPartner partner, int fileId) {
        var file = knowledgeBaseService.findFile(fileId).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != partner.stationId() || !isSharedWithPartner(partner, file)) {
            throw new NotFoundResponse();
        }
        return file;
    }

    /**
     * Whether the station shares the given file with the requesting partner, directly or through
     * the folder it sits in.
     */
    public boolean isSharedWithPartner(FederationPartner partner, KbFile file) {
        for (var share : sharesReaching(partner.stationId(), partner.id())) {
            if (share.fileId() != null && share.fileId() == file.id()) return true;
        }
        return isFolderSharedWithPartner(partner, file.folderId());
    }

    /**
     * Whether a folder is shared with this partner, itself or through a folder above it.
     *
     * <p>Sharing a folder shares what is under it, to the bottom. The check used to match a direct parent
     * only, so an article one level deeper was refused, which is not what sharing a folder means to
     * anybody and disagrees with public visibility, which walks the whole ancestry.
     *
     * @param partner  the requesting partner
     * @param folderId the folder to ask about, or {@code null} for the root, which is never shared
     */
    public boolean isFolderSharedWithPartner(FederationPartner partner, Integer folderId) {
        return isFolderShared(partner.stationId(), folderId, partner.id());
    }

    private boolean isFolderShared(int servingStationId, Integer folderId, Integer readingPartnerId) {
        return isInsideAnyOf(folderId, sharedFolderIds(servingStationId, readingPartnerId));
    }

    /** The folders one station shares that reach one reader, by id. */
    private Set<Integer> sharedFolderIds(int servingStationId, Integer readingPartnerId) {
        return sharesReaching(servingStationId, readingPartnerId).stream()
                .map(FederationShare::folderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * Loads a file's text content for a requesting partner, refusing files that belong to another
     * station.
     */
    public String fileContentForPartner(FederationPartner partner, int fileId) {
        var file = fileForPartner(partner, fileId);
        return contentService.getMarkdownContent(file.id()).orElse("");
    }

    /**
     * Maps a knowledge-base comment to its API response, resolving the author's display name for
     * local and federated authors alike.
     */
    public CommentResponse toCommentResponse(KbComment comment) {
        return CommentResponseMapper.fromKb(memberNameResolver, comment);
    }

    /**
     * Lists the comments on a knowledge-base file held by this instance, with author names
     * resolved for local and federated authors alike.
     */
    public List<CommentResponse> listComments(int fileId) {
        return commentRepository.findByFile(fileId).stream()
                .map(this::toCommentResponse)
                .toList();
    }

    /**
     * Lists the comments on a partner's knowledge-base file.
     */
    public List<CommentResponse> listFederatedComments(int stationId, UUID partnerStationUid, int fileId) {
        var partner = resolvePartner(stationId, partnerStationUid);
        if (!partner.isRemote()) {
            return listComments(fileId);
        }
        var station = requireStation(stationId);
        return httpClient.getList(
                partner.remoteHost(),
                RemoteKnowledgeBaseRoutes.LIST_COMMENTS.at(fileId),
                partner.partnerStationId(),
                station.id(),
                station.federationPrivateKey(),
                CommentResponse.class);
    }

    /**
     * Creates a comment on a partner's knowledge-base file on behalf of a local member.
     */
    public CommentResponse createFederatedComment(
            int stationId,
            UUID partnerStationUid,
            int fileId,
            UUID memberUid,
            String displayName,
            Integer parentId,
            String content) {
        var partner = resolvePartner(stationId, partnerStationUid);
        if (!partner.isRemote()) {
            var author = new MemberIdentity(partner.partnerStationId(), memberUid);
            var comment = commentRepository.create(fileId, parentId, author, content);
            eventFederationRepository.cacheName(partner.id(), memberUid, displayName);
            return toCommentResponse(comment);
        }
        var station = requireStation(stationId);
        var result = httpClient.post(
                partner.remoteHost(),
                RemoteKnowledgeBaseRoutes.CREATE_COMMENT.at(fileId),
                new RemoteCommentRequest(memberUid, displayName, parentId, content),
                partner.partnerStationId(),
                station.id(),
                station.federationPrivateKey(),
                CommentResponse.class);
        if (result == null) throw new InternalServerErrorResponse("Failed to create comment on partner");
        return result;
    }

    /**
     * Updates a comment a local member wrote on a partner's knowledge-base file.
     */
    public CommentResponse updateFederatedComment(
            int stationId, UUID partnerStationUid, int commentId, UUID memberUid, String content) {
        var partner = resolvePartner(stationId, partnerStationUid);
        if (!partner.isRemote()) {
            requireOwnComment(commentId, memberUid, "edit");
            commentRepository.update(commentId, content);
            return toCommentResponse(requireComment(commentId));
        }
        var station = requireStation(stationId);
        var result = httpClient.put(
                partner.remoteHost(),
                RemoteKnowledgeBaseRoutes.UPDATE_COMMENT.at(commentId),
                new RemoteCommentUpdateRequest(memberUid, content),
                partner.partnerStationId(),
                station.id(),
                station.federationPrivateKey(),
                CommentResponse.class);
        if (result == null) throw new InternalServerErrorResponse("Failed to update comment on partner");
        return result;
    }

    /**
     * Deletes a comment a local member wrote on a partner's knowledge-base file.
     *
     * @return {@code true} when the comment was removed
     */
    public boolean deleteFederatedComment(int stationId, UUID partnerStationUid, int commentId, UUID memberUid) {
        var partner = resolvePartner(stationId, partnerStationUid);
        if (!partner.isRemote()) {
            requireOwnComment(commentId, memberUid, "delete");
            return commentRepository.delete(commentId);
        }
        var station = requireStation(stationId);
        boolean success = httpClient.delete(
                partner.remoteHost(),
                RemoteKnowledgeBaseRoutes.DELETE_COMMENT.at(commentId),
                new RemoteCommentDeleteRequest(memberUid),
                partner.partnerStationId(),
                station.id(),
                station.federationPrivateKey());
        if (!success) throw new InternalServerErrorResponse("Failed to delete comment on partner");
        return true;
    }

    /**
     * Stores a comment a federated member wrote on one of this station's files and caches their
     * display name for later renders.
     */
    public KbComment createRemoteComment(
            int fileId, int partnerId, UUID remoteMemberUid, String displayName, Integer parentId, String content) {
        var partnerStationUid = federationRepository
                .findPartnerById(partnerId)
                .map(FederationPartner::partnerStationId)
                .orElse(null);
        var author = partnerStationUid != null ? new MemberIdentity(partnerStationUid, remoteMemberUid) : null;
        var comment = commentRepository.create(fileId, parentId, author, content);
        eventFederationRepository.cacheName(partnerId, remoteMemberUid, displayName);
        log.info("KB remote comment {} created on file {} from partner {}", comment.id(), fileId, partnerId);
        return comment;
    }

    /**
     * Updates a comment a federated member wrote on one of this station's files, after verifying
     * they are its author.
     */
    public KbComment updateRemoteComment(
            FederationPartner partner, int commentId, UUID remoteMemberUid, String content) {
        requireRemoteCommentAuthor(partner, commentId, remoteMemberUid, "edit");
        commentRepository.update(commentId, content);
        return requireComment(commentId);
    }

    /**
     * Loads a comment and verifies it was written by the given federated member, answering
     * {@code 404} when it is absent and {@code 403} on an author mismatch.
     *
     * @param action the verb used in the rejection message, for example {@code delete}
     */
    public KbComment requireRemoteCommentAuthor(
            FederationPartner partner, int commentId, UUID remoteMemberUid, String action) {
        var comment = requireComment(commentId);
        var expectedAuthor = new MemberIdentity(partner.partnerStationId(), remoteMemberUid);
        if (comment.author() == null || !comment.author().sameMember(expectedAuthor)) {
            throw new ForbiddenResponse("You can only " + action + " your own comments");
        }
        return comment;
    }

    /**
     * What one partner on this instance offers at the top of its shared wiki.
     *
     * <p>A shared folder arrives as a folder, and what is in it stays in it: the reader opens it the way
     * they open any other. An article whose folder is already shared is therefore not offered loose as
     * well, or it would stand twice, once at the top and once where it belongs.
     */
    private SharedKbLevel browseSharedKbDirect(int remoteStationId, FederationPartner partner) {
        var served = servedLevel(remoteStationId, servingSideId(partner));
        var folders = served.folders().stream()
                .map(folder -> new SharedKbFolder(
                        folder.id(),
                        folder.name(),
                        folder.description(),
                        remoteStationId,
                        partner.id(),
                        folder.userTypes()))
                .toList();
        var files = new ArrayList<SharedKbItem>();
        for (var summary : served.files()) {
            knowledgeBaseService.findFile(summary.id()).ifPresent(file -> {
                files.add(new SharedKbItem(KbFileSummary.of(file), remoteStationId, partner.id(), summary.userTypes()));
                federationRepository.upsertMetadataCache(
                        partner.id(), ContentType.KB, file.id(), file.name(), file.description());
            });
        }
        return new SharedKbLevel(folders, files);
    }

    private SharedKbLevel browseSharedKbViaHttp(int localStationId, FederationPartner partner, int remoteStationId) {
        var result = new ArrayList<SharedKbItem>();
        var served = fetchSharedKb(
                partner.remoteHost(), partner.partnerStationId(), localStationId, privateKey(localStationId));
        for (var remoteFile : served.files()) {
            var summary = new KbFileSummary(
                    remoteFile.id(),
                    remoteStationId,
                    null,
                    remoteFile.name(),
                    remoteFile.description(),
                    KbFileType.valueOf(remoteFile.fileType() != null ? remoteFile.fileType() : "MARKDOWN"),
                    Instant.now(),
                    false);
            result.add(new SharedKbItem(summary, remoteStationId, partner.id(), remoteFile.userTypes()));
            federationRepository.upsertMetadataCache(
                    partner.id(), ContentType.KB, remoteFile.id(), remoteFile.name(), remoteFile.description());
        }
        var folders = served.folders().stream()
                .map(folder -> new SharedKbFolder(
                        folder.id(),
                        folder.name(),
                        folder.description(),
                        remoteStationId,
                        partner.id(),
                        folder.userTypes()))
                .toList();
        return new SharedKbLevel(folders, result);
    }

    private List<FederatedSearchResult> searchKbDirect(FederationPartner partner, String query) {
        String stationName = FederationDisplayNames.partnerName(stationRepository, partner, "?");
        String stationUid = partner.partnerStationId().toString();
        return searchService.searchWithSnippets(partnerStationId(partner), query).stream()
                .map(result -> new FederatedSearchResult(
                        KbFileSummary.of(result.file()), result.snippet(), stationName, stationUid))
                .toList();
    }

    private List<FederatedSearchResult> searchKbViaHttp(int localStationId, FederationPartner partner, String query) {
        String privateKey = privateKey(localStationId);
        if (privateKey == null) return List.of();
        int remoteStationId = partnerStationId(partner);
        String stationName = FederationDisplayNames.partnerName(stationRepository, partner, "?");
        String stationUid = partner.partnerStationId().toString();
        var results = searchKb(partner.remoteHost(), partner.partnerStationId(), localStationId, privateKey, query);
        return results.stream()
                .map(result -> new FederatedSearchResult(
                        new KbFileSummary(
                                result.id(),
                                remoteStationId,
                                null,
                                result.name(),
                                result.description(),
                                null,
                                Instant.now(),
                                false),
                        result.snippet(),
                        stationName,
                        stationUid))
                .toList();
    }

    private RemoteKbBrowse fetchSharedKb(
            String remoteHost, UUID partnerStationUid, int localStationId, String localPrivateKeyBase64) {
        return httpClient.get(
                remoteHost,
                RemoteKnowledgeBaseRoutes.BROWSE_KB.at(),
                partnerStationUid,
                localStationId,
                localPrivateKeyBase64,
                RemoteKbBrowse.class);
    }

    private List<RemoteKbSearchResultItem> searchKb(
            String remoteHost, UUID partnerStationUid, int localStationId, String localPrivateKeyBase64, String query) {
        return httpClient.getList(
                remoteHost,
                RemoteKnowledgeBaseRoutes.SEARCH_KB.at().query("q", query),
                partnerStationUid,
                localStationId,
                localPrivateKeyBase64,
                RemoteKbSearchResultItem.class);
    }

    private String fetchKbFileContent(
            String remoteHost, UUID partnerStationUid, int fileId, int localStationId, String localPrivateKeyBase64) {
        var remoteContent = httpClient.get(
                remoteHost,
                RemoteKnowledgeBaseRoutes.GET_FILE_CONTENT.at(fileId),
                partnerStationUid,
                localStationId,
                localPrivateKeyBase64,
                RemoteKnowledgeBaseRoutes.FileContentResponse.class);
        if (remoteContent == null || remoteContent.content() == null) return "";
        return remoteContent.content();
    }

    private List<FederationPartner> sharedKbPartners(int stationId) {
        return federationService.findPartners(stationId).stream()
                .filter(partner -> partner.status() == FederationPartner.FederationStatus.ACTIVE)
                .filter(partner -> federationService.hasCapability(partner, CapabilityType.KB_SHARE, Direction.IMPORT))
                .toList();
    }

    private KbFile requirePartnerFile(int fileId, FederationPartner partner) {
        var file = knowledgeBaseService.findFile(fileId).orElseThrow();
        if (file.stationId() != partnerStationId(partner)) {
            throw new BadRequestResponse("File does not belong to this partner");
        }
        return file;
    }

    /**
     * Resolves the partnership a station has with the given remote station. Answers {@code 404} for
     * an unknown pairing, which is what the comment endpoints report to their callers.
     */
    private FederationPartner resolvePartner(int stationId, UUID partnerStationUid) {
        return federationRepository
                .findPartnerByStationAndRemoteUid(stationId, partnerStationUid)
                .orElseThrow(() -> new NotFoundResponse("Unknown partner"));
    }

    private KbComment requireComment(int commentId) {
        return commentRepository.findById(commentId).orElseThrow(NotFoundResponse::new);
    }

    private void requireOwnComment(int commentId, UUID memberUid, String action) {
        var comment = requireComment(commentId);
        if (comment.author() == null || !comment.author().memberUid().equals(memberUid)) {
            throw new ForbiddenResponse("You can only " + action + " your own comments");
        }
    }

    private Station requireStation(int stationId) {
        return stationRepository.findById(stationId).orElseThrow();
    }

    private String privateKey(int stationId) {
        return stationRepository
                .findById(stationId)
                .map(Station::federationPrivateKey)
                .orElse(null);
    }

    /**
     * The id of the partnership row the serving station holds, seen from the reader's own row.
     *
     * <p>A share aimed at named stations names them by the partnerships the serving station keeps, and the
     * reader arrives holding the mirror image of one. Both sides exist for the same pairing, and they have
     * different ids, so reading the aim means crossing over to the serving station's row.
     *
     * @param partner the reader's own partnership row
     * @return the serving station's row id, or {@code null} when the pairing has no other side recorded
     */
    private Integer servingSideId(FederationPartner partner) {
        var readerStation = stationRepository.findById(partner.stationId()).orElse(null);
        if (readerStation == null) return null;
        return federationRepository
                .findPartnerByStationAndRemoteUid(partnerStationId(partner), readerStation.uid())
                .map(FederationPartner::id)
                .orElse(null);
    }

    private int partnerStationId(FederationPartner partner) {
        return stationRepository
                .findByUid(partner.partnerStationId())
                .map(Station::id)
                .orElse(0);
    }

    private FederationPartner findPartnerForStation(int localStationId, int remoteStationId) {
        for (var partner : federationService.findPartners(localStationId)) {
            if (partnerStationId(partner) == remoteStationId
                    && partner.status() == FederationPartner.FederationStatus.ACTIVE) {
                return partner;
            }
        }
        return null;
    }

    /**
     * A file shared by a partner, carrying the station that owns it and the partnership it came
     * through.
     */
    public record SharedKbItem(KbFileSummary file, int sourceStationId, int partnerId, List<String> userTypes) {}

    /**
     * A search match from a partner, carrying the name and UUID of the serving station.
     */
    public record FederatedSearchResult(KbFileSummary file, String snippet, String stationName, String stationUid) {}

    /**
     * A shared file as rendered for the federated browse response. The station UUID addresses the
     * serving station on the federated read routes and is null when the partnership behind the file
     * can no longer be resolved.
     */
    public record FederatedKbItem(
            int remoteId,
            String title,
            String description,
            String stationName,
            String stationUid,
            int partnerId,
            List<String> userTypes) {}

    /**
     * A folder a partner shares, as offered to the station reading it.
     */
    public record FederatedKbFolder(
            int remoteId,
            String title,
            String description,
            String stationName,
            String stationUid,
            int partnerId,
            List<String> userTypes) {}

    /**
     * One level of what the partners share: their folders and the articles standing beside them.
     */
    public record FederatedKbBrowse(
            List<FederatedKbFolder> folders, List<FederatedKbItem> files, List<FederatedKbFolder> trail) {}

    /**
     * A folder a partner shares, as gathered before the partner's name is resolved.
     */
    public record SharedKbFolder(
            int id, String name, String description, int sourceStationId, int partnerId, List<String> userTypes) {}

    /**
     * One level of a partner's shared wiki, as gathered.
     */
    public record SharedKbLevel(List<SharedKbFolder> folders, List<SharedKbItem> files) {}

    /**
     * A shared file as served to a requesting partner.
     */
    public record RemoteKbFileSummary(
            int id, String name, String description, String fileType, String updatedAt, List<String> userTypes) {}

    /**
     * A shared folder as served to a requesting partner.
     */
    public record RemoteKbFolderSummary(int id, String name, String description, List<String> userTypes) {}

    /**
     * One level of a partner's shared wiki: the folders it offers and the articles standing beside them.
     */
    public record RemoteKbBrowse(
            List<RemoteKbFolderSummary> folders, List<RemoteKbFileSummary> files, List<RemoteKbFolderSummary> trail) {}

    /**
     * A search match as served to a requesting partner.
     */
    public record RemoteKbSearchResultItem(int id, String name, String description, String snippet) {}

    /**
     * A shared file as received from a partner instance.
     */

    /**
     * A search match as received from a partner instance.
     */

    /**
     * File content as received from a partner instance.
     */
    private record RemoteCommentRequest(UUID remoteMemberUid, String displayName, Integer parentId, String content) {}

    private record RemoteCommentUpdateRequest(UUID remoteMemberUid, String content) {}

    private record RemoteCommentDeleteRequest(UUID remoteMemberUid) {}
}
