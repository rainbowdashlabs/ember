/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {StationPermission} from '@/api/types'
import type {GroupRow} from '@/util/groupRules'
import {memberGroups, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'
import {useConfirmAction} from '@/composables/useConfirmAction'
import GroupListPanel from './groupsview/GroupListPanel.vue'
import GroupDetailPanel from './groupsview/GroupDetailPanel.vue'
import GroupFormModal from './groupsview/GroupFormModal.vue'
import GroupRulesFields from './groupsview/GroupRulesFields.vue'
import GroupSetPanel from './groupsview/GroupSetPanel.vue'
import GroupConflictModal from './groupsview/GroupConflictModal.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import GroupConvertModal from './groupsview/GroupConvertModal.vue'
import {useMemberAssignment} from './useMemberAssignment'
import {useGroupRulesForm} from './groupsview/useGroupRulesForm'
import {useGroupSets} from './groupsview/useGroupSets'
import {useSetMoves} from './groupsview/useSetMoves'
import {memberDisplayName} from './listview/useMemberData'
import {useGroupsConfig, type GroupsPort} from '@/composables/useGroupsConfig'

const {t} = useI18n()
const {canManageMembers, isManager, hasPermission} = useSession()

const canEditRoles = computed(() => canManageMembers() || isManager())

const rules = useGroupRulesForm()

/**
 * A station's groups gather its own members, carry a colour and an order, belong to a set and are
 * bound to member types, and can become tags. The rules ride with every save of the form.
 */
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
  createGroup: (patch) => rules.guarded(() => memberGroups.createGroup({
    ...patch,
    color: patch.color ?? undefined,
    rules: rules.payload(),
  })),
  updateGroup: (groupId, patch) => rules.guarded(() => memberGroups.updateGroup(groupId, {
    ...patch,
    color: patch.color ?? undefined,
    rules: rules.payload(),
    removeNonMatching: rules.removeNonMatching.value,
  })),
  deleteGroup: (groupId) => memberGroups.deleteGroup(groupId),
  setMembers: (groupId, memberIds) => memberGroups.setGroupMembers(groupId, {memberIds}),
  setRoles: (groupId, roleIds) => memberGroups.setGroupPermissions(groupId, {permissionIds: roleIds}),
  convertToTag: (groupId) => memberGroups.convertToTag(groupId),
}

const {
  groups, allMembers, allRoles, selectedGroup, groupMembers, groupRoles, groupRoleIds,
  groupLoading, loading, error, failure, showGroupModal, editingGroup, groupName, groupColor,
  groupSaving, groupSaveFailure, selectGroup, openCreateGroup, openEditGroup, saveGroup,
  showDeleteModal, deleteTarget, requestDelete, confirmDelete, refreshAfter,
} = useGroupsConfig(port, {
  hasColour: true,
  canConvertToTag: hasPermission(StationPermission.MEMBER_MANAGE_TAGS),
  hasPermissions: true,
  permissionScope: 'station',
  holds: 'members',
})

const canConvertToTag = computed(() => hasPermission(StationPermission.MEMBER_MANAGE_TAGS))

const sortedGroupMembers = computed(() =>
    [...groupMembers.value].sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
)

const {
  availableMembers,
  offeredUserTypes,
  addMember: addMemberToGroup,
  removeMember: removeMemberFromGroup,
} = useMemberAssignment(
    allMembers,
    groupMembers,
    ids => memberGroups.setGroupMembers(selectedGroup.value!.id, {memberIds: ids}),
    error,
    failure,
)

const moves = useSetMoves(groups, selectedGroup, groupMembers, addMemberToGroup, failure)

/** Everybody still to be added, marked where adding them moves them out of another group of the set. */
const offeredMembers = computed(() => availableMembers.value.map(option => {
  const from = moves.inSibling.value.get(Number(option.value))
  return from === undefined ? option : {...option, note: t('memberGroups.inGroup', {name: from})}
}))

const groupSets = useGroupSets(failure, async () => {
  groups.value = await memberGroups.listGroups()
})
onMounted(() => groupSets.load())

function createGroup() {
  rules.load(null)
  openCreateGroup()
}

function editGroup(group: GroupRow) {
  rules.load(group)
  openEditGroup(group)
}

async function removeMisfitsAndSave() {
  rules.conflict.value = null
  rules.removeNonMatching.value = true
  await saveGroup()
}

const {
  show: showConvertModal,
  target: convertTarget,
  request: requestConvertToTag,
  confirm: confirmConvertToTag,
} = useConfirmAction<GroupRow>({
  onConfirm: g => memberGroups.convertToTag(g.id),
  onSuccess: converted => refreshAfter(converted.id),
  failure,
})
</script>

<template>
  <ViewContent
      :title="t('pages.members-groups.title')"
      :subtitle="t('pages.members-groups.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <FailureAlert :failure="failure ?? (rules.conflict.value ? null : groupSaveFailure)"/>

      <div v-if="!loading" class="grid gap-6 lg:grid-cols-2">
        <div class="space-y-6">
          <GroupListPanel :groups="groups" :selected-group="selectedGroup" :can-convert-to-tag="canConvertToTag"
                          :sets="groupSets.sets.value"
                          @create="createGroup" @select="selectGroup" @edit="editGroup"
                          @delete="requestDelete" @convert="requestConvertToTag"/>
          <GroupSetPanel :sets="groupSets.sets.value" @create="groupSets.create" @rename="groupSets.rename"
                         @delete="groupSets.remove"/>
        </div>
        <GroupDetailPanel v-if="selectedGroup" v-model:group-role-ids="groupRoleIds" :selected-group="selectedGroup"
                          :group-loading="groupLoading" :sorted-group-members="sortedGroupMembers"
                          :available-members="offeredMembers"
                          :offered-user-types="offeredUserTypes" :group-roles="groupRoles" :all-roles="allRoles"
                          :can-edit-roles="canEditRoles" @add-member="moves.add"
                          @remove-member="removeMemberFromGroup"/>
        <div v-else class="flex items-center justify-center text-(--text-muted) py-12">
          {{ t('memberGroups.selectHint') }}
        </div>
      </div>

      <GroupFormModal v-model="showGroupModal" v-model:name="groupName" v-model:color="groupColor"
                      :is-edit="!!editingGroup" :saving="groupSaving" @save="saveGroup">
        <GroupRulesFields v-model:set-id="rules.groupSetId.value" v-model:user-types="rules.userTypes.value"
                          :sets="groupSets.sets.value"/>
      </GroupFormModal>
      <GroupConflictModal :conflict="rules.conflict.value" @close="rules.conflict.value = null"
                          @remove-and-save="removeMisfitsAndSave"/>
      <ConfirmDeleteModal v-model="showDeleteModal" :message="t('memberGroups.deleteConfirm', {name: deleteTarget?.name})"
                          @confirm="confirmDelete"/>
      <ConfirmDeleteModal :model-value="moves.pending.value !== null"
                          :title="t('memberGroups.moveTitle')"
                          :message="t('memberGroups.moveConfirm', {name: moves.pending.value?.from ?? ''})"
                          :confirm-label="t('memberGroups.move')"
                          @update:model-value="open => { if (!open) moves.pending.value = null }"
                          @confirm="moves.confirm"/>
      <GroupConvertModal v-model="showConvertModal" :target="convertTarget" @confirm="confirmConvertToTag"/>
    </div>
  </ViewContent>
</template>
