/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import MovementsPanel from '@/components/inventory/MovementsPanel.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import {inventory, managedMembers, exchanges} from '@/api'
import {stillMoving, type ExchangeRequestEntry} from '@/api/exchanges'
import type {InventorySize, MyInventoryItem, MyRequirement} from '@/api/inventory'
import type {ManagedMember} from '@/api/managedMembers'
import {useSession} from '@/composables/useSession'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import MemberTabSelector from './inventoryview/MemberTabSelector.vue'
import InventoryGroupList from './inventoryview/InventoryGroupList.vue'
import ExchangeModal from './inventoryview/ExchangeModal.vue'
import ReportLostModal from './inventoryview/ReportLostModal.vue'

const {t} = useI18n()
const {isGuardian, sessionInfo, loaded} = useSession()

const items = ref<MyInventoryItem[]>([])
const requirements = ref<MyRequirement[]>([])
const managed = ref<ManagedMember[]>([])
const selectedMemberId = ref<string>('')
const activeExchanges = ref<ExchangeRequestEntry[]>([])

const ownItems = ref<MyInventoryItem[]>([])
const ownRequirements = ref<MyRequirement[]>([])
const ownLoaded = ref(false)

const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)

const viewingMemberId = computed(() => {
  if (!selectedMemberId.value || selectedMemberId.value === String(currentMemberId.value)) return null
  return Number(selectedMemberId.value)
})

const showOwnTab = computed(() => {
  if (!isGuardian()) return true
  if (!ownLoaded.value) return true
  return ownItems.value.length > 0 || ownRequirements.value.length > 0
})

interface Tab {
  id: string
  label: string
  isOwn: boolean
}

const tabs = computed<Tab[]>(() => {
  const list: Tab[] = []
  if (showOwnTab.value) {
    list.push({id: String(currentMemberId.value), label: t('profile.myInventorySelf'), isOwn: true})
  }
  for (const m of managed.value) {
    list.push({id: String(m.id), label: m.name || m.email, isOwn: false})
  }
  return list
})

interface InventoryGroup {
  inventoryId: number
  inventoryName: string
  requiredQuantity: number
  items: MyInventoryItem[]
}

const grouped = computed((): InventoryGroup[] => {
  const groups: InventoryGroup[] = []
  const usedInventoryIds = new Set<number>()

  for (const req of requirements.value) {
    const groupItems = items.value.filter(i => i.inventoryId === req.inventoryId)
    groups.push({
      inventoryId: req.inventoryId,
      inventoryName: req.inventoryName,
      requiredQuantity: req.requiredQuantity,
      items: groupItems,
    })
    usedInventoryIds.add(req.inventoryId)
  }

  const extraItems = items.value.filter(i => !usedInventoryIds.has(i.inventoryId))
  if (extraItems.length > 0) {
    const extraByInv = new Map<number, MyInventoryItem[]>()
    for (const item of extraItems) {
      const list = extraByInv.get(item.inventoryId) ?? []
      list.push(item)
      extraByInv.set(item.inventoryId, list)
    }
    for (const [invId, invItems] of extraByInv) {
      groups.push({
        inventoryId: invId,
        inventoryName: invItems[0]?.inventoryName ?? '',
        requiredQuantity: 0,
        items: invItems,
      })
    }
  }

  return groups
})

async function loadOwnInventory() {
  const [myItems, myReqs] = await Promise.all([
    inventory.myItems(),
    inventory.myRequirements(),
  ])
  ownItems.value = myItems
  ownRequirements.value = myReqs
  ownLoaded.value = true
}

const {loading, error, reload} = useAsyncLoader(async () => {
  try {
    const allExch = await exchanges.listExchanges()
    activeExchanges.value = allExch.filter(e => stillMoving(e.status))
  } catch { activeExchanges.value = [] }
  const mid = viewingMemberId.value
  if (mid) {
    const [memberItems, memberReqs] = await Promise.all([
      managedMembers.getMemberInventory(mid),
      managedMembers.getMemberRequirements(mid),
    ])
    items.value = memberItems
    requirements.value = memberReqs
  } else {
    if (!ownLoaded.value) {
      await loadOwnInventory()
    }
    items.value = ownItems.value
    requirements.value = ownRequirements.value
  }
}, {autoLoad: false})

const initializing = ref(false)

async function init() {
  initializing.value = true
  try {
    try {
      await loadOwnInventory()
    } catch { void 0 }

    if (isGuardian()) {
      try {
        managed.value = await managedMembers.listManaged()
      } catch { void 0 }
    }

    const firstManaged = managed.value[0]
    if (showOwnTab.value) {
      selectedMemberId.value = String(currentMemberId.value)
    } else if (firstManaged) {
      selectedMemberId.value = String(firstManaged.id)
    } else {
      selectedMemberId.value = String(currentMemberId.value)
    }
  } finally {
    initializing.value = false
  }
  await reload()
}

onMounted(() => {
  if (loaded.value) init()
})

watch(loaded, (v) => {
  if (v) init()
})

watch(selectedMemberId, (newVal, oldVal) => {
  if (initializing.value) return
  if (newVal && newVal !== oldVal) reload()
})

const showExchangeModal = ref(false)
const exchangeItem = ref<MyInventoryItem | null>(null)
const exchangeReason = ref('')
const exchangeNewSizeId = ref<string>('')
const exchangeSizes = ref<InventorySize[]>([])
const exchangeSuccess = ref('')

async function openExchange(item: MyInventoryItem) {
  exchangeItem.value = item
  exchangeReason.value = ''
  exchangeNewSizeId.value = ''
  exchangeSizes.value = []
  exchangeSuccess.value = ''
  clearExchangeError()
  showExchangeModal.value = true
  try {
    exchangeSizes.value = await inventory.listSizes(item.inventoryId)
  } catch { void 0 }
}

function closeExchange() {
  showExchangeModal.value = false
  exchangeItem.value = null
}

const showLostModal = ref(false)
const lostItem = ref<MyInventoryItem | null>(null)
const lostNote = ref('')
const lostNoteRequired = ref(false)
const lostSuccess = ref('')

/**
 * Reporting a piece of gear missing, for the reader themselves or for somebody they act for.
 *
 * <p>The station decides whether a note is expected. Asking it before the form opens is what lets the
 * modal say so, rather than accepting the report and then refusing it.
 */
async function openLost(item: MyInventoryItem) {
  lostItem.value = item
  lostNote.value = ''
  lostSuccess.value = ''
  clearLostError()
  try {
    lostNoteRequired.value = (await inventory.getSettings()).lossNoteRequired
  } catch {
    lostNoteRequired.value = false
  }
  showLostModal.value = true
}

function closeLost() {
  showLostModal.value = false
  lostItem.value = null
}

const {
  running: submittingLost,
  error: lostError,
  run: submitLost,
  clearError: clearLostError,
} = useAsyncAction(async () => {
  if (!lostItem.value) return
  await inventory.markLost(lostItem.value.id, {note: lostNote.value.trim() || undefined})
  closeLost()
  ownLoaded.value = false
  await reload()
  lostSuccess.value = t('profile.lostReported')
})

const {
  running: submittingExchange,
  error: exchangeError,
  run: submitExchange,
  clearError: clearExchangeError,
} = useAsyncAction(async () => {
  if (!exchangeItem.value || !exchangeReason.value.trim()) return
  const created = await exchanges.createExchange({
    memberId: viewingMemberId.value ?? undefined,
    itemId: exchangeItem.value.id,
    inventoryId: exchangeItem.value.inventoryId,
    oldSizeId: exchangeItem.value.sizeId ?? undefined,
    newSizeId: exchangeNewSizeId.value ? Number(exchangeNewSizeId.value) : undefined,
    reason: exchangeReason.value.trim(),
  })
  activeExchanges.value = [...activeExchanges.value, created]
  exchangeSuccess.value = t('profile.exchangeCreated')
  closeExchange()
})
</script>

<template>
  <ViewContent
      :title="t('pages.profile-inventory.title')"
      :subtitle="t('pages.profile-inventory.subtitle')"
  >
    <div class="space-y-6">
      <MemberTabSelector :tabs="tabs" :selected-id="selectedMemberId" @select="selectedMemberId = $event"/>

      <MovementsPanel @changed="loadOwnInventory"/>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <InventoryGroupList
          v-if="!loading"
          :grouped="grouped"
          :items="items"
          :active-exchanges="activeExchanges"
          @request-exchange="openExchange"
          @report-lost="openLost"
      />

      <Alert v-if="exchangeSuccess" variant="success" class="mt-4">{{ exchangeSuccess }}</Alert>
      <Alert v-if="lostSuccess" variant="success" class="mt-4">{{ lostSuccess }}</Alert>

      <ReportLostModal
          v-model="showLostModal"
          v-model:note="lostNote"
          :item="lostItem"
          :note-required="lostNoteRequired"
          :submitting="submittingLost"
          :error="lostError"
          @cancel="closeLost"
          @submit="submitLost"
      />

      <ExchangeModal
          v-model="showExchangeModal"
          v-model:reason="exchangeReason"
          v-model:new-size-id="exchangeNewSizeId"
          :item="exchangeItem"
          :sizes="exchangeSizes"
          :submitting="submittingExchange"
          :error="exchangeError"
          @cancel="closeExchange"
          @submit="submitExchange"
      />
    </div>
  </ViewContent>
</template>
