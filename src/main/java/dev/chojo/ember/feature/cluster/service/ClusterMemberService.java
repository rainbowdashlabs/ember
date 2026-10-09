/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.cluster.service;

import dev.chojo.ember.api.auth.ClusterPermission;
import dev.chojo.ember.api.auth.ClusterUserType;
import dev.chojo.ember.api.refusal.ClusterRefusal;
import dev.chojo.ember.event.DomainEventBus;
import dev.chojo.ember.event.events.ClusterMemberRoleChanged;
import dev.chojo.ember.feature.account.entity.Account;
import dev.chojo.ember.feature.account.repository.AccountRepository;
import dev.chojo.ember.feature.account.service.AccountInviteService;
import dev.chojo.ember.feature.account.service.AccountNameRequiredException;
import dev.chojo.ember.feature.account.service.SetupMail;
import dev.chojo.ember.feature.accountlink.entity.AssociationLinkState;
import dev.chojo.ember.feature.accountlink.service.AssociationLinkService;
import dev.chojo.ember.feature.cluster.entity.Cluster;
import dev.chojo.ember.feature.cluster.entity.ClusterMember;
import dev.chojo.ember.feature.cluster.entity.ClusterMemberGroup;
import dev.chojo.ember.feature.cluster.repository.ClusterRepository;
import dev.chojo.ember.feature.members.entity.NameParts;
import dev.chojo.ember.feature.members.util.GroupNames;
import dev.chojo.ember.util.SetDiff;
import dev.chojo.ember.util.sql.Transactions;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    private final AccountRepository accountRepository;
    private final AccountInviteService accountInviteService;
    private final AssociationLinkService linkService;
    private final DomainEventBus eventBus;

    @Inject
    public ClusterMemberService(
            ClusterRepository clusterRepository,
            ClusterService clusterService,
            AccountRepository accountRepository,
            AccountInviteService accountInviteService,
            AssociationLinkService linkService,
            DomainEventBus eventBus) {
        this.clusterRepository = clusterRepository;
        this.clusterService = clusterService;
        this.accountRepository = accountRepository;
        this.accountInviteService = accountInviteService;
        this.linkService = linkService;
        this.eventBus = eventBus;
    }

    /**
     * Takes somebody on as a member of the association, making the account when Ember has never seen the
     * address.
     *
     * <p>An address that already has an account is not taken on: the account belongs to a person who has
     * not agreed to act for the association, so they are asked instead, and the membership with its role
     * is made once they accept. Until then the association reaches nothing of the account. An unknown
     * address is refused until a name comes with it, and the refusal is its own kind so the screen can ask
     * for one rather than only reporting a failure. The account made for it is the person's from the
     * start, and the setup link mailed to the address is their agreement.
     *
     * @param clusterId the association
     * @param email     the address
     * @param userType  what they are at the association
     * @param firstName their first name, read only when the address has no account
     * @param lastName  their last name, read only when the address has no account
     * @return the membership made, or the request that waits for the person
     */
    public Addition addByEmail(
            int clusterId, String email, ClusterUserType userType, String firstName, String lastName) {
        Cluster cluster = clusterRepository
                .findById(clusterId)
                .orElseThrow(ClusterRefusal.CLUSTER_MEMBER_ADDED_TO_NO_CLUSTER::raise);
        if (email == null || email.isBlank()) throw ClusterRefusal.CLUSTER_MEMBER_ADDRESS_MISSING.raise();
        String address = email.trim();
        ClusterUserType role = userType != null ? userType : ClusterUserType.CLUSTER_USER;

        var existing = accountRepository.findByEmail(address);
        if (existing.isPresent()) {
            return new Asked(linkService.ask(clusterId, existing.get().id(), role, address));
        }

        if (isBlank(firstName) || isBlank(lastName)) {
            throw new AccountNameRequiredException(
                    "No account has that address yet, so a first and last name are needed");
        }
        var invited = accountInviteService.resolveOrCreate(
                cluster.homeStationId(), address, firstName.trim(), lastName.trim(), SetupMail.SEND_NOW);
        log.info("Cluster {} made an account for {}", clusterId, address);
        return new Added(clusterService.addMember(clusterId, invited.account().id(), role));
    }

    /** What adding an address to the association came to. */
    public sealed interface Addition permits Added, Asked {}

    /**
     * The address had no account, so one was made and taken on.
     *
     * @param member the membership
     */
    public record Added(ClusterMember member) implements Addition {}

    /**
     * The address has an account, whose owner is asked first.
     *
     * @param request where the request stands
     */
    public record Asked(AssociationLinkState request) implements Addition {}

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

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
        var change = SetDiff.of(clusterRepository.findDirectPermissions(memberId), permissions);
        if (change.isEmpty()) return;

        Transactions.run(() -> {
            change.removed().forEach(permission -> clusterService.revoke(memberId, permission));
            change.added().forEach(permission -> clusterService.grant(memberId, permission));
        });
        log.info("Cluster member {} now holds {} of their own", memberId, permissions);
        announce(cluster, Set.of(memberId));
    }

    public List<ClusterMemberGroup> findGroups(int clusterId) {
        return clusterRepository.findGroups(clusterId);
    }

    public GroupDetail findGroupDetail(int clusterId, int groupId) {
        ClusterMemberGroup group = requireGroup(clusterId, groupId);
        return new GroupDetail(
                group, clusterRepository.findGroupPermissions(groupId), clusterRepository.findGroupMemberIds(groupId));
    }

    public ClusterMemberGroup createGroup(int clusterId, @Nullable String name) {
        requireCluster(clusterId);
        String checked = GroupNames.require(
                name,
                null,
                namesOfOtherGroups(clusterId, null),
                ClusterRefusal.CLUSTER_MEMBER_GROUP_NAME_MISSING_ON_CREATE,
                ClusterRefusal.CLUSTER_MEMBER_GROUP_NAME_TAKEN_ON_CREATE);
        ClusterMemberGroup group = clusterRepository.createGroup(clusterId, checked);
        log.info("Cluster {} opened the group '{}' ({})", clusterId, group.name(), group.id());
        return group;
    }

    public void renameGroup(int clusterId, int groupId, @Nullable String name) {
        updateGroup(clusterId, groupId, new GroupChange(name, null, null));
    }

    /**
     * Closes a group. Everybody who was in it is told, because what it carried is no longer theirs.
     *
     * @param clusterId the cluster
     * @param groupId   the group
     */
    public void deleteGroup(int clusterId, int groupId) {
        requireGroup(clusterId, groupId);
        Cluster cluster = requireCluster(clusterId);
        List<Integer> members = clusterRepository.findGroupMemberIds(groupId);
        clusterRepository.deleteGroup(groupId);
        log.info("Cluster {} closed group {}", clusterId, groupId);
        announce(cluster, members);
    }

    /**
     * Replaces who is in a group, telling everybody whose standing actually moved.
     *
     * @param clusterId the cluster
     * @param groupId   the group
     * @param memberIds who should be in it
     */
    public void setGroupMembers(int clusterId, int groupId, Set<Integer> memberIds) {
        updateGroup(clusterId, groupId, new GroupChange(null, null, memberIds));
    }

    /**
     * Changes a group's name, what it carries and who is in it, all of it or none of it.
     *
     * <p>One request may name all three, and a refusal for the last part must not leave the first two
     * written: a group renamed and granted more while its people stayed the same is a state nobody asked
     * for. Everybody whose standing moved is told once the change holds, and not before.
     *
     * @param clusterId the cluster
     * @param groupId   the group
     * @param change    the parts to change
     */
    public void updateGroup(int clusterId, int groupId, GroupChange change) {
        Cluster cluster = requireCluster(clusterId);
        ClusterMemberGroup group = requireGroup(clusterId, groupId);
        String name = change.name();
        Set<Integer> memberIds = change.memberIds();
        Set<ClusterPermission> permissions = change.permissions();
        Set<Integer> moved = Transactions.call(() -> {
            Set<Integer> affected = new HashSet<>();
            if (name != null) rename(group, name);
            if (memberIds != null) affected.addAll(writeGroupMembers(clusterId, groupId, memberIds));
            if (permissions != null) affected.addAll(writeGroupPermissions(groupId, permissions));
            return affected;
        });
        announce(cluster, moved);
    }

    private void rename(ClusterMemberGroup group, String name) {
        String checked = GroupNames.require(
                name,
                group.name(),
                namesOfOtherGroups(group.clusterId(), group.id()),
                ClusterRefusal.CLUSTER_MEMBER_GROUP_NAME_MISSING_ON_CHANGE,
                ClusterRefusal.CLUSTER_MEMBER_GROUP_NAME_TAKEN_ON_CHANGE);
        clusterRepository.renameGroup(group.id(), checked);
        log.info("Cluster {} renamed group {} to '{}'", group.clusterId(), group.id(), checked);
    }

    /**
     * @return everybody who joined or left
     */
    private List<Integer> writeGroupMembers(int clusterId, int groupId, Collection<Integer> memberIds) {
        var change = SetDiff.of(clusterRepository.findGroupMemberIds(groupId), memberIds);
        change.added().forEach(memberId -> requireMember(clusterId, memberId));
        change.removed().forEach(memberId -> clusterRepository.removeFromGroup(groupId, memberId));
        change.added().forEach(memberId -> clusterRepository.addToGroup(groupId, memberId));
        log.info(
                "Cluster {} put {} member(s) in group {} and took {} out",
                clusterId,
                change.added().size(),
                groupId,
                change.removed().size());
        return change.touched();
    }

    /**
     * @return everybody in the group, where what it carries changed
     */
    private List<Integer> writeGroupPermissions(int groupId, Set<ClusterPermission> permissions) {
        var change = SetDiff.of(clusterRepository.findGroupPermissions(groupId), permissions);
        if (change.isEmpty()) return List.of();

        Map<ClusterPermission, Integer> granted = new EnumMap<>(ClusterPermission.class);
        for (ClusterPermission permission : change.added()) {
            granted.put(
                    permission,
                    clusterRepository
                            .findPermissionId(permission)
                            .orElseThrow(() ->
                                    ClusterRefusal.CLUSTER_MEMBER_GROUP_PERMISSION_UNKNOWN.raise(permission.name())));
        }
        for (ClusterPermission permission : change.removed()) {
            clusterRepository
                    .findPermissionId(permission)
                    .ifPresent(id -> clusterRepository.revokeFromGroup(groupId, id));
        }
        granted.values().forEach(id -> clusterRepository.grantToGroup(groupId, id));
        log.info("Cluster group {} now carries {}", groupId, permissions);
        return clusterRepository.findGroupMemberIds(groupId);
    }

    private List<String> namesOfOtherGroups(int clusterId, @Nullable Integer exceptGroupId) {
        return clusterRepository.findGroups(clusterId).stream()
                .filter(group -> exceptGroupId == null || group.id() != exceptGroupId)
                .map(ClusterMemberGroup::name)
                .toList();
    }

    private void announce(Cluster cluster, Collection<Integer> memberIds) {
        memberIds.forEach(memberId -> eventBus.publish(new ClusterMemberRoleChanged(memberId, cluster.name())));
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
        var change = SetDiff.of(
                clusterRepository.findGroupsOfMember(memberId).stream()
                        .map(ClusterMemberGroup::id)
                        .toList(),
                groupIds);
        if (change.isEmpty()) return;

        change.added().forEach(groupId -> requireGroup(clusterId, groupId));
        Transactions.run(() -> {
            change.removed().forEach(groupId -> clusterRepository.removeFromGroup(groupId, memberId));
            change.added().forEach(groupId -> clusterRepository.addToGroup(groupId, memberId));
        });
        log.info(
                "Cluster {} moved member {} into groups {} and out of {}",
                clusterId,
                memberId,
                change.added(),
                change.removed());
        announce(cluster, Set.of(memberId));
    }

    /**
     * Replaces what a group carries. Everybody in it is told, because their standing has moved.
     *
     * @param clusterId   the cluster
     * @param groupId     the group
     * @param permissions what it now carries
     */
    public void setGroupPermissions(int clusterId, int groupId, Set<ClusterPermission> permissions) {
        updateGroup(clusterId, groupId, new GroupChange(null, permissions, null));
    }

    private Cluster requireCluster(int clusterId) {
        return clusterRepository.findById(clusterId).orElseThrow(ClusterRefusal.CLUSTER_MEMBER_CLUSTER_NOT_HERE::raise);
    }

    /**
     * The member, checked against the cluster acting, so one cluster cannot reach into another's people.
     */
    private ClusterMember requireMember(int clusterId, int memberId) {
        ClusterMember member =
                clusterRepository.findMemberById(memberId).orElseThrow(ClusterRefusal.CLUSTER_MEMBER_NOT_HERE::raise);
        if (member.clusterId() != clusterId) throw ClusterRefusal.CLUSTER_MEMBER_NOT_HERE.raise();
        return member;
    }

    private ClusterMemberGroup requireGroup(int clusterId, int groupId) {
        ClusterMemberGroup group = clusterRepository
                .findGroupById(groupId)
                .orElseThrow(ClusterRefusal.CLUSTER_MEMBER_GROUP_NOT_HERE::raise);
        if (group.clusterId() != clusterId) throw ClusterRefusal.CLUSTER_MEMBER_GROUP_NOT_HERE.raise();
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

    /**
     * The parts of a group one change touches. A part left {@code null} stays as it is.
     *
     * @param name        its new name
     * @param permissions everything it carries afterwards
     * @param memberIds   everybody in it afterwards
     */
    public record GroupChange(
            @Nullable String name,
            @Nullable Set<ClusterPermission> permissions,
            @Nullable Set<Integer> memberIds) {}

    /**
     * A member of a cluster as the member list shows them, named by the account behind them.
     *
     * @param member the member
     * @return the row, with no account details where the account is gone
     */
    public ClusterMemberResponse describe(ClusterMember member) {
        var account = accountRepository.findById(member.accountId());
        return new ClusterMemberResponse(
                member.id(),
                account.map(Account::uid).map(UUID::toString).orElse(null),
                account.map(a -> NameParts.of(a).identified()).orElse(null),
                account.map(Account::email).orElse(null),
                member.userType().name());
    }

    /**
     * @param accountUid the account behind this member, which is what their picture is keyed by. An
     *                   association's person need belong to no station, so there is no member of a
     *                   station to draw them as: the account is the only handle every one of them has.
     */
    public record ClusterMemberResponse(
            int id,
            @Nullable String accountUid,
            @Nullable String name,
            @Nullable String email,
            String userType) {}
}
