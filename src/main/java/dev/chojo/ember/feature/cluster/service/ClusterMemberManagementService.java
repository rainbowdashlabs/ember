/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.api.refusal.RefusalResponse;
import dev.chojo.ember.feature.account.service.SetupMail;
import dev.chojo.ember.feature.documents.entity.Document;
import dev.chojo.ember.feature.documents.entity.Uploader;
import dev.chojo.ember.feature.documents.repository.DocumentRepository;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService;
import dev.chojo.ember.feature.documents.service.DocumentCatalogService.MemberDocumentResponse;
import dev.chojo.ember.feature.documents.service.DocumentDoor;
import dev.chojo.ember.feature.documents.service.DocumentService;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.ProfileAuthor;
import dev.chojo.ember.feature.members.entity.ProfileWriter;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.FormerMemberService;
import dev.chojo.ember.feature.members.service.MemberNameResolver;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.members.service.UserTypeChangeService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.UploadedFile;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Members across every station of a cluster, for somebody who looks after all of them at once.
 *
 * <p>An additional actor rather than a replacement: the stations keep their own managers and their own
 * authority, and nothing here takes that away. What it adds is the ability to see and edit the people at
 * thirty stations without signing into thirty stations.
 *
 * <p>Two things a cluster member manager may never do, and both are about the same danger:
 *
 * <ol>
 *   <li>touch their own membership at any station of the cluster, so the role cannot be used to promote
 *       oneself,
 *   <li>touch a station's owner, so the one person who can speak for a station against the cluster cannot be
 *       quietly demoted by it.
 * </ol>
 *
 * <p>Beyond those two there is no ceiling. A cluster member manager may hand out anything a station manager
 * could, up to and including station administrator. The trust sits with whoever gave them the role.
 */
@Singleton
public class ClusterMemberManagementService {
    private static final Logger log = LoggerFactory.getLogger(ClusterMemberManagementService.class);

    private final StationMemberRepository memberRepository;
    private final StationRepository stationRepository;
    private final ProfileFieldService profileFieldService;
    private final StationMemberInviteService inviteService;
    private final UserTypeChangeService userTypeChanges;
    private final DocumentRepository documentRepository;
    private final DocumentService documentService;
    private final DocumentCatalogService documentCatalog;
    private final FormerMemberService formerMembers;
    private final MemberNameResolver names;

    @Inject
    public ClusterMemberManagementService(
            StationMemberRepository memberRepository,
            StationRepository stationRepository,
            ProfileFieldService profileFieldService,
            StationMemberInviteService inviteService,
            UserTypeChangeService userTypeChanges,
            DocumentRepository documentRepository,
            DocumentService documentService,
            DocumentCatalogService documentCatalog,
            FormerMemberService formerMembers,
            MemberNameResolver names) {
        this.formerMembers = formerMembers;
        this.names = names;
        this.memberRepository = memberRepository;
        this.stationRepository = stationRepository;
        this.profileFieldService = profileFieldService;
        this.inviteService = inviteService;
        this.userTypeChanges = userTypeChanges;
        this.documentRepository = documentRepository;
        this.documentService = documentService;
        this.documentCatalog = documentCatalog;
    }

    /**
     * Takes somebody on at one of the cluster's stations.
     *
     * <p>The station is named first and is part of the request rather than of the session, because a member
     * belongs to a station and the cluster is standing in for one. Everything after that is what the
     * station's own screen does, through the same service: an account is provisioned or an existing one is
     * attached, and the membership is made at the named station.
     *
     * <p>Somebody who is not meant to sign in gets an address nobody can receive mail at, which is how the
     * station's own screen records a member without a login and what the rest of the system reads as one.
     *
     * @param clusterId  the cluster acting
     * @param stationUid the station they join, which has to be one of the cluster's
     * @param firstName  their first name
     * @param lastName   their last name
     * @param email      their address, or {@code null} when they are not meant to sign in
     * @param userType   what they are at that station
     * @return the new membership
     * @throws RefusalResponse when the station does not answer to this cluster
     */
    public StationMemberInviteService.ProvisionedMember createMember(
            int clusterId, UUID stationUid, String firstName, String lastName, String email, StationUserType userType) {
        Station station = stationRepository
                .findByUid(stationUid)
                .filter(candidate -> candidate.clusterId() != null && candidate.clusterId() == clusterId)
                .orElseThrow(ClusterRefusal.CLUSTER_MANAGED_MEMBER_STATION_NOT_HERE::raise);

        String address = email != null && !email.isBlank() ? email.trim() : null;

        var provisioned = inviteService.provision(
                station.id(), address, firstName.trim(), lastName.trim(), userType, null, SetupMail.SEND_NOW);
        log.info("Cluster {} took on member {} at station {}", clusterId, provisioned.memberId(), station.id());
        return provisioned;
    }

    /**
     * The documents kept about one of the cluster's people.
     *
     * <p>Everything about them, hidden ones included: somebody trusted with the people at every station is
     * trusted with what is filed about them, which is the same test the station applies to its own managers.
     * A station that switched documents off has switched them off for the association too.
     *
     * @param clusterId the cluster acting
     * @param memberId  the member
     * @return what is filed about them
     */
    public List<MemberDocumentResponse> documentsOf(int clusterId, int memberId) {
        var member = requireMemberOfCluster(clusterId, memberId);
        return documentCatalog.forMember(member.stationId(), memberId, true, DocumentDoor.ASSOCIATION);
    }

    /**
     * Files a document about one of the cluster's people.
     *
     * <p>It belongs to the station that holds them rather than to the cluster, because that is where the
     * person is and where it has to stay when the station leaves. It passes the station's intake like any
     * other upload. The manager has no membership at that station, so their account is named as the
     * uploader rather than a membership of theirs elsewhere, which would name a stranger on this station's
     * paperwork.
     *
     * @param clusterId      the cluster acting
     * @param memberId       the member it is about
     * @param title          what it is called, or {@code null} to call it after its file
     * @param file           the uploaded file, or {@code null} where the request carried none
     * @param actorAccountId the account of the manager filing it
     * @return the document as filed
     */
    public Document fileDocument(
            int clusterId, int memberId, @Nullable String title, @Nullable UploadedFile file, int actorAccountId) {
        StationMember member = requireMemberOfCluster(clusterId, memberId);
        var filing = new DocumentService.Filing(
                List.of(memberId), title, false, false, Uploader.account(actorAccountId), List.of());
        return documentService.file(member.stationId(), filing, file, DocumentDoor.ASSOCIATION);
    }

    /**
     * A document as the association's screen shows it, the same way the station's does.
     */
    public MemberDocumentResponse view(Document document) {
        return documentCatalog.view(document);
    }

    /**
     * One document, checked to be about somebody at a station of this cluster.
     *
     * @param clusterId  the cluster acting
     * @param documentId the document
     * @return it, when the cluster has any business with it
     */
    public Document requireDocumentOfCluster(int clusterId, int documentId) {
        Document document = documentRepository
                .findById(documentId)
                .orElseThrow(ClusterRefusal.CLUSTER_MANAGED_DOCUMENT_NOT_HERE::raise);
        boolean reachable = documentRepository.membersOf(documentId).stream()
                .anyMatch(memberId -> memberOfCluster(clusterId, memberId).isPresent());
        if (!reachable) throw ClusterRefusal.CLUSTER_MANAGED_DOCUMENT_NOT_HERE.raise();
        return document;
    }

    /**
     * The bytes of a document the cluster may read.
     */
    public byte[] readDocument(Document document) {
        return documentService
                .open(document, DocumentDoor.ASSOCIATION)
                .orElseThrow(ClusterRefusal.CLUSTER_MANAGED_DOCUMENT_FILE_NOT_HERE::raise);
    }

    /**
     * Everything asked of one person, and what they have answered.
     *
     * <p>The questions are the ones their station asks merged with the ones the cluster asks, each saying
     * which of the two it came from, so the screen can lay them out together and still know whose they are.
     *
     * @param clusterId the cluster acting
     * @param memberId  the member
     * @return the member by name, the questions and the answers
     */
    public MemberProfile getMemberProfile(int clusterId, int memberId) {
        StationMember member = requireMemberOfCluster(clusterId, memberId);
        return new MemberProfile(
                member,
                names.identified(memberId),
                profileFieldService.findApplicableFields(memberId),
                profileFieldService.findValues(memberId));
    }

    /**
     * Records what a cluster manager answered for somebody.
     *
     * <p>The same two guardrails as every other write here. The answers then pass the locks every profile
     * write passes, as the association: only questions of the member's station or asked there by this
     * association, only those put to the member, and the association's own lock against the station does
     * not hold against the association. A cluster manager looks after the members, so a question only the
     * member management writes is theirs to write too.
     *
     * @param clusterId      the cluster acting
     * @param memberId       the member
     * @param entries        the answers, each naming which table its question lives in
     * @param actorAccountId the account behind the cluster manager, for the self-check and as the author of the
     *                       change, recorded by their membership at the member's station where they have one
     */
    public void updateMemberProfile(int clusterId, int memberId, List<FieldValueEntry> entries, int actorAccountId) {
        StationMember member = requireMemberOfCluster(clusterId, memberId);
        requireNotSelf(member, actorAccountId);
        requireNotStationOwner(member);

        profileFieldService.setValues(
                memberId, entries, ProfileAuthor.account(actorAccountId), ProfileWriter.association());
        log.info("Cluster {} answered {} questions for member {}", clusterId, entries.size(), memberId);
    }

    /**
     * @param member the person
     * @param name   who they are, from their account while they are a member and as kept once they left
     * @param fields what is asked of them, from their station and from the cluster together
     * @param values what they have answered
     */
    public record MemberProfile(
            StationMember member,
            String name,
            List<ProfileFieldService.MergedField> fields,
            List<ProfileFieldService.MergedValue> values) {}

    /**
     * Searches the people at every station of the cluster.
     *
     * @param clusterId     the cluster
     * @param query         a name or email fragment, or {@code null} for everybody
     * @param stationId     narrow to one station, or {@code null}
     * @param userType      narrow to one user type, or {@code null}
     * @param includeFormer whether people who have left are listed too
     * @param page          the page, from zero
     * @param size          the page size
     * @return the page, and how many there are in total
     */
    public MemberPage search(
            int clusterId,
            @Nullable String query,
            @Nullable Integer stationId,
            @Nullable StationUserType userType,
            boolean includeFormer,
            int page,
            int size) {
        int limit = Math.clamp(size, 1, 200);
        int offset = Math.max(0, page) * limit;
        return new MemberPage(
                memberRepository.findClusterMembers(
                        clusterId, query, stationId, userType, includeFormer, limit, offset),
                memberRepository.countClusterMembers(clusterId, query, stationId, userType, includeFormer),
                Math.max(0, page),
                limit);
    }

    /**
     * Changes what somebody is at their station. They leave the station's groups that do not take the
     * new type, the same as when the station changes it.
     *
     * @param clusterId     the cluster acting
     * @param memberId      the member
     * @param userType      their new type
     * @param actorAccountId the account behind the cluster manager, for the self-check
     */
    public void setUserType(int clusterId, int memberId, StationUserType userType, int actorAccountId) {
        StationMember member = requireMemberOfCluster(clusterId, memberId);
        requireNotSelf(member, actorAccountId);
        requireNotStationOwner(member);

        userTypeChanges.change(memberId, userType);
        log.info("Cluster {} set member {} to {}", clusterId, memberId, userType);
    }

    /**
     * Replaces what somebody may do at their station, outright rather than by diffing: what a cluster
     * manager sends is the whole answer.
     *
     * @param clusterId      the cluster acting
     * @param memberId       the member
     * @param permissions    what they should hold
     * @param actorAccountId the account behind the cluster manager, for the self-check
     */
    public void setPermissions(int clusterId, int memberId, Set<StationPermission> permissions, int actorAccountId) {
        StationMember member = requireMemberOfCluster(clusterId, memberId);
        requireNotSelf(member, actorAccountId);
        requireNotStationOwner(member);

        memberRepository.revokeAllPermissions(memberId);
        for (StationPermission permission : permissions) {
            memberRepository
                    .findPermissionByName(permission)
                    .ifPresent(row -> memberRepository.grantPermission(memberId, row.id()));
        }
        log.info("Cluster {} set the permissions of member {}", clusterId, memberId);
    }

    /**
     * Marks somebody as having left their station, the way the station itself does.
     *
     * <p>Archiving means the same whoever presses the button, so the station's whole leaving routine runs:
     * their roles and with them the login, their guardians and wards, groups, tags, documents and the
     * answers not kept for the record. Somebody the station could not archive, because they still hold
     * equipment or a role that has to be handed on first, is not archived from here either.
     *
     * @param clusterId      the cluster acting
     * @param memberId       the member
     * @param actorAccountId the account behind the cluster manager, for the self-check
     * @throws RefusalResponse {@link ClusterRefusal#CLUSTER_MANAGED_MEMBER_NOT_ARCHIVED} where the station could
     *                         not archive them either
     */
    public void archive(int clusterId, int memberId, int actorAccountId) {
        StationMember member = requireMemberOfCluster(clusterId, memberId);
        requireNotSelf(member, actorAccountId);
        requireNotStationOwner(member);
        if (formerMembers.canMarkFormer(memberId) != null) {
            throw ClusterRefusal.CLUSTER_MANAGED_MEMBER_NOT_ARCHIVED.raise();
        }

        formerMembers.markFormer(memberId);
        log.info("Cluster {} archived member {}", clusterId, memberId);
    }

    /**
     * The stations a cluster manager may act in, which is every station of the cluster.
     *
     * @param clusterId the cluster
     * @return its member stations
     */
    public List<Station> reachableStations(int clusterId) {
        return stationRepository.findByCluster(clusterId);
    }

    /**
     * The member, checked to actually belong to a station of this cluster.
     */
    private StationMember requireMemberOfCluster(int clusterId, int memberId) {
        return memberOfCluster(clusterId, memberId).orElseThrow(ClusterRefusal.CLUSTER_MANAGED_MEMBER_NOT_HERE::raise);
    }

    /**
     * The member, where they belong to a station of this cluster.
     *
     * @param clusterId the cluster
     * @param memberId  the member
     * @return the member, empty when they are gone or belong to a station outside the cluster
     */
    private Optional<StationMember> memberOfCluster(int clusterId, int memberId) {
        return memberRepository.findById(memberId).filter(member -> stationRepository
                .findById(member.stationId())
                .filter(station -> station.clusterId() != null && station.clusterId() == clusterId)
                .isPresent());
    }

    /**
     * Refuses somebody editing their own membership.
     *
     * <p>The check is on the account rather than the member row, because the same person at a different
     * station of the same cluster is still the same person, and that is exactly the hole this closes.
     */
    private static void requireNotSelf(StationMember member, int actorAccountId) {
        Integer accountId = member.accountId();
        if (accountId != null && accountId == actorAccountId) {
            throw ClusterRefusal.CLUSTER_MANAGED_MEMBER_IS_YOURSELF.raise();
        }
    }

    private void requireNotStationOwner(StationMember member) {
        stationRepository.findById(member.stationId()).ifPresent(station -> {
            if (station.isOwnedBy(member.id())) {
                throw ClusterRefusal.CLUSTER_MANAGED_MEMBER_OWNS_STATION.raise();
            }
        });
    }

    /**
     * @param total how many the search found altogether, not how many are on this page
     */
    public record MemberPage(List<StationMemberRepository.ClusterMemberRow> members, int total, int page, int size) {}
}
