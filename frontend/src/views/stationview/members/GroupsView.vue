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
import type {MemberGroup, PermissionGrant, StationMember} from '@/api/types'
import {StationPermission} from '@/api/types'
import {memberGroups, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import GroupListPanel from './groupsview/GroupListPanel.vue'
import GroupDetailPanel from './groupsview/GroupDetailPanel.vue'
import GroupFormModal from './groupsview/GroupFormModal.vue'
import GroupDeleteModal from './groupsview/GroupDeleteModal.vue'
import GroupConvertModal from './groupsview/GroupConvertModal.vue'

const {t} = useI18n()
const {canManageMembers, isManager, hasPermission} = useSession()
const canConvertToTag = computed(() => hasPermission(StationPermission.MEMBER_MANAGE_TAGS))

const groups = ref<MemberGroup[]>([])
const allMembers = ref<StationMember[]>([])

const selectedGroup = ref<MemberGroup | null>(null)
const groupMembers = ref<StationMember[]>([])
const groupRoles = ref<PermissionGrant[]>([])
const allRoles = ref<PermissionGrant[]>([])
const groupLoading = ref(false)

const canEditRoles = computed(() => canManageMembers() || isManager())

const groupRoleIds = computed({
  get: () => new Set(groupRoles.value.map(r => r.id)),
  set: (newIds: Set<number>) => syncGroupRoles(newIds),
})

const showGroupModal = ref(false)
const editingGroup = ref<MemberGroup | null>(null)
const groupName = ref('')
const groupColor = ref('')
const groupPosition = ref(0)
const groupSaving = ref(false)

const {loading, error} = useAsyncLoader(async () => {
  const [g, m, r] = await Promise.all([
    memberGroups.listGroups(),
    stationMembers.listMembers(),
    stationMembers.listAllPermissions(),
  ])
  groups.value = g
  allMembers.value = m
  allRoles.value = r
})

const {
  show: showDeleteModal,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<MemberGroup>({
  onDelete: g => memberGroups.deleteGroup(g.id),
  onSuccess: async deleted => {
    if (selectedGroup.value?.id === deleted.id) {
      selectedGroup.value = null
      groupMembers.value = []
    }
    groups.value = await memberGroups.listGroups()
  },
  error,
})

const {
  show: showConvertModal,
  target: convertTarget,
  request: requestConvertToTag,
  confirm: confirmConvertToTag,
} = useConfirmAction<MemberGroup>({
  onConfirm: g => memberGroups.convertToTag(g.id),
  onSuccess: async converted => {
    if (selectedGroup.value?.id === converted.id) {
      selectedGroup.value = null
      groupMembers.value = []
    }
    groups.value = await memberGroups.listGroups()
  },
  error,
})

const sortedGroupMembers = computed(() =>
    [...groupMembers.value].sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
)

const availableMembers = computed(() => {
  const memberIds = new Set(groupMembers.value.map(m => m.id))
  return allMembers.value
      .filter(m => !memberIds.has(m.id))
      .sort((a, b) => memberDisplayName(a).localeCompare(memberDisplayName(b)))
})

function memberDisplayName(member: StationMember): string {
  if (member.name && member.name.trim()) return member.name
  return member.email ?? `#${member.id}`
}

async function selectGroup(group: MemberGroup) {
  selectedGroup.value = group
  groupLoading.value = true
  try {
    const [members, roles] = await Promise.all([
      memberGroups.getGroupMembers(group.id),
      memberGroups.getGroupPermissions(group.id),
    ])
    groupMembers.value = members
    groupRoles.value = roles
  } catch {
    error.value = t('common.error')
    groupMembers.value = []
    groupRoles.value = []
  } finally {
    groupLoading.value = false
  }
}

function openCreateGroup() {
  editingGroup.value = null
  groupName.value = ''
  groupColor.value = ''
  groupPosition.value = 0
  showGroupModal.value = true
}

function openEditGroup(group: MemberGroup) {
  editingGroup.value = group
  groupName.value = group.name ?? ''
  groupColor.value = group.color ?? ''
  groupPosition.value = group.position ?? 0
  showGroupModal.value = true
}

async function saveGroup() {
  groupSaving.value = true
  error.value = ''
  try {
    if (editingGroup.value) {
      await memberGroups.updateGroup(editingGroup.value.id, {name: groupName.value, color: groupColor.value || null, position: groupPosition.value})
    } else {
      await memberGroups.createGroup({name: groupName.value, color: groupColor.value || null, position: groupPosition.value})
    }
    showGroupModal.value = false
    groups.value = await memberGroups.listGroups()
    if (selectedGroup.value && editingGroup.value?.id === selectedGroup.value.id) {
      selectedGroup.value = groups.value.find(g => g.id === selectedGroup.value!.id) ?? null
    }
  } catch {
    error.value = t('common.error')
  } finally {
    groupSaving.value = false
  }
}

async function addMemberToGroup(member: StationMember) {
  if (!selectedGroup.value) return
  const newIds = [...groupMembers.value.map(m => m.id), member.id]
  try {
    groupMembers.value = await memberGroups.setGroupMembers(selectedGroup.value.id, {memberIds: newIds})
  } catch {
    error.value = t('common.error')
  }
}

async function removeMemberFromGroup(member: StationMember) {
  if (!selectedGroup.value) return
  const newIds = groupMembers.value.filter(m => m.id !== member.id).map(m => m.id)
  try {
    groupMembers.value = await memberGroups.setGroupMembers(selectedGroup.value.id, {memberIds: newIds})
  } catch {
    error.value = t('common.error')
  }
}

async function syncGroupRoles(newIds: Set<number>) {
  if (!selectedGroup.value) return
  try {
    groupRoles.value = await memberGroups.setGroupPermissions(selectedGroup.value.id, {permissionIds: [...newIds]})
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    error.value = msg || t('common.error')
  }
}

</script>

<template>
  <ViewContent
      :title="t('pages.members-groups.title')"
      :subtitle="t('pages.members-groups.subtitle')"
  >
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

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
