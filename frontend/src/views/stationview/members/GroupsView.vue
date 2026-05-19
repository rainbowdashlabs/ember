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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {MemberGroup, Role, StationMember} from '@/api/types'
import {Roles} from '@/api/types'
import {memberGroups, stationMembers} from '@/api'
import {useStations} from '@/composables/useStations'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const {currentStationId} = useStations()
const {canManageMembers, isManager} = useSession()

const groups = ref<MemberGroup[]>([])
const allMembers = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')

// Selected group
const selectedGroup = ref<MemberGroup | null>(null)
const groupMembers = ref<StationMember[]>([])
const groupRoles = ref<Role[]>([])
const allRoles = ref<Role[]>([])
const groupLoading = ref(false)

const canEditRoles = computed(() => canManageMembers() || isManager())

const roleFriendlyNames: Record<string, string> = {
  LOGIN: 'Login',
  MEMBER: 'Mitglied',
  TEAM: 'Team',
  MEMBER_MANAGER: 'Mitgliedsmanager',
  ATTENDENCE_MANAGEMENT: 'Anwesenheitsverwaltung',
  ATTENDENCE_EXPORT_MANAGER: 'Anwesenheitsexport',
  INVENTORY_MANAGEMENT: 'Inventarverwaltung',
  EVENT_MANAGEMENT: 'Terminverwaltung',
  MEMBER_MANAGEMENT: 'Mitgliederverwaltung',
  MANAGER: 'Manager',
  NEWS_MANAGEMENT: 'Neuigkeiten',
}

const assignableRoles = computed(() => {
  const assignable = [
    Roles.MEMBER, Roles.TEAM, Roles.MEMBER_MANAGER, Roles.LOGIN,
    Roles.ATTENDENCE_MANAGEMENT, Roles.ATTENDENCE_EXPORT_MANAGER, Roles.INVENTORY_MANAGEMENT,
    Roles.EVENT_MANAGEMENT, Roles.MEMBER_MANAGEMENT, Roles.MANAGER, Roles.NEWS_MANAGEMENT,
  ] as readonly string[]
  return allRoles.value.filter(r => assignable.includes(r.role))
})

const groupRoleIds = computed(() => new Set(groupRoles.value.map(r => r.id)))

// Create/Edit modal
const showGroupModal = ref(false)
const editingGroup = ref<MemberGroup | null>(null)
const groupName = ref('')
const groupSaving = ref(false)

// Delete modal
const showDeleteModal = ref(false)
const deleteTarget = ref<MemberGroup | null>(null)

// Convert to tag modal
const showConvertModal = ref(false)
const convertTarget = ref<MemberGroup | null>(null)

const availableMembers = computed(() => {
  const memberIds = new Set(groupMembers.value.map(m => m.id))
  return allMembers.value.filter(m => !memberIds.has(m.id))
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
      stationMembers.listMembers(currentStationId.value!),
      stationMembers.listAllRoles(),
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
      memberGroups.getGroupRoles(group.id),
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
  showGroupModal.value = true
}

function openEditGroup(group: MemberGroup) {
  editingGroup.value = group
  groupName.value = group.name ?? ''
  showGroupModal.value = true
}

async function saveGroup() {
  groupSaving.value = true
  error.value = ''
  try {
    if (editingGroup.value) {
      await memberGroups.updateGroup(editingGroup.value.id, {name: groupName.value})
    } else {
      await memberGroups.createGroup({name: groupName.value})
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

async function toggleGroupRole(role: Role) {
  if (!selectedGroup.value) return
  const currentIds = groupRoles.value.map(r => r.id)
  const newIds = currentIds.includes(role.id)
      ? currentIds.filter(id => id !== role.id)
      : [...currentIds, role.id]
  try {
    groupRoles.value = await memberGroups.setGroupRoles(selectedGroup.value.id, {roleIds: newIds})
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
            <PrimaryButton @click="openCreateGroup">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
              {{ t('memberGroups.create') }}
            </PrimaryButton>
          </div>

          <div v-if="groups.length === 0" class="text-center text-(--text-muted) py-8">
            {{ t('memberGroups.empty') }}
          </div>

          <div class="space-y-2">
            <NeutralContainer
                v-for="group in groups"
                :key="group.id"
                :class="selectedGroup?.id === group.id ? 'border-primary' : 'hover:border-primary'"
                class="flex items-center justify-between cursor-pointer transition-colors"
                @click="selectGroup(group)"
            >
              <span class="font-medium">{{ group.name }}</span>
              <div class="flex items-center gap-2">
                <IconButton :icon="['fas', 'hashtag']" :label="t('memberGroups.convertToTag')" class="text-(--text-muted) hover:text-primary" @click.stop="requestConvertToTag(group)"/>
                <EditButton @click.stop="openEditGroup(group)"/>
                <DeleteButton @click.stop="requestDelete(group)"/>
              </div>
            </NeutralContainer>
          </div>
        </div>

        <!-- Group members panel -->
        <div v-if="selectedGroup" class="space-y-4">
          <SectionHeader>{{ selectedGroup.name }}</SectionHeader>

          <Spinner v-if="groupLoading" size="md"/>

          <template v-if="!groupLoading">
            <!-- Group roles -->
            <div v-if="canEditRoles" class="space-y-2">
              <label class="block text-sm font-medium text-(--text-muted)">{{ t('memberGroups.roles') }}</label>
              <div class="flex flex-wrap gap-2">
                <button
                    v-for="role in assignableRoles"
                    :key="role.id"
                    :class="groupRoleIds.has(role.id)
                    ? 'border-primary bg-primary/10 text-primary ring-1 ring-primary/30'
                    : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
                    class="rounded-lg px-3 py-1.5 text-xs font-medium border transition-all"
                    type="button"
                    @click="toggleGroupRole(role)"
                >
                  {{ roleFriendlyNames[role.role] ?? role.role }}
                </button>
              </div>
              <p v-if="groupRoles.length === 0" class="text-xs text-(--text-muted)">{{ t('memberGroups.noRoles') }}</p>
            </div>

            <!-- Current members -->
            <div class="space-y-1">
              <label class="block text-sm font-medium text-(--text-muted)">{{
                  t('memberGroups.currentMembers')
                }}</label>
              <div v-if="groupMembers.length === 0" class="text-sm text-(--text-muted) py-2">
                {{ t('memberGroups.noMembers') }}
              </div>
              <div class="space-y-1">
                <div v-for="member in groupMembers" :key="member.id"
                     class="flex items-center justify-between rounded-lg px-3 py-2 bg-bg-light-accent dark:bg-bg-dark-accent">
                  <div>
                    <MemberName :name="memberDisplayName(member)" :account-id="member.accountId" class="text-sm font-medium"/>
                    <span v-if="member.name && member.email" class="ml-2 text-xs text-(--text-muted)">{{
                        member.email
                      }}</span>
                  </div>
                  <button class="text-error hover:text-error/80 text-sm" @click="removeMemberFromGroup(member)">
                    <font-awesome-icon :icon="['fas', 'xmark']"/>
                  </button>
                </div>
              </div>
            </div>

            <!-- Available members to add -->
            <div class="space-y-1">
              <label class="block text-sm font-medium text-(--text-muted)">{{ t('memberGroups.addMembers') }}</label>
              <div v-if="availableMembers.length === 0" class="text-sm text-(--text-muted) py-2">
                {{ t('memberGroups.allAdded') }}
              </div>
              <div class="space-y-1">
                <div
                    v-for="member in availableMembers"
                    :key="member.id"
                    class="flex items-center justify-between rounded-lg px-3 py-2 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent cursor-pointer transition-colors"
                    @click="addMemberToGroup(member)"
                >
                  <div>
                    <MemberName :name="memberDisplayName(member)" :account-id="member.accountId" class="text-sm font-medium"/>
                    <span v-if="member.name && member.email" class="ml-2 text-xs text-(--text-muted)">{{
                        member.email
                      }}</span>
                  </div>
                  <font-awesome-icon :icon="['fas', 'plus']" class="text-primary text-sm"/>
                </div>
              </div>
            </div>
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
            <label class="block text-sm font-medium">{{ t('memberGroups.name') }}</label>
            <TextInput v-model="groupName" :placeholder="t('memberGroups.namePlaceholder')"/>
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
