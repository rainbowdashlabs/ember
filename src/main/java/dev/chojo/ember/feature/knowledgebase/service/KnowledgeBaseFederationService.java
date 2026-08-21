/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.knowledgebase.service;

import dev.chojo.ember.api.MemberIdentity;
import dev.chojo.ember.feature.comment.route.CommentResponse;
import dev.chojo.ember.feature.comment.route.CommentResponseMapper;
import dev.chojo.ember.feature.events.repository.EventFederationRepository;
import dev.chojo.ember.feature.federation.entity.CapabilityType;
import dev.chojo.ember.feature.federation.entity.ContentType;
import dev.chojo.ember.feature.federation.entity.Direction;
import dev.chojo.ember.feature.federation.entity.FederationPartner;
import dev.chojo.ember.feature.federation.entity.FederationShare;
import dev.chojo.ember.feature.federation.repository.FederationRepository;
import dev.chojo.ember.feature.federation.service.FederationDisplayNames;
import dev.chojo.ember.feature.federation.service.FederationEntityResolver;
import dev.chojo.ember.feature.federation.service.FederationFanout;
import dev.chojo.ember.feature.federation.service.FederationHttpClient;
import dev.chojo.ember.feature.federation.service.FederationService;
import dev.chojo.ember.feature.knowledgebase.entity.KbComment;
import dev.chojo.ember.feature.knowledgebase.entity.KbFile;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileSummary;
import dev.chojo.ember.feature.knowledgebase.entity.KbFileType;
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
import java.util.HashSet;
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
            KbPdfExportService pdfExportService) {
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
    }

    /**
     * Browses every file shared with a station, enriched with the name of the station that serves
     * it. Local partners are read from the database, remote partners over signed HTTP, all in
     * parallel.
     *
     * @param stationId the browsing station ID
     * @return the shared files of all partners that answered
     */
    public List<FederatedKbItem> browseFederatedKb(int stationId) {
        return browseSharedKb(stationId).stream()
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
                            item.partnerId());
                })
                .toList();
    }

    /**
     * Collects the files every active partner shares with a station.
     *
     * @param stationId the browsing station ID
     * @return the shared files with their owning station and partnership
     */
    public List<SharedKbItem> browseSharedKb(int stationId) {
        return fanout.fanOut(
                sharedKbPartners(stationId),
                partner -> browseSharedKbDirect(partnerStationId(partner), partner),
                partner -> browseSharedKbViaHttp(stationId, partner, partnerStationId(partner)));
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
    public List<RemoteKbFileSummary> browseForPartner(FederationPartner partner) {
        return federationRepository.findKbShares(partner.stationId()).stream()
                .filter(share -> share.fileId() != null)
                .flatMap(share -> knowledgeBaseService.findFile(share.fileId()).stream())
                .filter(file -> file.stationId() == partner.stationId())
                .map(file -> new RemoteKbFileSummary(
                        file.id(),
                        file.name(),
                        file.description() != null ? file.description() : "",
                        file.fileType().name(),
                        file.updatedAt().toString()))
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
     * Loads a file for a requesting partner, refusing files that belong to another station.
     */
    public KbFile fileForPartner(FederationPartner partner, int fileId) {
        var file = knowledgeBaseService.findFile(fileId).orElseThrow(NotFoundResponse::new);
        if (file.stationId() != partner.stationId()) {
            throw new ForbiddenResponse("File not shared with this partner");
        }
        return file;
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

    private List<SharedKbItem> browseSharedKbDirect(int remoteStationId, FederationPartner partner) {
        var result = new ArrayList<SharedKbItem>();
        // An article can be reached by two shares at once: its own, and the one on the folder holding it.
        // Sharing a folder and then an article inside it is a reasonable thing to do, and the reader should
        // still see the article once.
        var seen = new HashSet<Integer>();
        for (var share : federationRepository.findKbShares(remoteStationId)) {
            if (share.fileId() != null) {
                knowledgeBaseService
                        .findFile(share.fileId())
                        .ifPresent(file -> take(file, remoteStationId, partner, seen, result));
            } else if (share.folderId() != null) {
                for (var file : knowledgeBaseService.findFiles(remoteStationId, share.folderId())) {
                    take(file, remoteStationId, partner, seen, result);
                }
            }
        }
        return result;
    }

    /** Adds one shared article to the answer, unless another share has already offered it. */
    private void take(
            KbFile file, int remoteStationId, FederationPartner partner, Set<Integer> seen, List<SharedKbItem> result) {
        if (!seen.add(file.id())) return;
        result.add(new SharedKbItem(KbFileSummary.of(file), remoteStationId, partner.id()));
        federationRepository.upsertMetadataCache(
                partner.id(), ContentType.KB, file.id(), file.name(), file.description());
    }

    private List<SharedKbItem> browseSharedKbViaHttp(
            int localStationId, FederationPartner partner, int remoteStationId) {
        var result = new ArrayList<SharedKbItem>();
        var files = fetchSharedKbFiles(
                partner.remoteHost(), partner.partnerStationId(), localStationId, privateKey(localStationId));
        for (var remoteFile : files) {
            var summary = new KbFileSummary(
                    remoteFile.id(),
                    remoteStationId,
                    null,
                    remoteFile.name(),
                    remoteFile.description(),
                    KbFileType.valueOf(remoteFile.fileType() != null ? remoteFile.fileType() : "MARKDOWN"),
                    Instant.now(),
                    false);
            result.add(new SharedKbItem(summary, remoteStationId, partner.id()));
            federationRepository.upsertMetadataCache(
                    partner.id(), ContentType.KB, remoteFile.id(), remoteFile.name(), remoteFile.description());
        }
        return result;
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

    private List<RemoteKbFileSummary> fetchSharedKbFiles(
            String remoteHost, UUID partnerStationUid, int localStationId, String localPrivateKeyBase64) {
        return httpClient.getList(
                remoteHost,
                RemoteKnowledgeBaseRoutes.BROWSE_KB.at(),
                partnerStationUid,
                localStationId,
                localPrivateKeyBase64,
                RemoteKbFileSummary.class);
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
    public record SharedKbItem(KbFileSummary file, int sourceStationId, int partnerId) {}

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
            int remoteId, String title, String description, String stationName, String stationUid, int partnerId) {}

    /**
     * A shared file as served to a requesting partner.
     */
    public record RemoteKbFileSummary(int id, String name, String description, String fileType, String updatedAt) {}

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
