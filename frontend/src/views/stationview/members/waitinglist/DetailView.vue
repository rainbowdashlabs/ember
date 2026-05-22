/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import FormulaInput from '@/components/input/FormulaInput.vue'
import type {
  WaitingList,
  WaitingListEntryWithScore,
  WaitingListField,
  WaitingListInvite,
  MemberGroup,
  Role,
} from '@/api/types'
import { waitingList, memberGroups, stationMembers } from '@/api'
import { useBreakpoint } from '@/composables/useBreakpoint'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { isMobile } = useBreakpoint()

const listId = computed(() => Number(route.params.id))

const list = ref<WaitingList | null>(null)
const entries = ref<WaitingListEntryWithScore[]>([])
const invites = ref<WaitingListInvite[]>([])
const fields = ref<WaitingListField[]>([])
const groups = ref<MemberGroup[]>([])
const roles = ref<Role[]>([])
const fieldInfos = computed(() => fields.value.map(f => ({ name: f.name, type: f.fieldType })))
const loading = ref(true)
const error = ref('')
const success = ref('')

// Inline editing
const editing = ref(false)
const editName = ref('')
const editDescription = ref('')
const editScoringFormula = ref('')
const editConfirmInterval = ref(0)
const editTestingGroupId = ref<number | null>(null)
const editJoinGroupId = ref<number | null>(null)
const editJoinRoleId = ref<number | null>(null)
const editAttendanceThreshold = ref(5)
const saving = ref(false)

// Invite creation
const showInviteModal = ref(false)
const inviteMaxUses = ref<number | undefined>(undefined)
const inviteExpiresAt = ref('')
const creatingInvite = ref(false)

// Entry creation
const showEntryModal = ref(false)
const entryFirstname = ref('')
const entryLastname = ref('')
const entryParentName = ref('')
const entryEmail = ref('')
const entryNotes = ref('')
const creatingEntry = ref(false)

// Delete list
const showDeleteModal = ref(false)
const deletingList = ref(false)

// Delete entry
const showDeleteEntryModal = ref(false)
const deleteEntryTarget = ref<WaitingListEntryWithScore | null>(null)
const deletingEntry = ref(false)

// Computed entry groups
const waitingEntries = computed(() =>
  sortedEntries.value.filter(e => e.entry.status === 'WAITING' || e.entry.status === 'INVITED'),
)
const testingEntries = computed(() =>
  sortedEntries.value.filter(e => e.entry.status === 'TESTING'),
)
const finishedEntries = computed(() =>
  sortedEntries.value.filter(e => e.entry.status === 'JOINED' || e.entry.status === 'WITHDRAWN'),
)

const sortedEntries = computed(() =>
  [...entries.value].sort((a, b) => b.score - a.score),
)

const visibleFieldIds = computed(() => new Set(list.value?.visibleFields ?? []))
const visibleFields = computed(() => fields.value.filter(f => visibleFieldIds.value.has(f.id)))
const showFieldToggle = ref(false)

function entryFullName(item: WaitingListEntryWithScore): string {
  const e = item.entry
  return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
}

function getEntryFieldValue(item: WaitingListEntryWithScore, fieldId: number): string {
  return item.values.find(v => v.fieldId === fieldId)?.value ?? ''
}

async function toggleFieldVisibility(fieldId: number) {
  if (!list.value) return
  const current = new Set(list.value.visibleFields ?? [])
  if (current.has(fieldId)) current.delete(fieldId)
  else current.add(fieldId)
  try {
    list.value = await waitingList.updateVisibleFields(listId.value, [...current])
  } catch {
    error.value = t('common.error')
  }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [listData, entryData, inviteData, fieldData, groupData, roleData] = await Promise.all([
      waitingList.getById(listId.value),
      waitingList.listEntries(listId.value),
      waitingList.listInvites(listId.value),
      waitingList.listFields(listId.value),
      memberGroups.listGroups(),
      stationMembers.listAllRoles(),
    ])
    list.value = listData
    entries.value = entryData
    invites.value = inviteData
    fields.value = fieldData
    groups.value = groupData
    roles.value = roleData
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function startEditing() {
  if (!list.value) return
  editName.value = list.value.name
  editDescription.value = list.value.description ?? ''
  editScoringFormula.value = list.value.scoringFormula ?? ''
  editConfirmInterval.value = list.value.confirmIntervalDays ?? 0
  editTestingGroupId.value = list.value.testingGroupId ?? null
  editJoinGroupId.value = list.value.joinGroupId ?? null
  editJoinRoleId.value = list.value.joinRoleId ?? null
  editAttendanceThreshold.value = list.value.attendanceThreshold ?? 5
  editing.value = true
}

function cancelEditing() {
  editing.value = false
}

async function saveEditing() {
  if (!editName.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    list.value = await waitingList.update(listId.value, {
      name: editName.value.trim(),
      description: editDescription.value.trim(),
      scoringFormula: editScoringFormula.value.trim() || undefined,
      confirmIntervalDays: editConfirmInterval.value || undefined,
      testingGroupId: editTestingGroupId.value,
      joinGroupId: editJoinGroupId.value,
      joinRoleId: editJoinRoleId.value,
      attendanceThreshold: editAttendanceThreshold.value,
    })
    editing.value = false
    success.value = t('waitingList.saved')
    setTimeout(() => { success.value = '' }, 3000)
  } catch (e: unknown) {
    const msg = (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    error.value = msg || t('common.error')
  } finally {
    saving.value = false
  }
}

// Invites
function openInviteModal() {
  inviteMaxUses.value = undefined
  inviteExpiresAt.value = ''
  showInviteModal.value = true
}

async function createInvite() {
  creatingInvite.value = true
  error.value = ''
  try {
    await waitingList.createInvite(listId.value, {
      maxUses: inviteMaxUses.value || undefined,
      expiresAt: inviteExpiresAt.value || undefined,
    })
    invites.value = await waitingList.listInvites(listId.value)
    showInviteModal.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    creatingInvite.value = false
  }
}

async function deleteInvite(inviteId: number) {
  error.value = ''
  try {
    await waitingList.deleteInvite(listId.value, inviteId)
    invites.value = await waitingList.listInvites(listId.value)
  } catch {
    error.value = t('common.error')
  }
}

async function copyInviteLink(code: string) {
  const url = `${window.location.origin}/waiting-list/register?code=${code}`
  await navigator.clipboard.writeText(url)
  success.value = t('waitingList.linkCopied')
  setTimeout(() => { success.value = '' }, 3000)
}

// Entries
function openEntryModal() {
  entryFirstname.value = ''
  entryLastname.value = ''
  entryParentName.value = ''
  entryEmail.value = ''
  entryNotes.value = ''
  showEntryModal.value = true
}

async function createEntry() {
  if (!entryFirstname.value.trim() || !entryEmail.value.trim()) return
  creatingEntry.value = true
  error.value = ''
  try {
    await waitingList.createEntry(listId.value, {
      firstname: entryFirstname.value.trim(),
      lastname: entryLastname.value.trim(),
      parentName: entryParentName.value.trim(),
      email: entryEmail.value.trim(),
      notes: entryNotes.value.trim(),
    })
    entries.value = await waitingList.listEntries(listId.value)
    showEntryModal.value = false
  } catch {
    error.value = t('common.error')
  } finally {
    creatingEntry.value = false
  }
}

// State transitions
async function doInviteEntry(entryId: number) {
  error.value = ''
  try {
    await waitingList.inviteEntry(listId.value, entryId)
    entries.value = await waitingList.listEntries(listId.value)
    success.value = t('waitingList.entryInvited')
    setTimeout(() => { success.value = '' }, 3000)
  } catch {
    error.value = t('common.error')
  }
}

async function doMoveToTesting(entryId: number) {
  error.value = ''
  try {
    await waitingList.moveToTesting(listId.value, entryId)
    entries.value = await waitingList.listEntries(listId.value)
    success.value = t('waitingList.entryTesting')
    setTimeout(() => { success.value = '' }, 3000)
  } catch {
    error.value = t('common.error')
  }
}

async function doMoveToJoined(entryId: number) {
  error.value = ''
  try {
    await waitingList.moveToJoined(listId.value, entryId)
    entries.value = await waitingList.listEntries(listId.value)
    success.value = t('waitingList.entryJoined')
    setTimeout(() => { success.value = '' }, 3000)
  } catch {
    error.value = t('common.error')
  }
}

async function doWithdrawEntry(entryId: number) {
  error.value = ''
  try {
    await waitingList.withdrawEntry(listId.value, entryId)
    entries.value = await waitingList.listEntries(listId.value)
    success.value = t('waitingList.entryWithdrawn')
    setTimeout(() => { success.value = '' }, 3000)
  } catch {
    error.value = t('common.error')
  }
}

function requestDeleteEntry(entry: WaitingListEntryWithScore) {
  deleteEntryTarget.value = entry
  showDeleteEntryModal.value = true
}

async function confirmDeleteEntry() {
  if (!deleteEntryTarget.value) return
  deletingEntry.value = true
  error.value = ''
  try {
    await waitingList.deleteEntry(listId.value, deleteEntryTarget.value.entry.id)
    entries.value = await waitingList.listEntries(listId.value)
    showDeleteEntryModal.value = false
    deleteEntryTarget.value = null
  } catch {
    error.value = t('common.error')
  } finally {
    deletingEntry.value = false
  }
}

function navigateToEntry(entryId: number) {
  router.push({ name: 'waiting-list-entry', params: { id: listId.value, entryId } })
}

function navigateToFields() {
  router.push({ name: 'waiting-list-fields', params: { id: listId.value } })
}

function goBack() {
  router.push({ name: 'waiting-lists' })
}

async function confirmDeleteList() {
  deletingList.value = true
  error.value = ''
  try {
    await waitingList.deleteList(listId.value)
    router.push({ name: 'waiting-lists' })
  } catch {
    error.value = t('common.error')
  } finally {
    deletingList.value = false
  }
}

function statusBadgeComponent(status: string) {
  if (status === 'JOINED') return SuccessBadge
  if (status === 'WITHDRAWN') return ErrorBadge
  if (status === 'TESTING') return PrimaryBadge
  if (status === 'INVITED') return InfoBadge
  return SecondaryBadge
}

function formatDate(dateStr: string | undefined | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString()
}

function formatDateTime(dateStr: string | undefined | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleString()
}

function groupName(groupId: number | null | undefined): string {
  if (!groupId) return '-'
  return groups.value.find(g => g.id === groupId)?.name ?? '-'
}

function roleName(roleId: number | null | undefined): string {
  if (!roleId) return '-'
  return roles.value.find(r => r.id === roleId)?.role ?? '-'
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <SecondaryButton @click="goBack">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2" />
          {{ t('waitingList.back') }}
        </SecondaryButton>
        <div class="flex items-center gap-2 w-full sm:w-auto">
          <SecondaryButton :full-width="isMobile" class="flex-1 sm:flex-initial" @click="navigateToFields">
            <font-awesome-icon :icon="['fas', 'sliders']" class="mr-2" />
            {{ t('waitingList.manageFields') }}
          </SecondaryButton>
          <ErrorButton :full-width="isMobile" class="flex-1 sm:flex-initial" @click="showDeleteModal = true">
            <font-awesome-icon :icon="['fas', 'trash']" class="mr-2" />
            {{ t('waitingList.deleteList') }}
          </ErrorButton>
        </div>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading && list">
        <!-- Overview Section -->
        <NeutralContainer class="space-y-4">
          <div class="flex items-center justify-between">
            <SubHeader>{{ t('waitingList.overview') }}</SubHeader>
            <EditButton v-if="!editing" @click="startEditing" />
          </div>

          <template v-if="!editing">
            <div class="grid gap-3 sm:grid-cols-2">
              <div class="text-sm">
                <span class="text-(--text-muted)">{{ t('waitingList.name') }}:</span>
                <span class="ml-1 font-medium">{{ list.name }}</span>
              </div>
              <div class="text-sm">
                <span class="text-(--text-muted)">{{ t('waitingList.confirmInterval') }}:</span>
                <span class="ml-1 font-medium">{{ list.confirmIntervalDays ?? '-' }} {{ t('waitingList.days') }}</span>
              </div>
              <div class="text-sm sm:col-span-2">
                <span class="text-(--text-muted)">{{ t('waitingList.description') }}:</span>
                <span class="ml-1 font-medium">{{ list.description || '-' }}</span>
              </div>
              <div class="text-sm sm:col-span-2">
                <span class="text-(--text-muted)">{{ t('waitingList.scoringFormula') }}:</span>
                <span class="ml-1 font-medium font-mono text-xs">{{ list.scoringFormula || '-' }}</span>
              </div>
              <div class="text-sm">
                <span class="text-(--text-muted)">{{ t('waitingList.testingGroup') }}:</span>
                <span class="ml-1 font-medium">{{ groupName(list.testingGroupId) }}</span>
              </div>
              <div class="text-sm">
                <span class="text-(--text-muted)">{{ t('waitingList.joinGroup') }}:</span>
                <span class="ml-1 font-medium">{{ groupName(list.joinGroupId) }}</span>
              </div>
              <div class="text-sm">
                <span class="text-(--text-muted)">{{ t('waitingList.joinRole') }}:</span>
                <span class="ml-1 font-medium">{{ roleName(list.joinRoleId) }}</span>
              </div>
              <div class="text-sm">
                <span class="text-(--text-muted)">{{ t('waitingList.attendanceThreshold') }}:</span>
                <span class="ml-1 font-medium">{{ list.attendanceThreshold }}</span>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="space-y-3">
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('waitingList.name') }}</label>
                <TextInput v-model="editName" />
              </div>
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('waitingList.description') }}</label>
                <TextAreaInput v-model="editDescription" />
              </div>
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('waitingList.scoringFormula') }}</label>
                <FormulaInput v-model="editScoringFormula" :placeholder="t('waitingList.scoringFormulaPlaceholder')" :fields="fieldInfos" />
                <p class="text-xs text-(--text-muted)">{{ t('waitingList.scoringFormulaHint') }}</p>
              </div>
              <div class="space-y-1">
                <label class="block text-sm font-medium">{{ t('waitingList.confirmInterval') }}</label>
                <NumberInput v-model="editConfirmInterval" />
                <p class="text-xs text-(--text-muted)">{{ t('waitingList.confirmIntervalHint') }}</p>
              </div>
              <div class="grid gap-3 sm:grid-cols-2">
                <div class="space-y-1">
                  <label class="block text-sm font-medium">{{ t('waitingList.testingGroup') }}</label>
                  <SelectInput :model-value="editTestingGroupId != null ? String(editTestingGroupId) : ''" @update:model-value="editTestingGroupId = $event ? Number($event) : null">
                    <option value="">{{ t('waitingList.noGroup') }}</option>
                    <option v-for="g in groups" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
                  </SelectInput>
                </div>
                <div class="space-y-1">
                  <label class="block text-sm font-medium">{{ t('waitingList.joinGroup') }}</label>
                  <SelectInput :model-value="editJoinGroupId != null ? String(editJoinGroupId) : ''" @update:model-value="editJoinGroupId = $event ? Number($event) : null">
                    <option value="">{{ t('waitingList.noGroup') }}</option>
                    <option v-for="g in groups" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
                  </SelectInput>
                </div>
                <div class="space-y-1">
                  <label class="block text-sm font-medium">{{ t('waitingList.joinRole') }}</label>
                  <SelectInput :model-value="editJoinRoleId != null ? String(editJoinRoleId) : ''" @update:model-value="editJoinRoleId = $event ? Number($event) : null">
                    <option value="">{{ t('waitingList.noRole') }}</option>
                    <option v-for="r in roles" :key="r.id" :value="String(r.id)">{{ r.role }}</option>
                  </SelectInput>
                </div>
                <div class="space-y-1">
                  <label class="block text-sm font-medium">{{ t('waitingList.attendanceThreshold') }}</label>
                  <NumberInput v-model="editAttendanceThreshold" />
                </div>
              </div>
              <div class="flex justify-end gap-2">
                <SecondaryButton @click="cancelEditing">{{ t('common.cancel') }}</SecondaryButton>
                <PrimaryButton :disabled="saving || !editName.trim()" @click="saveEditing">
                  {{ saving ? t('common.loading') : t('common.save') }}
                </PrimaryButton>
              </div>
            </div>
          </template>
        </NeutralContainer>

        <!-- Waiting & Invited Section -->
        <NeutralContainer class="space-y-4">
          <div class="flex items-center justify-between flex-wrap gap-2">
            <SubHeader>{{ t('waitingList.sectionWaiting') }} ({{ waitingEntries.length }})</SubHeader>
            <div class="flex items-center gap-2 w-full sm:w-auto">
              <div class="relative flex-1 sm:flex-initial">
                <SecondaryButton :full-width="isMobile" @click="showFieldToggle = !showFieldToggle">
                  <font-awesome-icon :icon="['fas', 'table-columns']" class="mr-2" />
                  {{ t('waitingList.columns') }}
                </SecondaryButton>
                <div v-if="showFieldToggle" class="absolute right-0 top-full mt-1 z-20 rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark shadow-lg p-2 min-w-48">
                  <div v-for="field in fields" :key="field.id" class="flex items-center gap-2 px-2 py-1 rounded hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30 cursor-pointer" @click="toggleFieldVisibility(field.id)">
                    <font-awesome-icon :icon="['fas', visibleFieldIds.has(field.id) ? 'square-check' : 'square']" class="text-primary" />
                    <span class="text-sm">{{ field.name }}</span>
                  </div>
                  <div v-if="fields.length === 0" class="text-xs text-(--text-muted) px-2 py-1">{{ t('waitingList.noFields') }}</div>
                </div>
              </div>
              <PrimaryButton :full-width="isMobile" class="flex-1 sm:flex-initial" @click="openEntryModal">
                <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
                {{ t('waitingList.addEntry') }}
              </PrimaryButton>
            </div>
          </div>

          <div v-if="waitingEntries.length === 0" class="text-center text-(--text-muted) py-4">
            {{ t('waitingList.noEntries') }}
          </div>

          <!-- Desktop table -->
          <div v-if="!isMobile && waitingEntries.length > 0" class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                  <th class="py-2 px-2 font-medium">#</th>
                  <th class="py-2 px-2 font-medium">{{ t('waitingList.firstname') }}</th>
                  <th class="py-2 px-2 font-medium">{{ t('waitingList.lastname') }}</th>
                  <th class="py-2 px-2 font-medium">{{ t('waitingList.parentName') }}</th>
                  <th class="py-2 px-2 font-medium">{{ t('waitingList.email') }}</th>
                  <th v-for="vf in visibleFields" :key="vf.id" class="py-2 px-2 font-medium">{{ vf.name }}</th>
                  <th class="py-2 px-2 font-medium">{{ t('waitingList.status') }}</th>
                  <th class="py-2 px-2 font-medium text-right">{{ t('waitingList.score') }}</th>
                  <th class="py-2 px-2 font-medium text-right">{{ t('waitingList.actions') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(item, index) in waitingEntries"
                  :key="item.entry.id"
                  class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 hover:bg-bg-light-accent/30 dark:hover:bg-bg-dark-accent/30"
                >
                  <td class="py-2 px-2 text-(--text-muted)">{{ index + 1 }}</td>
                  <td class="py-2 px-2">
                    <button class="text-primary hover:underline" @click="navigateToEntry(item.entry.id)">
                      {{ item.entry.firstname }}
                    </button>
                  </td>
                  <td class="py-2 px-2">{{ item.entry.lastname }}</td>
                  <td class="py-2 px-2">{{ item.entry.parentName }}</td>
                  <td class="py-2 px-2">{{ item.entry.email }}</td>
                  <td v-for="vf in visibleFields" :key="vf.id" class="py-2 px-2 text-(--text-muted)">{{ getEntryFieldValue(item, vf.id) || '–' }}</td>
                  <td class="py-2 px-2">
                    <component :is="statusBadgeComponent(item.entry.status)">{{ t('waitingList.status_' + item.entry.status) }}</component>
                  </td>
                  <td class="py-2 px-2 text-right font-mono">{{ item.score }}</td>
                  <td class="py-2 px-2">
                    <div class="flex items-center justify-end gap-1">
                      <IconButton
                        v-if="item.entry.status === 'WAITING'"
                        icon="paper-plane"
                        :label="t('waitingList.invite')"
                        @click="doInviteEntry(item.entry.id)"
                      />
                      <IconButton
                        v-if="item.entry.status === 'INVITED'"
                        icon="play"
                        :label="t('waitingList.startTesting')"
                        @click="doMoveToTesting(item.entry.id)"
                      />
                      <EditButton @click="navigateToEntry(item.entry.id)" />
                      <DeleteButton @click="requestDeleteEntry(item)" />
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <!-- Mobile cards -->
          <div v-if="isMobile && waitingEntries.length > 0" class="space-y-3">
            <NeutralContainer
              v-for="(item, index) in waitingEntries"
              :key="item.entry.id"
              class="space-y-2"
            >
              <div class="flex items-center justify-between">
                <div>
                  <span class="text-xs text-(--text-muted) mr-2">#{{ index + 1 }}</span>
                  <button class="font-semibold text-primary hover:underline" @click="navigateToEntry(item.entry.id)">
                    {{ entryFullName(item) }}
                  </button>
                </div>
                <component :is="statusBadgeComponent(item.entry.status)">{{ t('waitingList.status_' + item.entry.status) }}</component>
              </div>
              <div class="text-sm text-(--text-muted)">
                {{ item.entry.parentName }} &middot; {{ item.entry.email }}
              </div>
              <div class="flex items-center justify-between text-sm">
                <span>{{ t('waitingList.score') }}: <span class="font-mono font-medium">{{ item.score }}</span></span>
                <div class="flex items-center gap-1">
                  <IconButton
                    v-if="item.entry.status === 'WAITING'"
                    icon="paper-plane"
                    :label="t('waitingList.invite')"
                    @click="doInviteEntry(item.entry.id)"
                  />
                  <IconButton
                    v-if="item.entry.status === 'INVITED'"
                    icon="play"
                    :label="t('waitingList.startTesting')"
                    @click="doMoveToTesting(item.entry.id)"
                  />
                  <EditButton @click="navigateToEntry(item.entry.id)" />
                  <DeleteButton @click="requestDeleteEntry(item)" />
                </div>
              </div>
            </NeutralContainer>
          </div>
        </NeutralContainer>

        <!-- Testing Section -->
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('waitingList.sectionTesting') }} ({{ testingEntries.length }})</SubHeader>

          <div v-if="testingEntries.length === 0" class="text-center text-(--text-muted) py-4">
            {{ t('waitingList.noTestingEntries') }}
          </div>

          <div v-if="testingEntries.length > 0" class="space-y-3">
            <NeutralContainer
              v-for="item in testingEntries"
              :key="item.entry.id"
              class="space-y-2"
              :class="{ 'ring-2 ring-success/40': item.entry.attendanceCount >= (list?.attendanceThreshold ?? 5) }"
            >
              <div class="flex items-center justify-between">
                <button class="font-semibold text-primary hover:underline" @click="navigateToEntry(item.entry.id)">
                  {{ entryFullName(item) }}
                </button>
                <PrimaryBadge>{{ t('waitingList.status_TESTING') }}</PrimaryBadge>
              </div>
              <div class="text-sm text-(--text-muted)">
                {{ item.entry.parentName }} &middot; {{ item.entry.email }}
              </div>
              <div class="flex items-center justify-between text-sm">
                <span>
                  {{ t('waitingList.attendanceCount') }}: <span class="font-mono font-medium" :class="{ 'text-success': item.entry.attendanceCount >= (list?.attendanceThreshold ?? 5) }">{{ item.entry.attendanceCount }} / {{ list?.attendanceThreshold ?? 5 }}</span>
                </span>
                <div class="flex items-center gap-1">
                  <SuccessButton @click="doMoveToJoined(item.entry.id)">
                    <font-awesome-icon :icon="['fas', 'check']" class="mr-1" />
                    {{ t('waitingList.join') }}
                  </SuccessButton>
                  <ErrorButton @click="doWithdrawEntry(item.entry.id)">
                    <font-awesome-icon :icon="['fas', 'xmark']" class="mr-1" />
                    {{ t('waitingList.withdraw') }}
                  </ErrorButton>
                </div>
              </div>
            </NeutralContainer>
          </div>
        </NeutralContainer>

        <!-- Joined / Withdrawn Section -->
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('waitingList.sectionFinished') }} ({{ finishedEntries.length }})</SubHeader>

          <div v-if="finishedEntries.length === 0" class="text-center text-(--text-muted) py-4">
            {{ t('waitingList.noFinishedEntries') }}
          </div>

          <div v-if="finishedEntries.length > 0" class="space-y-2">
            <div
              v-for="item in finishedEntries"
              :key="item.entry.id"
              class="flex items-center justify-between gap-2 px-3 py-2 rounded-lg bg-bg-light-accent/20 dark:bg-bg-dark-accent/20"
            >
              <div class="flex items-center gap-3">
                <button class="font-medium text-primary hover:underline" @click="navigateToEntry(item.entry.id)">
                  {{ entryFullName(item) }}
                </button>
                <component :is="statusBadgeComponent(item.entry.status)">{{ t('waitingList.status_' + item.entry.status) }}</component>
              </div>
              <span class="text-xs text-(--text-muted)">
                {{ item.entry.status === 'JOINED' ? formatDate(item.entry.joinedAt) : formatDate(item.entry.withdrawnAt) }}
              </span>
            </div>
          </div>
        </NeutralContainer>

        <!-- Invites Section -->
        <NeutralContainer class="space-y-4">
          <div class="flex items-center justify-between">
            <SubHeader>{{ t('waitingList.invites') }}</SubHeader>
            <PrimaryButton @click="openInviteModal">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
              {{ t('waitingList.createInvite') }}
            </PrimaryButton>
          </div>

          <div v-if="invites.length === 0" class="text-center text-(--text-muted) py-4">
            {{ t('waitingList.noInvites') }}
          </div>

          <div class="space-y-2">
            <div
              v-for="invite in invites"
              :key="invite.id"
              class="flex items-center justify-between gap-4 rounded-lg px-4 py-3 bg-bg-light-accent/30 dark:bg-bg-dark-accent/30"
            >
              <div class="flex-1 min-w-0 space-y-1">
                <div class="flex items-center gap-2 flex-wrap">
                  <code class="text-sm font-mono bg-bg-light-accent dark:bg-bg-dark-accent px-2 py-0.5 rounded select-all">{{ invite.code }}</code>
                  <IconButton icon="copy" :label="t('waitingList.copyLink')" @click="copyInviteLink(invite.code)" />
                </div>
                <div class="text-xs text-(--text-muted) flex flex-wrap gap-3">
                  <span>{{ t('waitingList.uses') }}: {{ invite.uses }}{{ invite.maxUses ? ' / ' + invite.maxUses : '' }}</span>
                  <span v-if="invite.expiresAt">{{ t('waitingList.expiresAt') }}: {{ formatDateTime(invite.expiresAt) }}</span>
                  <span>{{ t('waitingList.createdAt') }}: {{ formatDate(invite.createdAt) }}</span>
                </div>
              </div>
              <DeleteButton @click="deleteInvite(invite.id)" />
            </div>
          </div>
        </NeutralContainer>
      </template>

      <!-- Create invite modal -->
      <Modal v-model="showInviteModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('waitingList.createInvite') }}</SectionHeader>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('waitingList.maxUses') }}</label>
            <NumberInput v-model="inviteMaxUses" :placeholder="t('waitingList.maxUsesPlaceholder')" />
            <p class="text-xs text-(--text-muted)">{{ t('waitingList.maxUsesHint') }}</p>
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('waitingList.expiresAt') }}</label>
            <DateInput v-model="inviteExpiresAt" />
            <p class="text-xs text-(--text-muted)">{{ t('waitingList.expiresAtHint') }}</p>
          </div>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showInviteModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="creatingInvite" @click="createInvite">
              {{ creatingInvite ? t('common.loading') : t('waitingList.createInvite') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Create entry modal -->
      <Modal v-model="showEntryModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('waitingList.addEntry') }}</SectionHeader>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('waitingList.firstname') }}</label>
            <TextInput v-model="entryFirstname" :placeholder="t('waitingList.firstnamePlaceholder')" />
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('waitingList.lastname') }}</label>
            <TextInput v-model="entryLastname" :placeholder="t('waitingList.lastnamePlaceholder')" />
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('waitingList.parentName') }}</label>
            <TextInput v-model="entryParentName" :placeholder="t('waitingList.parentNamePlaceholder')" />
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('waitingList.email') }}</label>
            <TextInput v-model="entryEmail" :placeholder="t('waitingList.emailPlaceholder')" />
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('waitingList.notes') }}</label>
            <TextAreaInput v-model="entryNotes" :placeholder="t('waitingList.notesPlaceholder')" />
          </div>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showEntryModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="creatingEntry || !entryFirstname.trim() || !entryEmail.trim()" @click="createEntry">
              {{ creatingEntry ? t('common.loading') : t('waitingList.addEntry') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Delete list modal -->
      <Modal v-model="showDeleteModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('waitingList.deleteListTitle') }}</SectionHeader>
          <p class="text-sm">{{ t('waitingList.deleteListConfirm', { name: list?.name }) }}</p>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showDeleteModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton :disabled="deletingList" @click="confirmDeleteList">
              {{ deletingList ? t('common.loading') : t('waitingList.deleteList') }}
            </ErrorButton>
          </div>
        </div>
      </Modal>

      <!-- Delete entry modal -->
      <Modal v-model="showDeleteEntryModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('waitingList.deleteEntryTitle') }}</SectionHeader>
          <p class="text-sm">{{ t('waitingList.deleteEntryConfirm', { name: deleteEntryTarget ? entryFullName(deleteEntryTarget) : '' }) }}</p>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showDeleteEntryModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton :disabled="deletingEntry" @click="confirmDeleteEntry">
              {{ deletingEntry ? t('common.loading') : t('common.delete') }}
            </ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
