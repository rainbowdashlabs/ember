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
import ErrorButton from '@/components/button/ErrorButton.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import OverviewSection from './detailview/OverviewSection.vue'
import WaitingSection from './detailview/WaitingSection.vue'
import TestingSection from './detailview/TestingSection.vue'
import FinishedSection from './detailview/FinishedSection.vue'
import InvitesSection from './detailview/InvitesSection.vue'
import type {
  WaitingList,
  WaitingListEntryWithScore,
  WaitingListField,
  WaitingListInvite,
  MemberGroup,
} from '@/api/types'
import { waitingList, memberGroups } from '@/api'
import { StationPermission } from '@/api/types'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useSidebarCounts } from '@/composables/useSidebarCounts'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { isMobile } = useBreakpoint()
const { refresh: refreshSidebarCounts } = useSidebarCounts()
const { hasPermission } = useSession()

const canManage = computed(() => hasPermission(StationPermission.WAITLIST_MANAGER))
const canEdit = computed(() => hasPermission(StationPermission.WAITLIST_EDIT))
const canAdd = computed(() => hasPermission(StationPermission.WAITLIST_ADD))
const canReadMembers = computed(() => hasPermission(StationPermission.MEMBER_READ))

const listId = computed(() => Number(route.params.id))

const list = ref<WaitingList | null>(null)
const entries = ref<WaitingListEntryWithScore[]>([])
const invites = ref<WaitingListInvite[]>([])
const fields = ref<WaitingListField[]>([])
const groups = ref<MemberGroup[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')

// Invite creation
const showInviteModal = ref(false)
const inviteMaxUses = ref<number | undefined>(undefined)
const inviteExpiresAt = ref('')
const creatingInvite = ref(false)


// Delete list
const showDeleteModal = ref(false)
const deletingList = ref(false)

// Delete entry
const showDeleteEntryModal = ref(false)
const deleteEntryTarget = ref<WaitingListEntryWithScore | null>(null)
const deletingEntry = ref(false)

// Computed entry groups
const sortedEntries = computed(() =>
  [...entries.value].sort((a, b) => b.score - a.score),
)
const waitingEntries = computed(() =>
  sortedEntries.value.filter(e => e.entry.status === 'WAITING' || e.entry.status === 'INVITED'),
)
const testingEntries = computed(() =>
  sortedEntries.value.filter(e => e.entry.status === 'TESTING'),
)
const finishedEntries = computed(() =>
  sortedEntries.value.filter(e => e.entry.status === 'JOINED' || e.entry.status === 'WITHDRAWN'),
)

const visibleFieldIds = computed(() => new Set(list.value?.visibleFields ?? []))
const showFieldToggle = ref(false)

function entryFullName(item: WaitingListEntryWithScore): string {
  const e = item.entry
  return e.lastname ? `${e.firstname} ${e.lastname}` : e.firstname
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [listData, entryData, inviteData, fieldData, groupData] = await Promise.all([
      waitingList.getById(listId.value),
      waitingList.listEntries(listId.value),
      waitingList.listInvites(listId.value),
      waitingList.listFields(listId.value),
      memberGroups.listGroups(),
    ])
    list.value = listData
    entries.value = entryData
    invites.value = inviteData
    fields.value = fieldData
    groups.value = groupData
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
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
function navigateToCreateEntry() {
  router.push({ name: 'waiting-list-create-entry', params: { id: listId.value } })
}

// State transitions
async function doInviteEntry(entryId: number) {
  error.value = ''
  try {
    await waitingList.inviteEntry(listId.value, entryId)
    entries.value = await waitingList.listEntries(listId.value)
    success.value = t('waitingList.entryInvited')
    setTimeout(() => { success.value = '' }, 3000)
    refreshSidebarCounts()
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
    refreshSidebarCounts()
  } catch {
    error.value = t('common.error')
  }
}

async function doMoveToJoined(entryId: number) {
  error.value = ''
  try {
    const result = await waitingList.moveToJoined(listId.value, entryId)
    refreshSidebarCounts()
    if (result.memberId && hasPermission(StationPermission.MEMBER_EDIT)) {
      router.push({ name: 'members-edit', params: { id: result.memberId } })
    } else {
      entries.value = await waitingList.listEntries(listId.value)
      success.value = t('waitingList.entryJoined')
      setTimeout(() => { success.value = '' }, 3000)
    }
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
    refreshSidebarCounts()
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
    refreshSidebarCounts()
  } catch {
    error.value = t('common.error')
  } finally {
    deletingEntry.value = false
  }
}

function navigateToEntry(entryId: number) {
  router.push({ name: 'waiting-list-entry', params: { id: listId.value, entryId } })
}

function navigateToMember(memberId: number) {
  router.push({ name: 'members-detail', params: { id: memberId } })
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

function handleListUpdated(updated: WaitingList) {
  list.value = updated
}

function showSuccessMessage(msg: string) {
  success.value = msg
  setTimeout(() => { success.value = '' }, 3000)
}

function showErrorMessage(msg: string) {
  error.value = msg
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
          {{ t('waitingList.back') }}
        </SecondaryButton>
        <div v-if="canManage" class="flex items-center gap-2 w-full sm:w-auto">
          <SecondaryButton :icon="['fas', 'sliders']" :full-width="isMobile" class="flex-1 sm:flex-initial" @click="navigateToFields">
            {{ t('waitingList.manageFields') }}
          </SecondaryButton>
          <ErrorButton :icon="['fas', 'trash']" :full-width="isMobile" class="flex-1 sm:flex-initial" @click="showDeleteModal = true">
            {{ t('waitingList.deleteList') }}
          </ErrorButton>
        </div>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading && list">
        <OverviewSection
          :list="list"
          :list-id="listId"
          :fields="fields"
          :groups="groups"
          :readonly="!canManage"
          @updated="handleListUpdated"
          @error="showErrorMessage"
          @success="showSuccessMessage"
        />

        <WaitingSection
          :entries="waitingEntries"
          :fields="fields"
          :visible-field-ids="visibleFieldIds"
          :is-mobile="isMobile"
          :show-field-toggle="showFieldToggle"
          :readonly="!canEdit"
          :can-add="canAdd"
          @invite="doInviteEntry"
          @move-to-testing="doMoveToTesting"
          @navigate-to-entry="navigateToEntry"
          @delete-entry="requestDeleteEntry"
          @toggle-field="toggleFieldVisibility"
          @toggle-field-menu="showFieldToggle = !showFieldToggle"
          @add-entry="navigateToCreateEntry"
        />

        <TestingSection
          :entries="testingEntries"
          :attendance-threshold="list.attendanceThreshold ?? 5"
          :readonly="!canEdit"
          @move-to-joined="doMoveToJoined"
          @withdraw="doWithdrawEntry"
          @navigate-to-entry="navigateToEntry"
        />

        <FinishedSection
          :entries="finishedEntries"
          :can-link-member="canReadMembers"
          @navigate-to-entry="navigateToEntry"
          @navigate-to-member="navigateToMember"
        />

        <InvitesSection
          v-if="canManage"
          :invites="invites"
          @create-invite="openInviteModal"
          @delete-invite="deleteInvite"
          @copy-link="copyInviteLink"
        />
      </template>

      <!-- Create invite modal -->
      <Modal v-model="showInviteModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('waitingList.createInvite') }}</SectionHeader>
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.maxUses') }}</FieldLabel>
            <NumberInput v-model="inviteMaxUses" :placeholder="t('waitingList.maxUsesPlaceholder')" />
            <p class="text-xs text-(--text-muted)">{{ t('waitingList.maxUsesHint') }}</p>
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.expiresAt') }}</FieldLabel>
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
