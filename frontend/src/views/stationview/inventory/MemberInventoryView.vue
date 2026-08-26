/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import {inventory, exchanges, stationMembers} from '@/api'
import {ExchangeStatus, type ExchangeRequestEntry} from '@/api/exchanges'
import type {InventoryItem, InventorySize, MyInventoryItem} from '@/api/inventory'
import {StationPermission, type StationMember} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useFlashMessage} from '@/composables/useFlashMessage'
import {normaliseScannedPayload} from '@/components/scanner/useBarcodeScanner'
import UnknownScanModal from '@/views/stationview/inventory/UnknownScanModal.vue'
import MemberInventoryHeader from './memberinventoryview/MemberInventoryHeader.vue'
import ReturnEverythingBar from './memberinventoryview/ReturnEverythingBar.vue'
import MemberInventoryScanPanel from './memberinventoryview/MemberInventoryScanPanel.vue'
import MemberInventoryGroups from './memberinventoryview/MemberInventoryGroups.vue'
import RequestExchangeModal from './memberinventoryview/RequestExchangeModal.vue'
import {apiErrorMessage} from '@/util/apiError'

const routes = useInventoryRoutes()

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const {canManageInventory, hasPermission} = useSession()
const canAssign = computed(() =>
    hasPermission(StationPermission.INVENTORY_ASSIGN) || hasPermission(StationPermission.INVENTORY_EDIT))

const scanValue = ref('')
const unknownScanCode = ref<string | null>(null)

const {message: scanError, flash: flashScanError} = useFlashMessage(3500)
const {message: scanSuccess, flash: flashScanSuccess} = useFlashMessage(2500)

async function assignToCurrentMember(item: InventoryItem | {id: number; name?: string}) {
  await inventory.assignItem(item.id, {
    memberId: memberId.value,
    memberName: member.value?.name ?? '',
  })
  flashScanSuccess(t('inventory.assign.assigned', {name: item.name ?? ''}))
  items.value = await inventory.memberItems(memberId.value)
}

async function onCameraScan(value: string) {
  if (scanBusy.value) return
  scanValue.value = normaliseScannedPayload(value)
  await handleScanAssign()
}

const {running: scanBusy, error: scanAssignError, run: runScanAssign} = useAsyncAction(async (term: string) => {
  const item = await inventory.findByInternalId(term)
  if (!item) {
    unknownScanCode.value = term
    return
  }
  if (item.assignedTo === memberId.value) {
    flashScanSuccess(t('inventory.memberInventory.alreadyHere', {name: item.name ?? ''}))
    return
  }
  await assignToCurrentMember(item)
}, {formatError: (e) => apiErrorMessage(e) ?? t('inventory.assign.errors.failed')})

async function handleScanAssign() {
  const term = scanValue.value.trim()
  if (!term) return
  scanValue.value = ''
  await runScanAssign(term)
  if (scanAssignError.value) flashScanError(scanAssignError.value)
}

async function onUnknownScanCreated(item: InventoryItem) {
  unknownScanCode.value = null
  try {
    await assignToCurrentMember(item)
  } catch (e) {
    flashScanError(apiErrorMessage(e) ?? t('inventory.assign.errors.failed'))
  }
}

const memberId = computed(() => Number(route.params.memberId))


const canManage = computed(() => hasPermission(StationPermission.INVENTORY_MANAGER))

const member = ref<StationMember | null>(null)
const items = ref<MyInventoryItem[]>([])
const activeExchanges = ref<ExchangeRequestEntry[]>([])

interface InventoryGroup {
  inventoryId: number
  inventoryName: string
  items: MyInventoryItem[]
}

const grouped = computed((): InventoryGroup[] => {
  const byInv = new Map<number, MyInventoryItem[]>()
  for (const item of items.value) {
    const list = byInv.get(item.inventoryId) ?? []
    list.push(item)
    byInv.set(item.inventoryId, list)
  }
  const groups: InventoryGroup[] = []
  for (const [invId, invItems] of byInv) {
    groups.push({
      inventoryId: invId,
      inventoryName: invItems[0]?.inventoryName ?? '',
      items: invItems,
    })
  }
  return groups
})

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
  const mid = memberId.value
  const [memberItems, allMembers] = await Promise.all([
    inventory.memberItems(mid),
    stationMembers.listMembers(),
  ])
  items.value = memberItems
  member.value = allMembers.find(m => m.id === mid) ?? null
  try {
    const allExch = await exchanges.listExchanges()
    activeExchanges.value = allExch.filter(e => e.memberId === mid && e.status !== ExchangeStatus.DONE)
  } catch { activeExchanges.value = [] }
})

function itemExchange(itemId: number): ExchangeRequestEntry | undefined {
  return activeExchanges.value.find(e => e.itemId === itemId)
}

function goBack() {
  router.push({name: routes.exchanges})
}

const showExchangeModal = ref(false)
const exchangeItem = ref<MyInventoryItem | null>(null)
const exchangeNewSizeId = ref<string>('')
const exchangeReason = ref('')
const exchangeSizes = ref<InventorySize[]>([])
const exchangeSuccess = ref(false)

async function openExchangeModal(item: MyInventoryItem) {
  exchangeItem.value = item
  exchangeReason.value = ''
  exchangeNewSizeId.value = ''
  exchangeSizes.value = []
  exchangeSuccess.value = false
  showExchangeModal.value = true
  try {
    exchangeSizes.value = await inventory.listSizes(item.inventoryId)
  } catch {
    exchangeSizes.value = []
  }
}

const {running: exchangeSaving, error: exchangeError, run: submitExchange} = useAsyncAction(async () => {
  if (!exchangeItem.value || !exchangeReason.value.trim()) return
  await exchanges.createExchange({
    memberId: memberId.value,
    itemId: exchangeItem.value.id,
    inventoryId: exchangeItem.value.inventoryId,
    oldSizeId: exchangeItem.value.sizeId ?? undefined,
    newSizeId: exchangeNewSizeId.value ? Number(exchangeNewSizeId.value) : undefined,
    reason: exchangeReason.value.trim(),
  })
  exchangeSuccess.value = true
  showExchangeModal.value = false
  await loadData()
}, {formatError: () => t('common.error')})

watch(memberId, loadData)
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-member.title')"
      :subtitle="t('pages.inventory-member.subtitle')"
  >
    <div class="space-y-6">
      <MemberInventoryHeader :member="member" @back="goBack" />

      <Alert v-if="error || exchangeError" variant="error">{{ error || exchangeError }}</Alert>
      <ReturnEverythingBar v-if="canManage && items.length > 0" :member-id="memberId" @done="loadData"/>

      <AsyncSection :loading="loading">
        <MemberInventoryScanPanel
            v-if="canAssign"
            v-model:scan-value="scanValue"
            :scan-busy="scanBusy"
            :scan-error="scanError"
            :scan-success="scanSuccess"
            @submit="handleScanAssign"
            @decoded="onCameraScan"
        />

        <MemberInventoryGroups
            :groups="grouped"
            :items="items"
            :item-exchange="itemExchange"
            :show-exchange-button="canManageInventory()"
            @request-exchange="openExchangeModal"
        />
      </AsyncSection>

      <UnknownScanModal
          v-if="unknownScanCode"
          :scanned-code="unknownScanCode"
          context="member"
          @created="onUnknownScanCreated"
          @close="unknownScanCode = null"
      />

      <RequestExchangeModal
          v-model="showExchangeModal"
          :item="exchangeItem"
          :sizes="exchangeSizes"
          :reason="exchangeReason"
          :new-size-id="exchangeNewSizeId"
          :saving="exchangeSaving"
          :success="exchangeSuccess"
          @update:reason="exchangeReason = $event"
          @update:new-size-id="exchangeNewSizeId = $event"
          @submit="submitExchange"
      />
    </div>
  </ViewContent>
</template>
