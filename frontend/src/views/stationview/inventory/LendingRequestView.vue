/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, computed, watch, nextTick} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ScanButton from '@/components/scanner/ScanButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type {LendingRequestDetail, EnrichedMessage, AvailableItemDetail, LendingStatusName} from '@/api/lending'
import {LendingStatus} from '@/api/lending'
import * as lending from '@/api/lending'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()
const {refresh: refreshSidebarCounts} = useSidebarCounts()

const detail = ref<LendingRequestDetail | null>(null)
const messages = ref<EnrichedMessage[]>([])
const loading = ref(true)
const error = ref('')
const newMessage = ref('')
const sending = ref(false)
const showDeclineModal = ref(false)
const declineReason = ref('')
const chatContainer = ref<HTMLElement | null>(null)

// Item assignment state
const availableItems = ref<AvailableItemDetail[]>([])
const selectedItemIds = ref<Set<number>>(new Set())
const loadingItems = ref(false)
const assigning = ref(false)

const requestId = Number(route.params.id)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await lending.getRequest(requestId)
    messages.value = await lending.getMessages(requestId)
    await loadAvailableItems()
    await nextTick()
    scrollToBottom()
  } catch {
    error.value = t('lending.loadError')
  } finally {
    loading.value = false
  }
}

const itemFilter = ref('')

const filteredAvailableItems = computed(() => {
  const query = itemFilter.value.trim().toLocaleUpperCase('en-US')
  if (!query) return availableItems.value
  return availableItems.value.filter(i =>
      (i.internalId ?? '').toLocaleUpperCase('en-US').includes(query)
      || (i.itemName ?? '').toLocaleUpperCase('en-US').includes(query),
  )
})

const groupedAvailableItems = computed(() => {
  const groups = new Map<string, AvailableItemDetail[]>()
  for (const item of filteredAvailableItems.value) {
    const key = item.inventoryName
    if (!groups.has(key)) groups.set(key, [])
    groups.get(key)!.push(item)
  }
  return groups
})

function onAssignmentScan(value: string) {
  itemFilter.value = value
}

const selectedCount = computed(() => selectedItemIds.value.size)

async function loadAvailableItems() {
  if (!detail.value || detail.value.request.request.status !== LendingStatus.APPROVED || !detail.value.request.isOwner) return
  loadingItems.value = true
  try {
    availableItems.value = await lending.getAvailableItems(requestId)
    // Auto-select preselected items, or first N if none preselected
    const preselected = availableItems.value.filter(i => i.preselected)
    selectedItemIds.value = new Set()
    if (preselected.length > 0) {
      for (const item of preselected) {
        selectedItemIds.value.add(item.itemId)
      }
    } else {
      // Auto-select items based on requested quantities
      const neededByRequestItem = new Map<number, number>()
      for (const enriched of detail.value.items) {
        neededByRequestItem.set(enriched.item.id, enriched.item.quantity)
      }
      for (const item of availableItems.value) {
        const needed = neededByRequestItem.get(item.requestItemId) || 0
        const alreadySelected = availableItems.value.filter(
            a => a.requestItemId === item.requestItemId && selectedItemIds.value.has(a.itemId),
        ).length
        if (alreadySelected < needed) {
          selectedItemIds.value.add(item.itemId)
        }
      }
    }
  } catch { /* ignore */ } finally {
    loadingItems.value = false
  }
}

function toggleItem(itemId: number) {
  const s = new Set(selectedItemIds.value)
  if (s.has(itemId)) {
    s.delete(itemId)
  } else {
    s.add(itemId)
  }
  selectedItemIds.value = s
}

async function handleAssignAndLend() {
  assigning.value = true
  try {
    const items = availableItems.value
        .filter(i => selectedItemIds.value.has(i.itemId))
        .map(i => ({requestItemId: i.requestItemId, itemId: i.itemId}))
    await lending.assignItems(requestId, items)
    await lending.markLent(requestId)
    await loadData()
    refreshSidebarCounts()
  } catch { /* ignore */ } finally {
    assigning.value = false
  }
}

function scrollToBottom() {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (v) => {
  if (v) loadData()
})

async function handleSendMessage() {
  if (!newMessage.value.trim()) return
  sending.value = true
  try {
    await lending.sendMessage(requestId, newMessage.value.trim())
    newMessage.value = ''
    messages.value = await lending.getMessages(requestId)
    await nextTick()
    scrollToBottom()
  } catch { /* ignore */ } finally {
    sending.value = false
  }
}

async function handleApprove() {
  try {
    await lending.approveRequest(requestId)
    await loadData()
    refreshSidebarCounts()
  } catch { /* ignore */ }
}

async function handleDecline() {
  try {
    await lending.declineRequest(requestId, declineReason.value)
    showDeclineModal.value = false
    declineReason.value = ''
    await loadData()
    refreshSidebarCounts()
  } catch { /* ignore */ }
}

async function handleMarkReturned() {
  try {
    await lending.markReturned(requestId)
    await loadData()
    refreshSidebarCounts()
  } catch { /* ignore */ }
}

async function handleClose() {
  try {
    await lending.closeRequest(requestId)
    await loadData()
    refreshSidebarCounts()
  } catch { /* ignore */ }
}

function statusBadge(status: LendingStatusName) {
  switch (status) {
    case LendingStatus.APPROVED:
    case LendingStatus.LENT:
      return 'success'
    case LendingStatus.DECLINED:
      return 'error'
    case LendingStatus.REQUESTED:
    case LendingStatus.RETURNED:
      return 'secondary'
    case LendingStatus.CLOSED:
      return 'info'
    default:
      return 'secondary'
  }
}

function formatDate(d: string | null): string {
  if (!d) return '-'
  return new Date(d).toLocaleDateString('de-DE')
}

function formatTime(d: string): string {
  return new Date(d).toLocaleString('de-DE', {dateStyle: 'short', timeStyle: 'short'})
}
</script>

<template>
  <ViewContent>
    <SecondaryButton :icon="['fas', 'chevron-left']" class="mb-4" @click="router.push({name: 'inventory-lending'})">
      {{ t('lending.backToList') }}
    </SecondaryButton>

    <Spinner v-if="loading"/>
    <Alert v-else-if="error" variant="error">{{ error }}</Alert>

    <template v-else-if="detail">
      <!-- Header -->
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 mb-4">
        <SectionHeader>
          {{ detail.request.isOwner ? detail.request.requestingStationName : detail.request.owningStationName }}
        </SectionHeader>
        <div class="flex items-center gap-2">
          <SuccessBadge v-if="statusBadge(detail.request.request.status) === 'success'">{{ t(`lending.status.${detail.request.request.status}`) }}</SuccessBadge>
          <ErrorBadge v-else-if="statusBadge(detail.request.request.status) === 'error'">{{ t(`lending.status.${detail.request.request.status}`) }}</ErrorBadge>
          <InfoBadge v-else-if="statusBadge(detail.request.request.status) === 'info'">{{ t(`lending.status.${detail.request.request.status}`) }}</InfoBadge>
          <SecondaryBadge v-else>{{ t(`lending.status.${detail.request.request.status}`) }}</SecondaryBadge>
          <ErrorBadge v-if="detail.request.overdue">{{ t('lending.overdue') }}</ErrorBadge>
        </div>
      </div>

      <!-- Request info -->
      <NeutralContainer class="mb-4">
        <div class="grid grid-cols-1 sm:grid-cols-2 gap-2 text-sm">
          <div><span class="font-medium">{{ t('lending.requestingStation') }}:</span> {{ detail.request.requestingStationName }}</div>
          <div><span class="font-medium">{{ t('lending.owningStation') }}:</span> {{ detail.request.owningStationName }}</div>
          <div><span class="font-medium">{{ t('lending.dateFrom') }}:</span> {{ formatDate(detail.request.request.requestedDateFrom) }}</div>
          <div><span class="font-medium">{{ t('lending.dateTo') }}:</span> {{ formatDate(detail.request.request.requestedDateTo) }}</div>
        </div>
      </NeutralContainer>

      <!-- Items -->
      <SubHeader class="mb-2">{{ t('lending.items') }}</SubHeader>
      <NeutralContainer v-if="detail.items.length > 0" class="mb-4">
        <table class="w-full text-sm">
          <thead>
          <tr class="border-b border-[var(--border)]">
            <th class="text-left py-1">{{ t('lending.itemType') }}</th>
            <th class="text-left py-1">{{ t('lending.quantity') }}</th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="enriched in detail.items" :key="enriched.item.id" class="border-b border-[var(--border)] last:border-0">
            <td class="py-1">{{ enriched.inventoryName }}</td>
            <td class="py-1">{{ enriched.item.quantity }}</td>
          </tr>
          </tbody>
        </table>
      </NeutralContainer>
      <MutedText tag="p" size="sm" v-else>{{ t('lending.noItems') }}</MutedText>

      <!-- Actions -->
      <div class="flex flex-wrap gap-2 mb-4">
        <template v-if="detail.request.isOwner">
          <SuccessButton :icon="['fas', 'check']" v-if="detail.request.request.status === LendingStatus.REQUESTED" @click="handleApprove">
            {{ t('lending.approve') }}
          </SuccessButton>
          <ErrorButton :icon="['fas', 'xmark']" v-if="detail.request.request.status === LendingStatus.REQUESTED" @click="showDeclineModal = true">
            {{ t('lending.decline') }}
          </ErrorButton>
        </template>
        <PrimaryButton v-if="detail.request.request.status === LendingStatus.LENT" @click="handleMarkReturned">
          {{ t('lending.markReturned') }}
        </PrimaryButton>
        <SecondaryButton v-if="detail.request.request.status === LendingStatus.RETURNED" @click="handleClose">
          {{ t('lending.close') }}
        </SecondaryButton>
      </div>

      <!-- Item assignment for APPROVED requests (owner only) -->
      <template v-if="detail.request.isOwner && detail.request.request.status === LendingStatus.APPROVED">
        <SubHeader class="mb-2">{{ t('lending.assignItems') }}</SubHeader>
        <Spinner v-if="loadingItems"/>
        <NeutralContainer v-else-if="availableItems.length > 0" class="mb-4">
          <p class="text-sm text-[var(--text-muted)] mb-3">{{ t('lending.selectItemsToLend') }}</p>
          <div class="flex items-center gap-2 mb-3">
            <TextInput v-model="itemFilter" :placeholder="t('inventory.manage.scanPlaceholder')" class="flex-1"/>
            <ScanButton @decoded="onAssignmentScan"/>
          </div>
          <template v-for="[groupName, items] in groupedAvailableItems" :key="groupName">
            <div class="font-medium text-sm mb-1">{{ groupName }}</div>
            <div class="flex flex-col gap-1 mb-3">
              <div v-for="item in items" :key="item.itemId"
                   class="flex items-center gap-2">
                <SelectionToggleButton
                    :selected="selectedItemIds.has(item.itemId)"
                    size="sm"
                    @toggle="toggleItem(item.itemId)">
                  <font-awesome-icon :icon="['fas', selectedItemIds.has(item.itemId) ? 'square-check' : 'square']" class="mr-1"/>
                  <span>{{ item.internalId }}</span>
                  <span class="mx-1">—</span>
                  <span>{{ item.itemName }}</span>
                  <span v-if="item.sizeName" class="text-[var(--text-muted)] ml-1">({{ item.sizeName }})</span>
                </SelectionToggleButton>
              </div>
            </div>
          </template>
          <div class="flex items-center justify-between mt-2">
            <span class="text-sm text-[var(--text-muted)]">{{ selectedCount }} {{ t('lending.itemsSelected') }}</span>
            <SuccessButton :icon="['fas', 'hand-holding']" :disabled="selectedCount === 0 || assigning" @click="handleAssignAndLend">
              {{ t('lending.lendOut') }}
            </SuccessButton>
          </div>
        </NeutralContainer>
        <MutedText tag="p" size="sm" v-else>{{ t('lending.noItems') }}</MutedText>
      </template>

      <!-- Chat -->
      <SubHeader class="mb-2">{{ t('lending.chat') }}</SubHeader>
      <NeutralContainer class="mb-2">
        <div ref="chatContainer" class="max-h-80 overflow-y-auto flex flex-col gap-2 p-2">
          <div v-if="messages.length === 0" class="text-sm text-[var(--text-muted)] text-center py-4">
            {{ t('lending.noMessages') }}
          </div>
          <div v-for="msg in messages" :key="msg.message.id"
               :class="msg.message.isSystem ? 'text-center text-xs text-[var(--text-muted)] italic py-1' : 'flex flex-col gap-0.5'">
            <template v-if="msg.message.isSystem">
              <span>{{ msg.message.message }} - {{ formatTime(msg.message.createdAt) }}</span>
            </template>
            <template v-else>
              <div class="flex items-center gap-2">
                <span v-if="msg.senderName" class="text-xs font-medium">{{ msg.senderName }} <span class="text-[var(--text-muted)]">({{ msg.senderStationName }})</span></span>
                <span v-else class="text-xs font-medium">{{ msg.senderStationName }}</span>
                <span class="text-xs text-[var(--text-muted)]">{{ formatTime(msg.message.createdAt) }}</span>
              </div>
              <div class="bg-[var(--bg)] rounded px-3 py-1.5 text-sm">{{ msg.message.message }}</div>
            </template>
          </div>
        </div>
      </NeutralContainer>

      <div v-if="detail.request.request.status !== LendingStatus.CLOSED && detail.request.request.status !== LendingStatus.DECLINED" class="flex gap-2">
        <TextInput v-model="newMessage" :placeholder="t('lending.messagePlaceholder')" class="flex-1" @keyup.enter="handleSendMessage"/>
        <PrimaryButton :disabled="sending || !newMessage.trim()" @click="handleSendMessage">
          <font-awesome-icon :icon="['fas', 'paper-plane']"/>
        </PrimaryButton>
      </div>
    </template>

    <!-- Decline modal -->
    <Modal v-model="showDeclineModal">
      <SectionHeader class="mb-4">{{ t('lending.declineTitle') }}</SectionHeader>
      <TextAreaInput v-model="declineReason" :placeholder="t('lending.declineReasonPlaceholder')" :rows="3"/>
      <div class="flex justify-end gap-2 mt-4">
        <SecondaryButton @click="showDeclineModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <ErrorButton @click="handleDecline">{{ t('lending.decline') }}</ErrorButton>
      </div>
    </Modal>
  </ViewContent>
</template>
