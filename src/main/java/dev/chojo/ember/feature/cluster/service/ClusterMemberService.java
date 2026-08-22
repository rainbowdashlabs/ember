/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterMemberRoleChanged;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.cluster.entity.ClusterMemberGroup;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.NotFoundResponse;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The cluster's own members, their groups, and the three ways they come to hold a permission.
 *
 * <p>A cluster member is an account acting for the cluster, and nothing more: no station membership is
 * invented for them, they appear in no station's member list, and they never enter a member station's own
 * screens. What they may do at the cluster is decided here; what anybody may do at a station is decided
 * there.
 */
@Singleton
public class ClusterMemberService {
    private static final Logger log = LoggerFactory.getLogger(ClusterMemberService.class);

    private final ClusterRepository clusterRepository;
    private final ClusterService clusterService;
    private final DomainEventBus eventBus;

    @Inject
    public ClusterMemberService(
            ClusterRepository clusterRepository, ClusterService clusterService, DomainEventBus eventBus) {
        this.clusterRepository = clusterRepository;
        this.clusterService = clusterService;
        this.eventBus = eventBus;
    }

    // -- Members --

    public List<ClusterMember> findMembers(int clusterId) {
        return clusterRepository.findMembers(clusterId);
    }

    /**
     * Everything one member holds, split by where it came from.
     *
     * <p>Split rather than merged, because the screen showing it has to say which grants can be taken away
     * here and which follow from the user type or a group.
     *
     * @param clusterId the cluster
     * @param memberId  the member
     * @return their type, their own grants, their groups and the whole expanded set
     */
    public MemberDetail findMemberDetail(int clusterId, int memberId) {
        ClusterMember member = requireMember(clusterId, memberId);
        return new MemberDetail(
                member,
                clusterRepository.findDirectPermissions(memberId),
                clusterRepository.findGroupsOfMember(memberId),
                clusterService.resolvePermissions(member));
    }

    /**
     * Changes what a member is, which changes what they hold by default.
     *
     * @param clusterId the cluster
     * @param memberId  the member
     * @param userType  their new type
     */
    public void setUserType(int clusterId, int memberId, ClusterUserType userType) {
        Cluster cluster = requireCluster(clusterId);
        ClusterMember member = requireMember(clusterId, memberId);
        if (member.userType() == userType) return;

        clusterRepository.setMemberUserType(memberId, userType);
        log.info("Cluster member {} is now {}", memberId, userType);
        eventBus.publish(new ClusterMemberRoleChanged(memberId, cluster.name()));
    }

    /**
     * Replaces the grants made to a member by name.
     *
     * <p>What their user type carries and what their groups carry is untouched: those are not this member's
     * to hold or lose, and revoking one here would be undone the moment the type was read again.
     *
     * @param clusterId   the cluster
     * @param memberId    the member
     * @param permissions what they should hold in their own right
     */
    public void setPermissions(int clusterId, int memberId, Set<ClusterPermission> permissions) {
        Cluster cluster = requireCluster(clusterId);
        requireMember(clusterId, memberId);
        Set<ClusterPermission> before = clusterRepository.findDirectPermissions(memberId);
        if (before.equals(permissions)) return;

        for (ClusterPermission permission : before) {
            if (!permissions.contains(permission)) clusterService.revoke(memberId, permission);
        }
        for (ClusterPermission permission : permissions) {
            if (!before.contains(permission)) clusterService.grant(memberId, permission);
        }
        log.info("Cluster member {} now holds {} of their own", memberId, permissions);
        eventBus.publish(new ClusterMemberRoleChanged(memberId, cluster.name()));
    }

    // -- Groups --

    public List<ClusterMemberGroup> findGroups(int clusterId) {
        return clusterRepository.findGroups(clusterId);
    }

    public GroupDetail findGroupDetail(int clusterId, int groupId) {
        ClusterMemberGroup group = requireGroup(clusterId, groupId);
        return new GroupDetail(
                group, clusterRepository.findGroupPermissions(groupId), clusterRepository.findGroupMemberIds(groupId));
    }

    public ClusterMemberGroup createGroup(int clusterId, String name) {
        requireCluster(clusterId);
        if (name == null || name.isBlank()) throw new BadRequestResponse("A group needs a name");
        return clusterRepository.createGroup(clusterId, name.trim());
    }

    public void renameGroup(int clusterId, int groupId, String name) {
        requireGroup(clusterId, groupId);
        if (name == null || name.isBlank()) throw new BadRequestResponse("A group needs a name");
        clusterRepository.renameGroup(groupId, name.trim());
    }

    public void deleteGroup(int clusterId, int groupId) {
        requireGroup(clusterId, groupId);
        clusterRepository.deleteGroup(groupId);
    }

    /**
     * Replaces who is in a group, telling everybody whose standing actually moved.
     *
     * @param clusterId the cluster
     * @param groupId   the group
     * @param memberIds who should be in it
     */
    public void setGroupMembers(int clusterId, int groupId, Set<Integer> memberIds) {
        Cluster cluster = requireCluster(clusterId);
        requireGroup(clusterId, groupId);
        List<Integer> before = clusterRepository.findGroupMemberIds(groupId);

        for (int memberId : before) {
            if (!memberIds.contains(memberId)) {
                clusterRepository.removeFromGroup(groupId, memberId);
                eventBus.publish(new ClusterMemberRoleChanged(memberId, cluster.name()));
            }
        }
        for (int memberId : memberIds) {
            if (before.contains(memberId)) continue;
            requireMember(clusterId, memberId);
            clusterRepository.addToGroup(groupId, memberId);
            eventBus.publish(new ClusterMemberRoleChanged(memberId, cluster.name()));
        }
    }

    /**
     * Replaces which groups one person is in, which is the same membership read from the other end.
     *
     * <p>Editing a member is where somebody looks when the question is what one person may do, and editing a
     * group is where they look when the question is what a whole role may do. Both write the same rows.
     *
     * @param clusterId the cluster
     * @param memberId  the member
     * @param groupIds  the groups they should be in
     */
    public void setMemberGroups(int clusterId, int memberId, Set<Integer> groupIds) {
        Cluster cluster = requireCluster(clusterId);
        requireMember(clusterId, memberId);
        Set<Integer> before = clusterRepository.findGroupsOfMember(memberId).stream()
                .map(ClusterMemberGroup::id)
                .collect(Collectors.toSet());
        if (before.equals(groupIds)) return;

        for (int groupId : before) {
            if (!groupIds.contains(groupId)) clusterRepository.removeFromGroup(groupId, memberId);
        }
        for (int groupId : groupIds) {
            if (before.contains(groupId)) continue;
            requireGroup(clusterId, groupId);
            clusterRepository.addToGroup(groupId, memberId);
        }
        eventBus.publish(new ClusterMemberRoleChanged(memberId, cluster.name()));
    }

    /**
     * Replaces what a group carries. Everybody in it is told, because their standing has moved.
     *
     * @param clusterId   the cluster
     * @param groupId     the group
     * @param permissions what it now carries
     */
    public void setGroupPermissions(int clusterId, int groupId, Set<ClusterPermission> permissions) {
        Cluster cluster = requireCluster(clusterId);
        requireGroup(clusterId, groupId);
        Set<ClusterPermission> before = clusterRepository.findGroupPermissions(groupId);
        if (before.equals(permissions)) return;

        for (ClusterPermission permission : before) {
            if (permissions.contains(permission)) continue;
            clusterRepository
                    .findPermissionId(permission)
                    .ifPresent(id -> clusterRepository.revokeFromGroup(groupId, id));
        }
        for (ClusterPermission permission : permissions) {
            if (before.contains(permission)) continue;
            int id = clusterRepository
                    .findPermissionId(permission)
                    .orElseThrow(() -> new BadRequestResponse("No such permission: " + permission));
            clusterRepository.grantToGroup(groupId, id);
        }
        for (int memberId : clusterRepository.findGroupMemberIds(groupId)) {
            eventBus.publish(new ClusterMemberRoleChanged(memberId, cluster.name()));
        }
    }

    private Cluster requireCluster(int clusterId) {
        return clusterRepository.findById(clusterId).orElseThrow(() -> new NotFoundResponse("No such cluster"));
    }

    /**
     * The member, checked against the cluster acting, so one cluster cannot reach into another's people.
     */
    private ClusterMember requireMember(int clusterId, int memberId) {
        ClusterMember member =
                clusterRepository.findMemberById(memberId).orElseThrow(() -> new NotFoundResponse("No such member"));
        if (member.clusterId() != clusterId) throw new NotFoundResponse("No such member");
        return member;
    }

    private ClusterMemberGroup requireGroup(int clusterId, int groupId) {
        ClusterMemberGroup group =
                clusterRepository.findGroupById(groupId).orElseThrow(() -> new NotFoundResponse("No such group"));
        if (group.clusterId() != clusterId) throw new NotFoundResponse("No such group");
        return group;
    }

    /**
     * @param direct   what the member holds in their own right, which is the only part editable per member
     * @param groups   the groups they are in
     * @param resolved everything they hold once type, grants and groups are put together and expanded
     */
    public record MemberDetail(
            ClusterMember member,
            Set<ClusterPermission> direct,
            List<ClusterMemberGroup> groups,
            Set<ClusterPermission> resolved) {}

    public record GroupDetail(ClusterMemberGroup group, Set<ClusterPermission> permissions, List<Integer> memberIds) {}
}
