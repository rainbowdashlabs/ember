/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import IconButton from '@/components/button/IconButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PermissionPicker from '@/components/input/PermissionPicker.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import type {MemberGroup, PermissionGrant, StationMember} from '@/api/types'
import {memberGroups, stationMembers} from '@/api'
import {useSession} from '@/composables/useSession'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
import {StationPermission} from '@/api/types'

const {canManageMembers, isManager, hasPermission} = useSession()
const canConvertToTag = computed(() => hasPermission(StationPermission.MEMBER_MANAGE_TAGS))

const groups = ref<MemberGroup[]>([])
const allMembers = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')

// Selected group
const selectedGroup = ref<MemberGroup | null>(null)
const groupMembers = ref<StationMember[]>([])
const groupRoles = ref<PermissionGrant[]>([])
const allRoles = ref<PermissionGrant[]>([])
const groupLoading = ref(false)

const canEditRoles = computed(() => canManageMembers() || isManager())

// Detail panel tab
const detailTab = ref('members')
const detailTabs = computed(() => [
  {key: 'members', label: t('memberGroups.tabMembers')},
  {key: 'permissions', label: t('memberGroups.tabPermissions')},
])

const groupRoleIds = computed({
  get: () => new Set(groupRoles.value.map(r => r.id)),
  set: (newIds: Set<number>) => syncGroupRoles(newIds),
})

// Create/Edit modal
const showGroupModal = ref(false)
const editingGroup = ref<MemberGroup | null>(null)
const groupName = ref('')
const groupColor = ref('')
const groupPosition = ref(0)
const groupSaving = ref(false)

// Delete modal
const showDeleteModal = ref(false)
const deleteTarget = ref<MemberGroup | null>(null)

// Convert to tag modal
const showConvertModal = ref(false)
const convertTarget = ref<MemberGroup | null>(null)

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

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [g, m, r] = await Promise.all([
      memberGroups.listGroups(),
      stationMembers.listMembers(),
      stationMembers.listAllPermissions(),
    ])
    groups.value = g
    allMembers.value = m
    allRoles.value = r
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
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

function requestDelete(group: MemberGroup) {
  deleteTarget.value = group
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await memberGroups.deleteGroup(deleteTarget.value.id)
    showDeleteModal.value = false
    if (selectedGroup.value?.id === deleteTarget.value.id) {
      selectedGroup.value = null
      groupMembers.value = []
    }
    deleteTarget.value = null
    groups.value = await memberGroups.listGroups()
  } catch {
    error.value = t('common.error')
  }
}

function requestConvertToTag(group: MemberGroup) {
  convertTarget.value = group
  showConvertModal.value = true
}

async function confirmConvertToTag() {
  if (!convertTarget.value) return
  try {
    await memberGroups.convertToTag(convertTarget.value.id)
    showConvertModal.value = false
    if (selectedGroup.value?.id === convertTarget.value.id) {
      selectedGroup.value = null
      groupMembers.value = []
    }
    convertTarget.value = null
    groups.value = await memberGroups.listGroups()
  } catch {
    error.value = t('common.error')
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

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div v-if="!loading" class="grid gap-6 lg:grid-cols-2">
        <!-- Groups list -->
        <div class="space-y-4">
          <div class="flex items-center justify-between">
            <SectionHeader>{{ t('memberGroups.title') }}</SectionHeader>
            <PrimaryButton :icon="['fas', 'plus']" @click="openCreateGroup">
              {{ t('memberGroups.create') }}
            </PrimaryButton>
          </div>

          <EmptyState v-if="groups.length === 0">{{ t('memberGroups.empty') }}</EmptyState>

          <div class="space-y-2">
            <NeutralContainer
                v-for="group in groups"
                :key="group.id"
                :class="selectedGroup?.id === group.id ? 'border-primary' : 'hover:border-primary'"
                class="flex items-center justify-between gap-2 flex-wrap cursor-pointer transition-colors"
                @click="selectGroup(group)"
            >
              <span class="flex items-center gap-2">
                <span v-if="group.color" class="inline-block h-3 w-3 rounded-full" :style="{ backgroundColor: group.color }"></span>
                <span class="font-medium">{{ group.name }}</span>
              </span>
              <div class="flex items-center gap-2">
                <IconButton v-if="canConvertToTag" :icon="['fas', 'hashtag']" :label="t('memberGroups.convertToTag')" class="text-(--text-muted) hover:text-primary" @click.stop="requestConvertToTag(group)"/>
                <EditButton @click.stop="openEditGroup(group)"/>
                <DeleteButton @click.stop="requestDelete(group)"/>
              </div>
            </NeutralContainer>
          </div>
        </div>

        <!-- Group detail panel -->
        <div v-if="selectedGroup" class="space-y-4">
          <SectionHeader>{{ selectedGroup.name }}</SectionHeader>

          <Spinner v-if="groupLoading" size="md"/>

          <template v-if="!groupLoading">
            <TabBar v-model="detailTab" :tabs="detailTabs" />

            <!-- Members tab -->
            <template v-if="detailTab === 'members'">
              <div class="space-y-1">
                <FieldLabel class="text-(--text-muted)">{{ t('memberGroups.currentMembers') }}</FieldLabel>
                <MutedText tag="div" size="sm" class="py-2" v-if="groupMembers.length === 0">
                  {{ t('memberGroups.noMembers') }}
                </MutedText>
                <div class="space-y-1">
                  <div v-for="member in sortedGroupMembers" :key="member.id"
                       class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
                    <div>
                      <MemberName :identity="member.identity" class="text-sm font-medium"/>
                      <MutedText class="ml-2" v-if="member.name && member.email">{{ member.email }}</MutedText>
                    </div>
                    <IconButton :icon="['fas', 'xmark']" :label="t('memberGroups.removeMember')" class="text-error hover:text-error/80 text-sm" @click="removeMemberFromGroup(member)"/>
                  </div>
                </div>
              </div>

              <div class="space-y-1">
                <FieldLabel class="text-(--text-muted)">{{ t('memberGroups.addMembers') }}</FieldLabel>
                <MutedText tag="div" size="sm" class="py-2" v-if="availableMembers.length === 0">
                  {{ t('memberGroups.allAdded') }}
                </MutedText>
                <div class="space-y-1">
                  <div
                      v-for="member in availableMembers"
                      :key="member.id"
                      class="flex items-center justify-between rounded-lg px-3 py-2 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer transition-colors"
                      @click="addMemberToGroup(member)"
                  >
                    <div>
                      <MemberName :identity="member.identity" class="text-sm font-medium"/>
                      <MutedText class="ml-2" v-if="member.name && member.email">{{ member.email }}</MutedText>
                    </div>
                    <font-awesome-icon :icon="['fas', 'plus']" class="text-primary text-sm"/>
                  </div>
                </div>
              </div>
            </template>

            <!-- Permissions tab -->
            <template v-if="detailTab === 'permissions'">
              <div v-if="canEditRoles" class="space-y-2">
                <PermissionPicker v-model="groupRoleIds" :all-roles="allRoles" />
                <MutedText v-if="groupRoles.length === 0" size="sm">{{ t('memberGroups.noPermissions') }}</MutedText>
              </div>
              <MutedText v-else size="sm">{{ t('memberGroups.noPermissionAccess') }}</MutedText>
            </template>
          </template>
        </div>

        <div v-else class="flex items-center justify-center text-(--text-muted) py-12">
          {{ t('memberGroups.selectHint') }}
        </div>
      </div>

      <!-- Create/Edit group modal -->
      <Modal v-model="showGroupModal">
        <div class="space-y-4">
          <SectionHeader>{{
              editingGroup ? t('memberGroups.editTitle') : t('memberGroups.createTitle')
            }}
          </SectionHeader>
          <div class="space-y-1">
            <FieldLabel>{{ t('memberGroups.name') }}</FieldLabel>
            <TextInput v-model="groupName" :placeholder="t('memberGroups.namePlaceholder')"/>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('memberGroups.color') }}</FieldLabel>
            <div class="flex items-center gap-2">
              <ColorInput v-model="groupColor" />
              <SecondaryButton v-if="groupColor" compact @click="groupColor = ''">
                <font-awesome-icon :icon="['fas', 'xmark']" />
              </SecondaryButton>
              <MutedText size="sm">{{ t('memberGroups.colorHint') }}</MutedText>
            </div>
          </div>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showGroupModal = false">{{ t('memberGroups.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="groupSaving || !groupName" @click="saveGroup">
              {{ groupSaving ? t('common.loading') : t('memberGroups.save') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Delete modal -->
      <Modal v-model="showDeleteModal">
        <div class="space-y-4">
          <p>{{ t('memberGroups.deleteConfirm', {name: deleteTarget?.name}) }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showDeleteModal = false">{{ t('memberGroups.cancel') }}</SecondaryButton>
            <ErrorButton @click="confirmDelete">{{ t('memberGroups.delete') }}</ErrorButton>
          </div>
        </div>
      </Modal>

      <Modal v-model="showConvertModal">
        <div class="space-y-4">
          <p>{{ t('memberGroups.convertToTagConfirm', {name: convertTarget?.name}) }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showConvertModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton @click="confirmConvertToTag">{{ t('memberGroups.convertToTag') }}</PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
