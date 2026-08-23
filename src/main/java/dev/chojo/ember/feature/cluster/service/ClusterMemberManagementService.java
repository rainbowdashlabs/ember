/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.FieldValueEntry;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.members.service.ProfileFieldService;
import dev.chojo.ember.feature.members.service.StationMemberInviteService;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
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

    /** What stands in for an address when somebody is not meant to sign in at all. */
    private static final String SYNTHETIC_EMAIL_SUFFIX = ".local";

    private final StationMemberRepository memberRepository;
    private final StationRepository stationRepository;
    private final ProfileFieldService profileFieldService;
    private final StationMemberInviteService inviteService;

    @Inject
    public ClusterMemberManagementService(
            StationMemberRepository memberRepository,
            StationRepository stationRepository,
            ProfileFieldService profileFieldService,
            StationMemberInviteService inviteService) {
        this.memberRepository = memberRepository;
        this.stationRepository = stationRepository;
        this.profileFieldService = profileFieldService;
        this.inviteService = inviteService;
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
     * @throws NotFoundResponse when the station does not answer to this cluster
     */
    public StationMemberInviteService.ProvisionedMember createMember(
            int clusterId, UUID stationUid, String firstName, String lastName, String email, StationUserType userType) {
        Station station = stationRepository
                .findByUid(stationUid)
                .filter(candidate -> candidate.clusterId() != null && candidate.clusterId() == clusterId)
                .orElseThrow(() -> new NotFoundResponse("No such station"));

        String address = email != null && !email.isBlank()
                ? email.trim()
                : "%s.%s@%s%s".formatted(slug(firstName), slug(lastName), station.uid(), SYNTHETIC_EMAIL_SUFFIX);

        var provisioned =
                inviteService.provision(station.id(), address, firstName.trim(), lastName.trim(), userType, null);
        log.info("Cluster {} took on member {} at station {}", clusterId, provisioned.memberId(), station.id());
        return provisioned;
    }

    /** A name as it can stand in an address: letters and digits, and a dash for everything else. */
    private static String slug(String name) {
        String cleaned = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return cleaned.isBlank() ? "person" : cleaned;
    }

    /**
     * Everything asked of one person, and what they have answered.
     *
     * <p>The questions are the ones their station asks merged with the ones the cluster asks, each saying
     * which of the two it came from, so the screen can lay them out together and still know whose they are.
     *
     * @param clusterId the cluster acting
     * @param memberId  the member
     * @return the member, the questions and the answers
     */
    public MemberProfile getMemberProfile(int clusterId, int memberId) {
        StationMember member = requireMemberOfCluster(clusterId, memberId);
        return new MemberProfile(
                member, profileFieldService.findApplicableFields(memberId), profileFieldService.findValues(memberId));
    }

    /**
     * Records what a cluster manager answered for somebody.
     *
     * <p>The same two guardrails as every other write here, and one more that is not this class's doing: a
     * cluster field marked readable but not writable at the station is refused further down, whoever sends it.
     * A cluster manager is not the station, so that refusal does not apply to them and their answer stands.
     *
     * @param clusterId      the cluster acting
     * @param memberId       the member
     * @param entries        the answers, each naming which table its question lives in
     * @param actorAccountId the account behind the cluster manager, for the self-check
     * @param changedBy      the member row to record as the author of the change
     */
    public void updateMemberProfile(
            int clusterId, int memberId, List<FieldValueEntry> entries, int actorAccountId, int changedBy) {
        StationMember member = requireMemberOfCluster(clusterId, memberId);
        requireNotSelf(member, actorAccountId);
        requireNotStationOwner(member);

        profileFieldService.setValues(memberId, entries, changedBy, true);
        log.info("Cluster {} answered {} questions for member {}", clusterId, entries.size(), memberId);
    }

    /**
     * @param member the person
     * @param fields what is asked of them, from their station and from the cluster together
     * @param values what they have answered
     */
    public record MemberProfile(
            StationMember member,
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
            String query,
            Integer stationId,
            StationUserType userType,
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
     * Changes what somebody is at their station.
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

        memberRepository.setUserType(memberId, userType);
        log.info("Cluster {} set member {} to {}", clusterId, memberId, userType);
    }

    /**
     * Replaces what somebody may do at their station.
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

        // Replace outright rather than diffing: what a cluster manager sends is the whole answer
        memberRepository.revokeAllPermissions(memberId);
        for (StationPermission permission : permissions) {
            memberRepository
                    .findPermissionByName(permission)
                    .ifPresent(row -> memberRepository.grantPermission(memberId, row.id()));
        }
        log.info("Cluster {} set the permissions of member {}", clusterId, memberId);
    }

    /**
     * Marks somebody as having left their station.
     *
     * @param clusterId      the cluster acting
     * @param memberId       the member
     * @param actorAccountId the account behind the cluster manager, for the self-check
     */
    public void archive(int clusterId, int memberId, int actorAccountId) {
        StationMember member = requireMemberOfCluster(clusterId, memberId);
        requireNotSelf(member, actorAccountId);
        requireNotStationOwner(member);

        memberRepository.setFormer(memberId, true);
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
        StationMember member =
                memberRepository.findById(memberId).orElseThrow(() -> new NotFoundResponse("No such member"));
        Station station = stationRepository
                .findById(member.stationId())
                .orElseThrow(() -> new NotFoundResponse("No such member"));
        if (station.clusterId() == null || station.clusterId() != clusterId) {
            throw new NotFoundResponse("No such member");
        }
        return member;
    }

    /**
     * Refuses somebody editing their own membership.
     *
     * <p>The check is on the account rather than the member row, because the same person at a different
     * station of the same cluster is still the same person, and that is exactly the hole this closes.
     */
    private static void requireNotSelf(StationMember member, int actorAccountId) {
        if (member.accountId() != null && member.accountId() == actorAccountId) {
            throw new ForbiddenResponse("You cannot edit your own membership from the cluster");
        }
    }

    private void requireNotStationOwner(StationMember member) {
        stationRepository.findById(member.stationId()).ifPresent(station -> {
            if (station.ownerMemberId() != null && station.ownerMemberId() == member.id()) {
                throw new ForbiddenResponse("A station's owner cannot be edited from the cluster");
            }
        });
    }

    /**
     * @param total how many the search found altogether, not how many are on this page
     */
    public record MemberPage(List<StationMemberRepository.ClusterMemberRow> members, int total, int page, int size) {}
}
