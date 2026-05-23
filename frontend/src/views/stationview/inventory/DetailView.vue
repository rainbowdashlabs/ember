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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import ItemsTable from './ItemsTable.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import type { InventoryDetail, InventoryItem, InventoryItemHistory, InventorySize, StationMember, ProcurementEntry } from '@/api/types'
import { InventoryTypes } from '@/api/types'
import { inventory, stationMembers, procurement } from '@/api'
import { useStations } from '@/composables/useStations'
import MemberName from '@/components/avatar/MemberName.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { currentStationId } = useStations()

const inventoryId = computed(() => Number(route.params.id))
const detail = ref<InventoryDetail | null>(null)
const items = ref<InventoryItem[]>([])
const memberMap = ref<Map<number, StationMember>>(new Map())
const openProcurement = ref<ProcurementEntry[]>([])
const loading = ref(true)
const error = ref('')

// Assign modal
const showAssignModal = ref(false)
const assignItemId = ref<number | null>(null)
const assignMemberId = ref('')

// Procurement modal
const showProcurementModal = ref(false)
const procMemberId = ref('')
const procSizeId = ref('')
const procNotes = ref('')
const procCreated = ref(false)

// Edit item modal
const showEditItemModal = ref(false)
const editingItem = ref<InventoryItem | null>(null)
const editItemName = ref('')
const editItemInternalId = ref('')
const editItemSizeId = ref('')
const editItemSaving = ref(false)

// History modal
const showHistoryModal = ref(false)
const historyTarget = ref<InventoryItem | null>(null)
const historyEntries = ref<InventoryItemHistory[]>([])
const historyLoading = ref(false)

// Delete
const showDeleteModal = ref(false)
const deleteTarget = ref<InventoryItem | null>(null)

const totalCount = computed(() => items.value.length)
const lostCount = computed(() => items.value.filter(i => i.lostAt).length)
const assignedCount = computed(() => items.value.filter(i => i.assignedTo && !i.lostAt).length)
const freeCount = computed(() => items.value.filter(i => !i.assignedTo && !i.lostAt).length)

const lostItems = computed(() => items.value.filter(i => i.lostAt))

const sizeDistribution = computed(() => {
  if (!detail.value?.hasSizes || !detail.value.sizes) return []
  const sizes = detail.value.sizes
  return sizes.map(size => {
    const sizeItems = items.value.filter(i => i.sizeId === size.id)
    return {
      size,
      total: sizeItems.length,
      assigned: sizeItems.filter(i => i.assignedTo && !i.lostAt).length,
      free: sizeItems.filter(i => !i.assignedTo && !i.lostAt).length,
      lost: sizeItems.filter(i => i.lostAt).length,
    }
  })
})

// Items without a size
const noSizeItems = computed(() => {
  if (!detail.value?.hasSizes) return []
  const nosizeItems = items.value.filter(i => !i.sizeId)
  if (nosizeItems.length === 0) return []
  return [{
    size: null as InventorySize | null,
    total: nosizeItems.length,
    assigned: nosizeItems.filter(i => i.assignedTo && !i.lostAt).length,
    free: nosizeItems.filter(i => !i.assignedTo && !i.lostAt).length,
    lost: nosizeItems.filter(i => i.lostAt).length,
  }]
})

const allSizeStats = computed(() => [...sizeDistribution.value, ...noSizeItems.value])

function ownerName(memberId: number | null | undefined): string {
  if (!memberId) return '-'
  const m = memberMap.value.get(memberId)
  return m ? (m.name || m.email || `#${m.id}`) : `#${memberId}`
}

function sizeName(sizeId: number | null | undefined): string {
  if (!sizeId || !detail.value?.sizes) return ''
  return detail.value.sizes.find(s => s.id === sizeId)?.label ?? ''
}

function formatDate(dateStr?: string | null): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('de-DE')
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [inv, allItems, members] = await Promise.all([
      inventory.getInventory(inventoryId.value),
      inventory.listItems(inventoryId.value),
      currentStationId.value ? stationMembers.listMembers() : Promise.resolve([]),
    ])
    detail.value = inv
    items.value = allItems
    const map = new Map<number, StationMember>()
    for (const m of members) map.set(m.id, m)
    memberMap.value = map
    try {
      const allProc = await procurement.listOpen()
      openProcurement.value = allProc.filter(p => p.inventoryId === inventoryId.value)
    } catch { openProcurement.value = [] }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

const freeItems = computed(() => items.value.filter(i => !i.assignedTo && !i.lostAt))
const unassignedMembers = computed(() => [...memberMap.value.values()])

function openAssign(itemId: number) {
  assignItemId.value = itemId
  assignMemberId.value = ''
  showAssignModal.value = true
}

async function submitAssign() {
  if (!assignItemId.value || !assignMemberId.value) return
  const mid = Number(assignMemberId.value)
  const m = memberMap.value.get(mid)
  try {
    await inventory.assignItem(assignItemId.value, { memberId: mid, memberName: m?.name ?? '' })
    showAssignModal.value = false
    await loadData()
  } catch { /* ignore */ }
}

function openProcurementModal() {
  procMemberId.value = ''
  procSizeId.value = ''
  procNotes.value = ''
  procCreated.value = false
  showProcurementModal.value = true
}

async function submitProcurement() {
  if (!procMemberId.value) return
  try {
    await procurement.createProcurement({
      inventoryId: inventoryId.value,
      memberId: Number(procMemberId.value),
      sizeId: procSizeId.value ? Number(procSizeId.value) : undefined,
      notes: procNotes.value || undefined,
    })
    procCreated.value = true
    await loadData()
  } catch { /* ignore */ }
}

// Item actions
function onAssign(item: InventoryItem) {
  openAssign(item.id)
}

async function onUnassign(item: InventoryItem) {
  error.value = ''
  try {
    if (detail.value?.inventoryType === InventoryTypes.EXTERNAL) {
      await inventory.deleteItem(item.id)
    } else {
      await inventory.assignItem(item.id, { memberId: null, memberName: '' })
    }
    await loadData()
  } catch { error.value = t('common.error') }
}

function onEditItem(item: InventoryItem) {
  editingItem.value = item
  editItemName.value = item.name ?? ''
  editItemInternalId.value = item.internalId ?? ''
  editItemSizeId.value = item.sizeId != null ? String(item.sizeId) : ''
  showEditItemModal.value = true
}

async function saveEditItem() {
  if (!editingItem.value) return
  editItemSaving.value = true
  error.value = ''
  try {
    await inventory.updateItem(editingItem.value.id, {
      name: editItemName.value,
      internalId: editItemInternalId.value || undefined,
      sizeId: editItemSizeId.value ? Number(editItemSizeId.value) : undefined,
    })
    showEditItemModal.value = false
    await loadData()
  } catch { error.value = t('common.error') }
  finally { editItemSaving.value = false }
}

async function onMarkLost(item: InventoryItem) {
  error.value = ''
  try {
    await inventory.markLost(item.id)
    await loadData()
  } catch { error.value = t('common.error') }
}

async function onMarkFound(item: InventoryItem) {
  error.value = ''
  try {
    await inventory.markFound(item.id)
    await loadData()
  } catch { error.value = t('common.error') }
}

async function onHistory(item: InventoryItem) {
  historyTarget.value = item
  historyEntries.value = []
  historyLoading.value = true
  showHistoryModal.value = true
  try {
    historyEntries.value = await inventory.getItemHistory(item.id)
  } catch { error.value = t('common.error') }
  finally { historyLoading.value = false }
}

function onDeleteItem(item: InventoryItem) {
  deleteTarget.value = item
  showDeleteModal.value = true
}

async function confirmDeleteItem() {
  if (!deleteTarget.value) return
  try {
    await inventory.deleteItem(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    await loadData()
  } catch { error.value = t('common.error') }
}

async function fulfillProcurement(id: number) {
  error.value = ''
  try {
    await procurement.fulfill(id)
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

function goBack() {
  router.push({ name: 'inventory-manage' })
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-2">
        <div>
          <SectionHeader>{{ detail?.name ?? '' }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">
            {{ t('inventory.manage.type.' + (detail?.inventoryType ?? InventoryTypes.INTERNAL)) }}
            <span v-if="detail?.hasSizes"> &middot; {{ t('inventory.manage.withSizes') }}</span>
          </p>
        </div>
        <SecondaryButton @click="goBack">
          <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-1" />
          {{ t('inventory.manage.back') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && detail">
        <!-- Summary -->
        <div class="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <NeutralContainer class="text-center">
            <div class="text-2xl font-bold">{{ totalCount }}</div>
            <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.total') }}</div>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <div class="text-2xl font-bold text-success">{{ freeCount }}</div>
            <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.free') }}</div>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <div class="text-2xl font-bold text-primary">{{ assignedCount }}</div>
            <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.assigned') }}</div>
          </NeutralContainer>
          <NeutralContainer class="text-center">
            <div class="text-2xl font-bold text-error">{{ lostCount }}</div>
            <div class="text-xs text-(--text-muted)">{{ t('inventory.detail.lost') }}</div>
          </NeutralContainer>
        </div>

        <!-- Size distribution -->
        <template v-if="detail.hasSizes && allSizeStats.length > 0">
          <SubHeader>{{ t('inventory.detail.bySize') }}</SubHeader>
          <NeutralContainer class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                  <th class="px-3 py-2 font-medium">{{ t('inventory.detail.size') }}</th>
                  <th class="px-3 py-2 font-medium text-center">{{ t('inventory.detail.total') }}</th>
                  <th class="px-3 py-2 font-medium text-center">{{ t('inventory.detail.free') }}</th>
                  <th class="px-3 py-2 font-medium text-center">{{ t('inventory.detail.assigned') }}</th>
                  <th class="px-3 py-2 font-medium text-center">{{ t('inventory.detail.lost') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in allSizeStats" :key="row.size?.id ?? 'none'" class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                  <td class="px-3 py-2.5 font-medium">{{ row.size?.label ?? t('inventory.detail.noSize') }}</td>
                  <td class="px-3 py-2.5 text-center">{{ row.total }}</td>
                  <td class="px-3 py-2.5 text-center text-success">{{ row.free }}</td>
                  <td class="px-3 py-2.5 text-center text-primary">{{ row.assigned }}</td>
                  <td class="px-3 py-2.5 text-center text-error">{{ row.lost }}</td>
                </tr>
              </tbody>
            </table>
          </NeutralContainer>
        </template>

        <!-- Procurement needs -->
        <template v-if="openProcurement.length > 0">
          <SubHeader>
            <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-2" />
            {{ t('inventory.detail.procurement') }} ({{ openProcurement.length }})
          </SubHeader>
          <NeutralContainer class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                  <th class="px-3 py-2 font-medium">{{ t('inventory.detail.owner') }}</th>
                  <th class="px-3 py-2 font-medium">{{ t('inventory.detail.size') }}</th>
                  <th class="px-3 py-2 font-medium">{{ t('inventory.detail.notes') }}</th>
                  <th class="px-3 py-2"></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="p in openProcurement" :key="p.id" class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                  <td class="px-3 py-2.5"><MemberName :name="p.memberName"/></td>
                  <td class="px-3 py-2.5"><SizeBadge>{{ p.sizeLabel || t('common.unisize') }}</SizeBadge></td>
                  <td class="px-3 py-2.5 text-(--text-muted)">{{ p.notes || '-' }}</td>
                  <td class="px-3 py-2.5 text-right">
                    <PrimaryButton @click="fulfillProcurement(p.id)">
                      <font-awesome-icon :icon="['fas', 'check']" class="mr-1" />
                      {{ t('procurement.markFulfilled') }}
                    </PrimaryButton>
                  </td>
                </tr>
              </tbody>
            </table>
          </NeutralContainer>
        </template>

        <!-- All items -->
        <template v-if="items.length > 0">
          <SubHeader>{{ t('inventory.edit.itemsTitle') }} ({{ items.length }})</SubHeader>
          <ItemsTable
            :items="items"
            :has-sizes="detail.hasSizes"
            :sizes="detail.sizes"
            :members="memberMap"
            :show-actions="true"
            :inventory-type="detail.inventoryType ?? InventoryTypes.INTERNAL"
            @assign="onAssign"
            @unassign="onUnassign"
            @edit="onEditItem"
            @mark-lost="onMarkLost"
            @mark-found="onMarkFound"
            @history="onHistory"
            @delete="onDeleteItem"
          />
        </template>

        <!-- Free items for assignment -->
        <template v-if="freeItems.length > 0">
          <SubHeader>{{ t('inventory.detail.freeItems') }}</SubHeader>
          <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
            <NeutralContainer v-for="item in freeItems" :key="item.id" class="flex items-center justify-between gap-2">
              <div>
                <div class="text-sm font-medium">
                  {{ item.name }}
                  <SizeBadge v-if="sizeName(item.sizeId)">{{ sizeName(item.sizeId) }}</SizeBadge>
                </div>
                <div v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</div>
              </div>
              <PrimaryButton @click="openAssign(item.id)">
                {{ t('inventory.detail.assign') }}
              </PrimaryButton>
            </NeutralContainer>
          </div>
        </template>

        <!-- Actions -->
        <div class="flex gap-2">
          <PrimaryButton @click="openProcurementModal">
            <font-awesome-icon :icon="['fas', 'folder-plus']" class="mr-1" />
            {{ t('inventory.detail.createProcurement') }}
          </PrimaryButton>
        </div>

        <!-- Lost items -->
        <template v-if="lostItems.length > 0">
          <SubHeader>{{ t('inventory.detail.lostItems') }}</SubHeader>
          <NeutralContainer class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
                <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                  <th class="px-3 py-2 font-medium">{{ t('inventory.detail.item') }}</th>
                  <th class="px-3 py-2 font-medium">{{ t('inventory.detail.owner') }}</th>
                  <th class="px-3 py-2 font-medium">{{ t('inventory.detail.lostSince') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in lostItems" :key="item.id" class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                  <td class="px-3 py-2.5">
                    <div class="font-medium">
                      {{ item.name }}
                      <SizeBadge v-if="sizeName(item.sizeId)" lost>{{ sizeName(item.sizeId) }}</SizeBadge>
                    </div>
                    <div v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</div>
                  </td>
                  <td class="px-3 py-2.5"><MemberName :name="ownerName(item.assignedTo)"/></td>
                  <td class="px-3 py-2.5">
                    <ErrorBadge>{{ formatDate(item.lostAt) }}</ErrorBadge>
                  </td>
                </tr>
              </tbody>
            </table>
          </NeutralContainer>
        </template>
      </template>

      <!-- Assign modal -->
      <Modal v-model="showAssignModal">
        <div class="space-y-3">
          <SectionHeader>{{ t('inventory.detail.assign') }}</SectionHeader>
          <SelectInput v-model="assignMemberId">
            <option value="" disabled>{{ t('inventory.detail.selectMember') }}</option>
            <option v-for="m in unassignedMembers" :key="m.id" :value="String(m.id)">
              {{ m.name || m.email || `#${m.id}` }}
            </option>
          </SelectInput>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showAssignModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="!assignMemberId" @click="submitAssign">{{ t('inventory.detail.assign') }}</PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Procurement modal -->
      <Modal v-model="showProcurementModal">
        <div class="space-y-3">
          <SectionHeader>{{ t('inventory.detail.createProcurement') }}</SectionHeader>
          <Alert v-if="procCreated" variant="success">{{ t('inventory.check.procurementCreated') }}</Alert>
          <template v-if="!procCreated">
            <SelectInput v-model="procMemberId">
              <option value="" disabled>{{ t('inventory.detail.selectMember') }}</option>
              <option v-for="m in unassignedMembers" :key="m.id" :value="String(m.id)">
                {{ m.name || m.email || `#${m.id}` }}
              </option>
            </SelectInput>
            <SelectInput v-if="detail?.hasSizes" v-model="procSizeId">
              <option value="">{{ t('inventory.detail.anySize') }}</option>
              <option v-for="s in detail.sizes" :key="s.id" :value="String(s.id)">{{ s.label }}</option>
            </SelectInput>
            <TextAreaInput v-model="procNotes" :placeholder="t('inventory.detail.procurementNotes')" :rows="2" />
          </template>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showProcurementModal = false">{{ t('common.close') }}</SecondaryButton>
            <PrimaryButton v-if="!procCreated" :disabled="!procMemberId" @click="submitProcurement">{{ t('common.save') }}</PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Edit item modal -->
      <Modal v-model="showEditItemModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('inventory.edit.editItem') }}</SectionHeader>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.edit.itemName') }}</label>
            <TextInput v-model="editItemName" :placeholder="t('inventory.edit.itemNamePlaceholder')" />
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.edit.itemInternalId') }}</label>
            <TextInput v-model="editItemInternalId" :placeholder="t('inventory.edit.itemInternalIdPlaceholder')" />
          </div>
          <div v-if="detail?.hasSizes" class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.edit.itemSize') }}</label>
            <SelectInput v-model="editItemSizeId">
              <option value="">–</option>
              <option v-for="size in detail?.sizes ?? []" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
            </SelectInput>
          </div>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showEditItemModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="editItemSaving || !editItemName.trim()" @click="saveEditItem">
              {{ editItemSaving ? t('common.loading') : t('common.save') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- History modal -->
      <Modal v-model="showHistoryModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('inventory.edit.historyTitle') }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">{{ historyTarget?.name }}</p>
          <Spinner v-if="historyLoading" size="md" />
          <div v-if="!historyLoading && historyEntries.length === 0" class="text-center text-(--text-muted) py-4">
            {{ t('inventory.edit.noHistory') }}
          </div>
          <div v-if="!historyLoading && historyEntries.length > 0" class="space-y-2 max-h-80 overflow-y-auto">
            <div v-for="entry in historyEntries" :key="entry.id"
                 class="flex items-center justify-between rounded-lg px-3 py-2 border border-bg-light-accent dark:border-bg-dark-accent">
              <span class="text-sm font-medium"><MemberName :name="entry.memberName || ownerName(entry.memberId)"/></span>
              <div class="text-xs text-(--text-muted) text-right">
                <div>{{ t('inventory.edit.givenOut') }}: {{ formatDate(entry.givenOut) }}</div>
                <div v-if="entry.returned">{{ t('inventory.edit.returned') }}: {{ formatDate(entry.returned) }}</div>
                <div v-else class="text-primary font-medium">{{ t('inventory.edit.currentlyAssigned') }}</div>
              </div>
            </div>
          </div>
          <div class="flex justify-end">
            <SecondaryButton @click="showHistoryModal = false">{{ t('common.close') }}</SecondaryButton>
          </div>
        </div>
      </Modal>

      <ConfirmDeleteModal
        v-model="showDeleteModal"
        :message="t('inventory.edit.deleteConfirm', { name: deleteTarget?.name })"
        @confirm="confirmDeleteItem"
      />
    </div>
  </ViewContent>
</template>
