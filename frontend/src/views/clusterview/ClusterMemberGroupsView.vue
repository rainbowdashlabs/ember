/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import GroupListPanel from '@/views/stationview/members/groupsview/GroupListPanel.vue'
import GroupDetailPanel from '@/views/stationview/members/groupsview/GroupDetailPanel.vue'
import GroupFormModal from '@/views/stationview/members/groupsview/GroupFormModal.vue'
import GroupDeleteModal from '@/views/stationview/members/groupsview/GroupDeleteModal.vue'
import {useMemberAssignment} from '@/views/stationview/members/useMemberAssignment'
import {memberDisplayName} from '@/views/stationview/members/listview/useMemberData'
import {clusterMembers, data} from '@/api'
import {clusterMemberIdentity, type ClusterMemberSummary} from '@/api/clusterMembers'
import {ClusterPermission} from '@/api/clusters'
import {useSession} from '@/composables/useSession'
import {useGroupsConfig, type GroupsPort} from '@/composables/useGroupsConfig'
import type {PermissionGrant} from '@/api/types'

const {t} = useI18n()
const {hasClusterPermission} = useSession()

const editable = computed(() => hasClusterPermission(ClusterPermission.CLUSTER_MEMBER_MANAGER))

/**
 * The association's permissions in the shape the shared picker speaks.
 *
 * <p>The picker identifies a selection by a numeric grant id, because a station's permissions are
 * rows. An association's are not: its API speaks their names throughout. Numbering them here is what
 * lets the one picker draw both, and the numbers never leave this file.
 */
const grants = ref<PermissionGrant[]>([])
const idByName = computed(() => new Map(grants.value.map(g => [g.permission, g.id])))
const nameById = computed(() => new Map(grants.value.map(g => [g.id, g.permission])))

/**
 * A member in the shape the shared group panels draw a person in.
 *
 * <p>Those panels read one field for the whole row: the identity. An association's people are accounts
 * rather than members of a station, so the identity is built from the account here instead of arriving
 * from the server, and without it every row showed a blank name beside an empty avatar.
 */
function drawable(member: ClusterMemberSummary) {
  return {...member, identity: clusterMemberIdentity(member)}
}

/** An association's group gathers the people who run it, carries no colour and cannot become a tag. */
const port: GroupsPort = {
  listGroups: () => clusterMembers.listGroups(),
  listCandidates: async () => (await clusterMembers.listMembers()).map(drawable),
  listAllRoles: async () => {
    const hierarchy = await data.getClusterPermissionHierarchy().catch(() => [])
    grants.value = hierarchy.map((node, index) => ({id: index + 1, permission: node.name}))
    return grants.value
  },
  getDetail: async (groupId) => {
    const [detail, all] = await Promise.all([
      clusterMembers.getGroup(groupId),
      clusterMembers.listMembers(),
    ])
    const held = new Set(detail.memberIds)
    return {
      members: all.filter(m => held.has(m.id)).map(drawable),
      roles: detail.permissions
          .map(name => idByName.value.get(name))
          .filter((id): id is number => !!id)
          .map(id => ({id, permission: nameById.value.get(id) ?? ''})),
    }
  },
  createGroup: (patch) => clusterMembers.createGroup(patch.name),
  updateGroup: (groupId, patch) => clusterMembers.updateGroup(groupId, {name: patch.name}),
  deleteGroup: (groupId) => clusterMembers.deleteGroup(groupId),
  setMembers: (groupId, memberIds) => clusterMembers.updateGroup(groupId, {memberIds}),
  setRoles: async (groupId, roleIds) => {
    const names = roleIds.map(id => nameById.value.get(id)).filter((n): n is string => !!n)
    await clusterMembers.updateGroup(groupId, {permissions: names})
    return roleIds.map(id => ({id, permission: nameById.value.get(id) ?? ''}))
  },
}

const {
  groups, allMembers, allRoles, selectedGroup, groupMembers, groupRoles, groupRoleIds,
  groupLoading, loading, error, showGroupModal, editingGroup, groupName, groupColor,
  groupSaving, groupSaveError, selectGroup, openCreateGroup, openEditGroup, saveGroup,
  showDeleteModal, deleteTarget, requestDelete, confirmDelete,
} = useGroupsConfig(port, {
    hasColour: false,
    canConvertToTag: false,
    hasPermissions: true,
    permissionScope: 'cluster',
    holds: 'members',
})

const sortedGroupMembers = computed(() =>
    [...groupMembers.value].sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
)

const {
  availableMembers,
  addMember: addMemberToGroup,
  removeMember: removeMemberFromGroup,
} = useMemberAssignment(
    allMembers,
    groupMembers,
    async ids => {
      await clusterMembers.updateGroup(selectedGroup.value!.id, {memberIds: ids})
      const held = new Set(ids)
      return allMembers.value.filter(m => held.has(m.id))
    },
    error,
)
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-member-groups.subtitle')" :title="t('pages.cluster-member-groups.title')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error || groupSaveError" variant="error">{{ error || groupSaveError }}</Alert>

      <div v-if="!loading" class="grid gap-6 lg:grid-cols-2">
        <GroupListPanel :groups="groups" :selected-group="selectedGroup" :can-convert-to-tag="false"
                        @create="openCreateGroup" @select="selectGroup" @edit="openEditGroup"
                        @delete="requestDelete"/>
        <GroupDetailPanel v-if="selectedGroup" :selected-group="selectedGroup" :group-loading="groupLoading"
                          :sorted-group-members="sortedGroupMembers" :available-members="availableMembers"
                          :group-roles="groupRoles" :all-roles="allRoles" v-model:group-role-ids="groupRoleIds"
                          :can-edit-roles="editable" @add-member="addMemberToGroup"
                          @remove-member="removeMemberFromGroup"/>
        <div v-else class="flex items-center justify-center text-(--text-muted) py-12">
          {{ t('memberGroups.selectHint') }}
        </div>
      </div>

      <GroupFormModal v-model="showGroupModal" :is-edit="!!editingGroup" v-model:name="groupName"
                      v-model:color="groupColor" :saving="groupSaving" @save="saveGroup"/>
      <GroupDeleteModal v-model="showDeleteModal" :target="deleteTarget" @confirm="confirmDelete"/>
    </div>
  </ViewContent>
</template>
