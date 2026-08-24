/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {StationPermission, type MemberGroup} from '@/api/types'
import {memberGroups, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'
import {useConfirmAction} from '@/composables/useConfirmAction'
import GroupListPanel from './groupsview/GroupListPanel.vue'
import GroupDetailPanel from './groupsview/GroupDetailPanel.vue'
import GroupFormModal from './groupsview/GroupFormModal.vue'
import GroupDeleteModal from './groupsview/GroupDeleteModal.vue'
import GroupConvertModal from './groupsview/GroupConvertModal.vue'
import {useMemberAssignment} from './useMemberAssignment'
import {memberDisplayName} from './listview/useMemberData'
import {useGroupsConfig, type GroupsPort} from '@/composables/useGroupsConfig'

const {t} = useI18n()
const {canManageMembers, isManager, hasPermission} = useSession()

const canEditRoles = computed(() => canManageMembers() || isManager())

/** A station's groups gather its own members, carry a colour and an order, and can become tags. */
const port: GroupsPort = {
  listGroups: () => memberGroups.listGroups(),
  listCandidates: () => stationMembers.listMembers(),
  listAllRoles: () => stationMembers.listAllPermissions(),
  getDetail: async (groupId) => {
    const [members, roles] = await Promise.all([
      memberGroups.getGroupMembers(groupId),
      memberGroups.getGroupPermissions(groupId),
    ])
    return {members, roles}
  },
  createGroup: (patch) => memberGroups.createGroup(patch),
  updateGroup: (groupId, patch) => memberGroups.updateGroup(groupId, patch),
  deleteGroup: (groupId) => memberGroups.deleteGroup(groupId),
  setMembers: (groupId, memberIds) => memberGroups.setGroupMembers(groupId, {memberIds}),
  setRoles: (groupId, roleIds) => memberGroups.setGroupPermissions(groupId, {permissionIds: roleIds}),
  convertToTag: (groupId) => memberGroups.convertToTag(groupId),
}

const {
  groups, allMembers, allRoles, selectedGroup, groupMembers, groupRoles, groupRoleIds,
  groupLoading, loading, error, showGroupModal, editingGroup, groupName, groupColor,
  groupSaving, groupSaveError, selectGroup, openCreateGroup, openEditGroup, saveGroup,
  showDeleteModal, deleteTarget, requestDelete, confirmDelete, refreshAfter,
} = useGroupsConfig(port, {
  hasColour: true,
  canConvertToTag: hasPermission(StationPermission.MEMBER_MANAGE_TAGS),
  hasPermissions: true,
  holds: 'members',
})

const canConvertToTag = computed(() => hasPermission(StationPermission.MEMBER_MANAGE_TAGS))

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
    ids => memberGroups.setGroupMembers(selectedGroup.value!.id, {memberIds: ids}),
    error,
)

const {
  show: showConvertModal,
  target: convertTarget,
  request: requestConvertToTag,
  confirm: confirmConvertToTag,
} = useConfirmAction<MemberGroup>({
  onConfirm: g => memberGroups.convertToTag(g.id),
  onSuccess: converted => refreshAfter(converted.id),
  error,
})
</script>

<template>
  <ViewContent
      :title="t('pages.members-groups.title')"
      :subtitle="t('pages.members-groups.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error || groupSaveError" variant="error">{{ error || groupSaveError }}</Alert>

      <div v-if="!loading" class="grid gap-6 lg:grid-cols-2">
        <GroupListPanel :groups="groups" :selected-group="selectedGroup" :can-convert-to-tag="canConvertToTag"
                        @create="openCreateGroup" @select="selectGroup" @edit="openEditGroup"
                        @delete="requestDelete" @convert="requestConvertToTag"/>
        <GroupDetailPanel v-if="selectedGroup" :selected-group="selectedGroup" :group-loading="groupLoading"
                          :sorted-group-members="sortedGroupMembers" :available-members="availableMembers"
                          :group-roles="groupRoles" :all-roles="allRoles" v-model:group-role-ids="groupRoleIds"
                          :can-edit-roles="canEditRoles" @add-member="addMemberToGroup"
                          @remove-member="removeMemberFromGroup"/>
        <div v-else class="flex items-center justify-center text-(--text-muted) py-12">
          {{ t('memberGroups.selectHint') }}
        </div>
      </div>

      <GroupFormModal v-model="showGroupModal" :is-edit="!!editingGroup" v-model:name="groupName"
                      v-model:color="groupColor" :saving="groupSaving" @save="saveGroup"/>
      <GroupDeleteModal v-model="showDeleteModal" :target="deleteTarget" @confirm="confirmDelete"/>
      <GroupConvertModal v-model="showConvertModal" :target="convertTarget" @confirm="confirmConvertToTag"/>
    </div>
  </ViewContent>
</template>
