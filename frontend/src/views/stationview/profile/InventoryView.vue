/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import InventoryItemCard from '@/views/stationview/inventory/InventoryItemCard.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import {inventory, managedMembers, exchanges} from '@/api'
import type {ExchangeRequestEntry, InventorySize} from '@/api/types'
import {ExchangeStatus} from '@/api/types'
import type {MyInventoryItem, MyRequirement} from '@/api/inventory'
import type {ManagedMember} from '@/api/managedMembers'
import {useSession} from '@/composables/useSession'
import MutedText from '@/components/typography/MutedText.vue'

const {t} = useI18n()
const {isGuardian, sessionInfo, loaded} = useSession()

const items = ref<MyInventoryItem[]>([])
const requirements = ref<MyRequirement[]>([])
const managed = ref<ManagedMember[]>([])
// Selected tab id. '' is reserved for "own"; otherwise the managed member id as string.
const selectedMemberId = ref<string>('')
const activeExchanges = ref<ExchangeRequestEntry[]>([])
const loading = ref(true)
const error = ref('')

// Cache of own items — used both to render the "own" tab and to determine
// whether a guardian's own inventory tab should be shown at all.
const ownItems = ref<MyInventoryItem[]>([])
const ownRequirements = ref<MyRequirement[]>([])
const ownLoaded = ref(false)

const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)

const viewingMemberId = computed(() => {
  if (!selectedMemberId.value || selectedMemberId.value === String(currentMemberId.value)) return null
  return Number(selectedMemberId.value)
})

const viewingMemberName = computed(() => {
  if (!viewingMemberId.value) return null
  return managed.value.find(m => m.id === viewingMemberId.value)?.name ?? ''
})

// Whether the "own" inventory tab should be available.
// For guardians, hide the own tab if their own inventory is empty
// (no items and no requirements). For everyone else, always show it.
const showOwnTab = computed(() => {
  if (!isGuardian()) return true
  if (!ownLoaded.value) return true
  return ownItems.value.length > 0 || ownRequirements.value.length > 0
})

interface Tab {
  id: string // '' for own, otherwise member id as string
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
        inventoryName: invItems[0].inventoryName,
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

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    try {
      const allExch = await exchanges.listExchanges()
      activeExchanges.value = allExch.filter(e => e.status !== ExchangeStatus.DONE)
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
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

const initializing = ref(false)

async function init() {
  initializing.value = true
  try {
    // Always pre-load own inventory so we can decide whether to show the
    // own tab for guardians (it is hidden when empty).
    try {
      await loadOwnInventory()
    } catch { /* ignore — we'll surface a real error on the active tab */ }

    if (isGuardian()) {
      try {
        managed.value = await managedMembers.listManaged()
      } catch { /* ignore */ }
    }

    // Pick the initial tab: own when available, otherwise the first managed member.
    if (showOwnTab.value) {
      selectedMemberId.value = String(currentMemberId.value)
    } else if (managed.value.length > 0) {
      selectedMemberId.value = String(managed.value[0].id)
    } else {
      selectedMemberId.value = String(currentMemberId.value)
    }
  } finally {
    initializing.value = false
  }
  await loadData()
}

onMounted(() => {
  if (loaded.value) init()
})

watch(loaded, (v) => {
  if (v) init()
})

watch(selectedMemberId, (newVal, oldVal) => {
  if (initializing.value) return
  if (newVal && newVal !== oldVal) loadData()
})

function itemExchange(itemId: number): ExchangeRequestEntry | undefined {
  return activeExchanges.value.find(e => e.itemId === itemId)
}

// Exchange request
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
  showExchangeModal.value = true
  try {
    exchangeSizes.value = await inventory.listSizes(item.inventoryId)
  } catch { /* ignore */ }
}

function closeExchange() {
  showExchangeModal.value = false
  exchangeItem.value = null
}

async function submitExchange() {
  if (!exchangeItem.value || !exchangeReason.value.trim()) return
  try {
    const created = await exchanges.createExchange({
      memberId: viewingMemberId.value ?? undefined,
      itemId: exchangeItem.value.id,
      inventoryId: exchangeItem.value.inventoryId,
      oldSizeId: exchangeItem.value.sizeId ?? undefined,
      newSizeId: exchangeNewSizeId.value ? Number(exchangeNewSizeId.value) : undefined,
      reason: exchangeReason.value.trim(),
    })
    // Append the new request so InventoryItemCard.itemExchange() sees it
    // immediately and flips the item into its "exchange pending" state.
    activeExchanges.value = [...activeExchanges.value, created]
    exchangeSuccess.value = t('profile.exchangeCreated')
    closeExchange()
  } catch { /* ignore */ }
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>
        {{ viewingMemberName ? `${t('profile.inventory')} — ${viewingMemberName}` : t('profile.inventory') }}
      </SectionHeader>

      <div v-if="tabs.length > 1" class="flex flex-wrap gap-2">
        <SelectionToggleButton
            v-for="tab in tabs"
            :key="tab.id"
            :selected="selectedMemberId === tab.id"
            @toggle="selectedMemberId = tab.id"
        >
          <font-awesome-icon :icon="['fas', tab.isOwn ? 'user' : 'users']" class="mr-1"/>
          {{ tab.label }}
        </SelectionToggleButton>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <EmptyState v-if="grouped.length === 0 && items.length === 0">{{ t('profile.noInventory') }}</EmptyState>

        <div v-else class="space-y-6">
          <div v-for="group in grouped" :key="group.inventoryId">
            <div class="flex items-center justify-between mb-2">
              <SubHeader>{{ group.inventoryName }}</SubHeader>
              <span v-if="group.requiredQuantity > 0" class="text-sm text-(--text-muted)">
                {{ group.items.length }} / {{ group.requiredQuantity }}
                <span v-if="group.items.length < group.requiredQuantity" class="text-error">
                  ({{ group.requiredQuantity - group.items.length }} fehlt)
                </span>
              </span>
            </div>

            <MutedText tag="div" size="sm" class="py-2" v-if="group.items.length === 0">
              {{ t('profile.noInventory') }}
            </MutedText>

            <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
              <InventoryItemCard
                  v-for="item in group.items"
                  :key="item.id"
                  :item="item"
                  :exchange="itemExchange(item.id) ?? null"
                  :show-exchange-button="true"
                  @request-exchange="openExchange"
              />
            </div>
          </div>
        </div>
      </template>

      <Alert v-if="exchangeSuccess" variant="success" class="mt-4">{{ exchangeSuccess }}</Alert>

      <!-- Exchange request modal -->
      <Modal v-model="showExchangeModal">
        <div class="space-y-3">
          <SectionHeader>{{ t('profile.requestExchange') }}</SectionHeader>
          <p class="text-sm" v-if="exchangeItem">
            {{ exchangeItem.inventoryName }} — {{ exchangeItem.name }}
            <SizeBadge>{{ exchangeItem.sizeName ?? t('common.unisize') }}</SizeBadge>
          </p>
          <div v-if="exchangeSizes.length > 0" class="space-y-1">
            <FieldLabel>{{ t('exchanges.newSize') }}</FieldLabel>
            <SelectInput v-model="exchangeNewSizeId" class="w-full">
              <option value="" disabled>{{ t('exchanges.selectNewSize') }}</option>
              <option v-for="size in exchangeSizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
            </SelectInput>
          </div>
          <TextAreaInput v-model="exchangeReason" :placeholder="t('profile.exchangeReasonPlaceholder')" :rows="3" />
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="closeExchange">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="!exchangeReason.trim() || (exchangeSizes.length > 0 && !exchangeNewSizeId)" @click="submitExchange">
              {{ t('profile.submitExchange') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
