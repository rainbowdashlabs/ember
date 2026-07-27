/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import type {LendingRequestDetail, EnrichedMessage, AvailableItemDetail} from '@/api/lending'
import {LendingStatus} from '@/api/lending'
import * as lending from '@/api/lending'
import {useSession} from '@/composables/useSession'
import {useSidebarCounts} from '@/composables/useSidebarCounts'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import LendingRequestHeader from './lendingrequestview/LendingRequestHeader.vue'
import LendingRequestInfo from './lendingrequestview/LendingRequestInfo.vue'
import LendingItemsTable from './lendingrequestview/LendingItemsTable.vue'
import LendingActionsBar from './lendingrequestview/LendingActionsBar.vue'
import LendingItemAssignment from './lendingrequestview/LendingItemAssignment.vue'
import LendingChat from './lendingrequestview/LendingChat.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {loaded} = useSession()
const {refresh: refreshSidebarCounts} = useSidebarCounts()

const detail = ref<LendingRequestDetail | null>(null)
const messages = ref<EnrichedMessage[]>([])
const newMessage = ref('')
const showDeclineModal = ref(false)
const declineReason = ref('')

const availableItems = ref<AvailableItemDetail[]>([])
const selectedItemIds = ref<Set<number>>(new Set())
const loadingItems = ref(false)

const requestId = Number(route.params.id)

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
  detail.value = await lending.getRequest(requestId)
  messages.value = await lending.getMessages(requestId)
  await loadAvailableItems()
}, {autoLoad: false, errorMessageKey: 'lending.loadError'})

async function loadAvailableItems() {
  if (!detail.value || detail.value.request.request.status !== LendingStatus.APPROVED || !detail.value.request.isOwner) return
  loadingItems.value = true
  try {
    availableItems.value = await lending.getAvailableItems(requestId)
    const preselected = availableItems.value.filter(i => i.preselected)
    selectedItemIds.value = new Set()
    if (preselected.length > 0) {
      for (const item of preselected) {
        selectedItemIds.value.add(item.itemId)
      }
    } else {
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
  } catch { void 0 } finally {
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

const {running: assigning, run: handleAssignAndLend} = useAsyncAction(async () => {
  const items = availableItems.value
      .filter(i => selectedItemIds.value.has(i.itemId))
      .map(i => ({requestItemId: i.requestItemId, itemId: i.itemId}))
  await lending.assignItems(requestId, items)
  await lending.markLent(requestId)
  await loadData()
  refreshSidebarCounts()
})

watch(loaded, (v) => {
  if (v) loadData()
}, {immediate: true})

const {running: sending, run: handleSendMessage} = useAsyncAction(async () => {
  if (!newMessage.value.trim()) return
  await lending.sendMessage(requestId, newMessage.value.trim())
  newMessage.value = ''
  messages.value = await lending.getMessages(requestId)
})

async function handleApprove() {
  try {
    await lending.approveRequest(requestId)
    await loadData()
    refreshSidebarCounts()
  } catch { void 0 }
}

async function handleDecline() {
  try {
    await lending.declineRequest(requestId, declineReason.value)
    showDeclineModal.value = false
    declineReason.value = ''
    await loadData()
    refreshSidebarCounts()
  } catch { void 0 }
}

async function handleMarkReturned() {
  try {
    await lending.markReturned(requestId)
    await loadData()
    refreshSidebarCounts()
  } catch { void 0 }
}

async function handleClose() {
  try {
    await lending.closeRequest(requestId)
    await loadData()
    refreshSidebarCounts()
  } catch { void 0 }
}
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-lending-request.title')"
      :subtitle="t('pages.inventory-lending-request.subtitle')"
  >
    <SecondaryButton :icon="['fas', 'chevron-left']" class="mb-4" @click="router.push({name: 'inventory-lending'})">
      {{ t('lending.backToList') }}
    </SecondaryButton>

    <Spinner v-if="loading"/>
    <Alert v-else-if="error" variant="error">{{ error }}</Alert>

    <template v-else-if="detail">
      <LendingRequestHeader :detail="detail"/>
      <LendingRequestInfo :detail="detail"/>
      <LendingItemsTable :detail="detail"/>
      <LendingActionsBar
          :detail="detail"
          @approve="handleApprove"
          @decline="showDeclineModal = true"
          @mark-returned="handleMarkReturned"
          @close="handleClose"/>
      <LendingItemAssignment
          v-if="detail.request.isOwner && detail.request.request.status === LendingStatus.APPROVED"
          :loading="loadingItems"
          :available-items="availableItems"
          :selected-item-ids="selectedItemIds"
          :assigning="assigning"
          @toggle-item="toggleItem"
          @assign-and-lend="handleAssignAndLend"/>
      <LendingChat
          :detail="detail"
          :messages="messages"
          :sending="sending"
          v-model:new-message="newMessage"
          @send="handleSendMessage"/>
    </template>

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
