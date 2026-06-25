/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import InventoryItemCard from './InventoryItemCard.vue'
import {inventory, exchanges, stationMembers} from '@/api'
import type {ExchangeRequestEntry, InventoryItem, InventorySize, StationMember} from '@/api/types'
import {ExchangeStatus, StationPermission} from '@/api/types'
import type {MyInventoryItem} from '@/api/inventory'
import {useSession} from '@/composables/useSession'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import UnknownScanModal from '@/views/stationview/inventory/UnknownScanModal.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()

const {canManageInventory, hasPermission} = useSession()
const canAssign = computed(() =>
    hasPermission(StationPermission.INVENTORY_ASSIGN) || hasPermission(StationPermission.INVENTORY_EDIT))

const scanValue = ref('')
const scanError = ref('')
const scanSuccess = ref('')
const scanBusy = ref(false)
const unknownScanCode = ref<string | null>(null)

function flashScanError(msg: string) {
  scanError.value = msg
  setTimeout(() => (scanError.value = ''), 3500)
}

function flashScanSuccess(msg: string) {
  scanSuccess.value = msg
  setTimeout(() => (scanSuccess.value = ''), 2500)
}

async function assignToCurrentMember(item: InventoryItem | {id: number; name?: string}) {
  await inventory.assignItem(item.id, {
    memberId: memberId.value,
    memberName: member.value
        ? `${member.value.firstName ?? ''} ${member.value.lastName ?? ''}`.trim()
        : '',
  })
  flashScanSuccess(t('inventory.assign.assigned', {name: item.name ?? ''}))
  items.value = await inventory.memberItems(memberId.value)
}

async function handleScanAssign() {
  const term = scanValue.value.trim()
  if (!term) return
  scanValue.value = ''
  scanBusy.value = true
  try {
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
  } catch (e: any) {
    flashScanError(e?.response?.data?.message ?? t('inventory.assign.errors.failed'))
  } finally {
    scanBusy.value = false
  }
}

async function onUnknownScanCreated(item: InventoryItem) {
  unknownScanCode.value = null
  try {
    await assignToCurrentMember(item)
  } catch (e: any) {
    flashScanError(e?.response?.data?.message ?? t('inventory.assign.errors.failed'))
  }
}

const memberId = computed(() => Number(route.params.memberId))
const member = ref<StationMember | null>(null)
const items = ref<MyInventoryItem[]>([])
const activeExchanges = ref<ExchangeRequestEntry[]>([])
const loading = ref(true)
const error = ref('')

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
      inventoryName: invItems[0].inventoryName,
      items: invItems,
    })
  }
  return groups
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
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
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function itemExchange(itemId: number): ExchangeRequestEntry | undefined {
  return activeExchanges.value.find(e => e.itemId === itemId)
}

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

function goBack() {
  router.push({name: 'inventory-exchanges'})
}

// Exchange modal
const showExchangeModal = ref(false)
const exchangeItem = ref<MyInventoryItem | null>(null)
const exchangeNewSizeId = ref<string>('')
const exchangeReason = ref('')
const exchangeSizes = ref<InventorySize[]>([])
const exchangeSaving = ref(false)
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
  } catch { /* ignore */ }
}

async function submitExchange() {
  if (!exchangeItem.value || !exchangeReason.value.trim()) return
  exchangeSaving.value = true
  try {
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
  } catch {
    error.value = t('common.error')
  } finally {
    exchangeSaving.value = false
  }
}

onMounted(loadData)
watch(memberId, loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <SectionHeader>
          {{ member ? `${t('profile.inventory')} — ${memberDisplayName(member)}` : t('profile.inventory') }}
        </SectionHeader>
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
          {{ t('common.back') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer v-if="canAssign && !loading">
        <SubHeader class="mb-2">{{ t('inventory.memberInventory.scanTitle') }}</SubHeader>
        <p class="text-xs text-(--text-muted) mb-2">{{ t('inventory.memberInventory.scanHint') }}</p>
        <div class="flex gap-2">
          <TextInput
              v-model="scanValue"
              :placeholder="t('inventory.memberInventory.scanPlaceholder')"
              @keydown.enter="handleScanAssign"
              class="flex-1"
              :disabled="scanBusy"
          />
          <PrimaryButton :disabled="scanBusy" @click="handleScanAssign">
            <font-awesome-icon :icon="['fas', 'barcode']" class="mr-2" />
            {{ t('inventory.memberInventory.scanAction') }}
          </PrimaryButton>
        </div>
        <Alert v-if="scanError" variant="error" class="mt-2">{{ scanError }}</Alert>
        <Alert v-if="scanSuccess" variant="success" class="mt-2">{{ scanSuccess }}</Alert>
      </NeutralContainer>

      <template v-if="!loading">
        <EmptyState v-if="grouped.length === 0 && items.length === 0">{{ t('profile.noInventory') }}</EmptyState>

        <div v-else class="space-y-6">
          <div v-for="group in grouped" :key="group.inventoryId">
            <div class="flex items-center justify-between mb-2">
              <SubHeader>{{ group.inventoryName }}</SubHeader>
              <span class="text-sm text-(--text-muted)">{{ group.items.length }}</span>
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
                  :show-exchange-button="canManageInventory()"
                  @request-exchange="openExchangeModal"
              />
            </div>
          </div>
        </div>
      </template>

      <UnknownScanModal
          v-if="unknownScanCode"
          :scanned-code="unknownScanCode"
          context="member"
          @created="onUnknownScanCreated"
          @close="unknownScanCode = null"
      />

      <!-- Exchange modal -->
      <Modal v-model="showExchangeModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('memberDetail.requestExchange') }}</SectionHeader>
          <template v-if="exchangeSuccess">
            <Alert variant="success">{{ t('profile.exchangeCreated') }}</Alert>
            <div class="flex justify-end">
              <SecondaryButton @click="showExchangeModal = false">{{ t('common.close') }}</SecondaryButton>
            </div>
          </template>
          <template v-else>
            <p v-if="exchangeItem" class="text-sm">
              {{ exchangeItem.inventoryName }} — {{ exchangeItem.name }}
              <span class="text-(--text-muted)">{{ exchangeItem.sizeName ?? t('common.unisize') }}</span>
            </p>
            <div v-if="exchangeSizes.length > 0" class="space-y-1">
              <FieldLabel>{{ t('exchanges.newSize') }}</FieldLabel>
              <SelectInput v-model="exchangeNewSizeId">
                <option value="" disabled>{{ t('exchanges.selectNewSize') }}</option>
                <option v-for="size in exchangeSizes" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
              </SelectInput>
            </div>
            <div class="space-y-1">
              <FieldLabel>{{ t('exchanges.reason') }}</FieldLabel>
              <TextAreaInput v-model="exchangeReason" :placeholder="t('exchanges.reasonPlaceholder')" :rows="3" />
            </div>
            <div class="flex justify-end gap-2">
              <SecondaryButton @click="showExchangeModal = false">{{ t('common.cancel') }}</SecondaryButton>
              <PrimaryButton :disabled="exchangeSaving || !exchangeReason.trim() || (exchangeSizes.length > 0 && !exchangeNewSizeId)" @click="submitExchange">
                {{ exchangeSaving ? t('common.loading') : t('exchanges.submit') }}
              </PrimaryButton>
            </div>
          </template>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
