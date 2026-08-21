/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.StationPermission;
import dev.chojo.ember.api.auth.StationUserType;
import dev.chojo.ember.feature.members.entity.StationMember;
import dev.chojo.ember.feature.members.repository.StationMemberRepository;
import dev.chojo.ember.feature.station.entity.Station;
import dev.chojo.ember.feature.station.repository.StationRepository;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

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

    @Inject
    public ClusterMemberManagementService(
            StationMemberRepository memberRepository, StationRepository stationRepository) {
        this.memberRepository = memberRepository;
        this.stationRepository = stationRepository;
    }

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
