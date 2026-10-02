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
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import GroupListPanel from './GroupListPanel.vue'
import GroupDetailPanel from './GroupDetailPanel.vue'
import GroupFormModal from './GroupFormModal.vue'
import {useMemberAssignment} from './useMemberAssignment'
import {fromMember} from '@/components/input/select/memberOption'
import {useGroupsConfig, type GroupsCapabilities, type GroupsPort} from '@/composables/useGroupsConfig'

/**
 * A group screen with nothing of its own beyond the list, the open group and the form: the shape both of
 * an association's group screens take. What differs between them, where the groups live and what one
 * may hold, comes in through the port and the capabilities.
 *
 * <p>A station's group screen does more (sets, bindings, moves, tags) and builds on the same panels
 * itself rather than on this.
 */
const props = defineProps<{
  title: string
  subtitle: string
  /** What the empty side says before a group is opened. */
  selectHint: string
  port: GroupsPort
  capabilities: GroupsCapabilities
  /** Whether the reader may change what an open group grants. */
  canEditRoles: boolean
}>()

const {t} = useI18n()

const {
  groups, allMembers, allRoles, selectedGroup, groupMembers, groupRoles, groupRoleIds,
  groupLoading, loading, error, showGroupModal, editingGroup, groupName, groupColor,
  groupSaving, groupSaveError, selectGroup, openCreateGroup, openEditGroup, saveGroup,
  showDeleteModal, deleteTarget, requestDelete, confirmDelete,
} = useGroupsConfig(props.port, props.capabilities)

const sortedGroupMembers = computed(() =>
    [...groupMembers.value].sort((a, b) => fromMember(a).name.localeCompare(fromMember(b).name))
)

const {
  availableMembers,
  offeredUserTypes,
  addMember,
  removeMember,
} = useMemberAssignment(
    allMembers,
    groupMembers,
    async ids => {
      await props.port.setMembers(selectedGroup.value!.id, ids)
      const held = new Set(ids)
      return allMembers.value.filter(m => held.has(m.id))
    },
    error,
)
</script>

<template>
  <ViewContent :subtitle="subtitle" :title="title">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error || groupSaveError" variant="error">{{ error || groupSaveError }}</Alert>

      <div v-if="!loading" class="grid gap-6 lg:grid-cols-2">
        <GroupListPanel :groups="groups" :selected-group="selectedGroup" :can-convert-to-tag="false"
                        @create="openCreateGroup" @select="selectGroup" @edit="openEditGroup"
                        @delete="requestDelete"/>
        <GroupDetailPanel v-if="selectedGroup" v-model:group-role-ids="groupRoleIds" :selected-group="selectedGroup"
                          :group-loading="groupLoading" :sorted-group-members="sortedGroupMembers"
                          :available-members="availableMembers"
                          :offered-user-types="offeredUserTypes" :group-roles="groupRoles" :all-roles="allRoles"
                          :can-edit-roles="canEditRoles" @add-member="addMember"
                          @remove-member="removeMember"/>
        <div v-else class="flex items-center justify-center text-(--text-muted) py-12">
          {{ selectHint }}
        </div>
      </div>

      <GroupFormModal v-model="showGroupModal" v-model:name="groupName" v-model:color="groupColor"
                      :is-edit="!!editingGroup" :saving="groupSaving" @save="saveGroup"/>
      <ConfirmDeleteModal v-model="showDeleteModal" :message="t('memberGroups.deleteConfirm', {name: deleteTarget?.name})"
                          @confirm="confirmDelete"/>
    </div>
  </ViewContent>
</template>
