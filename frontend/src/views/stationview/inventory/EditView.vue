/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import DragList from '@/components/input/DragList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import type {InventoryDetail, InventoryItem, InventoryItemHistory, InventorySize, StationMember} from '@/api/types'
import {InventoryTypes, ItemSource} from '@/api/types'
import {inventory, stationMembers} from '@/api'
import {useStations} from '@/composables/useStations'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {currentStationId} = useStations()

const inventoryId = computed(() => Number(route.params.id))

const detail = ref<InventoryDetail | null>(null)
const items = ref<InventoryItem[]>([])
const members = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')

// Edit inventory settings
const editName = ref('')
const editType = ref<string>(InventoryTypes.INTERNAL)
const savingSettings = ref(false)

// Size modal
const showSizeModal = ref(false)
const editingSize = ref<InventorySize | null>(null)
const sizeLabel = ref('')
const sizeNote = ref('')
const sizeSaving = ref(false)

// Item modal
const showItemModal = ref(false)
const editingItem = ref<InventoryItem | null>(null)
const itemName = ref('')
const itemInternalId = ref('')
const itemInternalIdInput = ref<InstanceType<typeof import('@/components/input/text/TextInput.vue').default> | null>(null)
const itemSizeId = ref('')
const itemQuantity = ref(1)
const itemSaving = ref(false)

// Assign modal
const showAssignModal = ref(false)
const assignTarget = ref<InventoryItem | null>(null)
const assignMemberId = ref('')

// History modal
const showHistoryModal = ref(false)
const historyTarget = ref<InventoryItem | null>(null)
const historyEntries = ref<InventoryItemHistory[]>([])
const historyLoading = ref(false)

// Delete
const showDeleteModal = ref(false)
const deleteTarget = ref<InventoryItem | null>(null)

function getMemberName(memberId: number | null | undefined): string {
  if (!memberId) return ''
  const m = members.value.find(mem => mem.id === memberId)
  return m ? (m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`) : `#${memberId}`
}

function getSizeLabel(sizeId: number | null | undefined): string {
  if (!sizeId || !detail.value?.sizes) return ''
  return detail.value.sizes.find(s => s.id === sizeId)?.label ?? ''
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [inv, allItems, allMembers] = await Promise.all([
      inventory.getInventory(inventoryId.value),
      inventory.listItems(inventoryId.value),
      stationMembers.listMembers(currentStationId.value!),
    ])
    detail.value = inv
    items.value = allItems
    members.value = allMembers
    editName.value = inv.name ?? ''
    editType.value = inv.inventoryType ?? InventoryTypes.INTERNAL
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

// Inventory settings
async function saveSettings() {
  savingSettings.value = true
  error.value = ''
  success.value = ''
  try {
    await inventory.updateInventory(inventoryId.value, {
      name: editName.value,
      inventoryType: editType.value as import('@/api/types').InventoryTypeName,
      hasSizes: detail.value?.hasSizes ?? false,
    })
    success.value = t('inventory.edit.settingsSaved')
    detail.value = await inventory.getInventory(inventoryId.value)
  } catch {
    error.value = t('common.error')
  } finally {
    savingSettings.value = false
  }
}

// Sizes
function openAddSize() {
  editingSize.value = null
  sizeLabel.value = ''
  sizeNote.value = ''
  showSizeModal.value = true
}

function openEditSize(size: InventorySize) {
  editingSize.value = size
  sizeLabel.value = size.label ?? ''
  sizeNote.value = size.note ?? ''
  showSizeModal.value = true
}

async function saveSize() {
  sizeSaving.value = true
  error.value = ''
  try {
    if (editingSize.value) {
      await inventory.updateSize(inventoryId.value, editingSize.value.id, {
        label: sizeLabel.value,
        position: editingSize.value.position,
        note: sizeNote.value,
      })
    } else {
      await inventory.createSize(inventoryId.value, {
        label: sizeLabel.value,
        position: (detail.value?.sizes ?? []).length,
        note: sizeNote.value,
      })
    }
    showSizeModal.value = false
    detail.value = await inventory.getInventory(inventoryId.value)
  } catch {
    error.value = t('common.error')
  } finally {
    sizeSaving.value = false
  }
}

async function deleteSize(size: InventorySize) {
  try {
    await inventory.deleteSize(inventoryId.value, size.id)
    detail.value = await inventory.getInventory(inventoryId.value)
  } catch {
    error.value = t('common.error')
  }
}

async function onSizeReorder(fromIndex: number, toIndex: number) {
  const sizes = [...(detail.value?.sizes ?? [])]
  const [moved] = sizes.splice(fromIndex, 1)
  sizes.splice(toIndex, 0, moved)
  try {
    for (let i = 0; i < sizes.length; i++) {
      await inventory.updateSize(inventoryId.value, sizes[i].id, {
        label: sizes[i].label ?? '',
        position: i,
        note: sizes[i].note,
      })
    }
    detail.value = await inventory.getInventory(inventoryId.value)
  } catch {
    error.value = t('common.error')
  }
}

// Item CRUD
function openAddItem() {
  editingItem.value = null
  itemName.value = detail.value?.name ?? ''
  itemInternalId.value = ''
  itemSizeId.value = ''
  itemQuantity.value = 1
  showItemModal.value = true
  nextTick(() => itemInternalIdInput.value?.$el?.focus())
}

function openEditItem(item: InventoryItem) {
  editingItem.value = item
  itemName.value = item.name ?? ''
  itemInternalId.value = item.internalId ?? ''
  itemSizeId.value = item.sizeId != null ? String(item.sizeId) : ''
  showItemModal.value = true
  nextTick(() => itemInternalIdInput.value?.$el?.focus())
}

async function saveItem() {
  itemSaving.value = true
  error.value = ''
  try {
    const data = {
      name: itemName.value,
      internalId: itemInternalId.value || undefined,
      sizeId: itemSizeId.value ? Number(itemSizeId.value) : undefined,
      metadata: '{}',
    }
    if (editingItem.value) {
      await inventory.updateItem(editingItem.value.id, data)
    } else {
      const count = Math.max(1, Math.min(itemQuantity.value, 100))
      for (let i = 0; i < count; i++) {
        await inventory.createItem(inventoryId.value, data)
      }
    }
    showItemModal.value = false
    items.value = await inventory.listItems(inventoryId.value)
  } catch {
    error.value = t('common.error')
  } finally {
    itemSaving.value = false
  }
}

function requestDeleteItem(item: InventoryItem) {
  deleteTarget.value = item
  showDeleteModal.value = true
}

async function confirmDeleteItem() {
  if (!deleteTarget.value) return
  try {
    await inventory.deleteItem(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    items.value = await inventory.listItems(inventoryId.value)
  } catch {
    error.value = t('common.error')
  }
}

// Assignment
function openAssign(item: InventoryItem) {
  assignTarget.value = item
  assignMemberId.value = ''
  showAssignModal.value = true
}

async function submitAssign() {
  if (!assignTarget.value) return
  error.value = ''
  try {
    const memberId = assignMemberId.value ? Number(assignMemberId.value) : null
    const memberName = memberId ? getMemberName(memberId) : ''
    await inventory.assignItem(assignTarget.value.id, {memberId, memberName})
    showAssignModal.value = false
    items.value = await inventory.listItems(inventoryId.value)
  } catch {
    error.value = t('common.error')
  }
}

async function unassignItem(item: InventoryItem) {
  error.value = ''
  try {
    if (detail.value?.inventoryType === InventoryTypes.EXTERNAL) {
      // External items are removed when unassigned
      await inventory.deleteItem(item.id)
    } else {
      await inventory.assignItem(item.id, {memberId: null, memberName: ''})
    }
    items.value = await inventory.listItems(inventoryId.value)
  } catch {
    error.value = t('common.error')
  }
}

// External inventory: quick-assign creates item automatically
const showQuickAssignModal = ref(false)
const quickAssignMemberId = ref('')
const quickAssignSizeId = ref('')

function openQuickAssign() {
  quickAssignMemberId.value = ''
  quickAssignSizeId.value = ''
  showQuickAssignModal.value = true
}

async function submitQuickAssign() {
  if (!quickAssignMemberId.value) return
  error.value = ''
  try {
    const memberId = Number(quickAssignMemberId.value)
    const memberName = getMemberName(memberId)
    const sizeId = quickAssignSizeId.value ? Number(quickAssignSizeId.value) : undefined
    const item = await inventory.createItem(inventoryId.value, {
      name: detail.value?.name ?? '',
      sizeId,
      metadata: '{}',
      itemSource: ItemSource.EXTERNAL,
    })
    await inventory.assignItem(item.id, {memberId, memberName})
    showQuickAssignModal.value = false
    items.value = await inventory.listItems(inventoryId.value)
  } catch {
    error.value = t('common.error')
  }
}

// Lost/Found
async function doMarkLost(item: InventoryItem) {
  error.value = ''
  try {
    await inventory.markLost(item.id)
    items.value = await inventory.listItems(inventoryId.value)
  } catch {
    error.value = t('common.error')
  }
}

async function doMarkFound(item: InventoryItem) {
  error.value = ''
  try {
    await inventory.markFound(item.id)
    items.value = await inventory.listItems(inventoryId.value)
  } catch {
    error.value = t('common.error')
  }
}

// History
async function openHistory(item: InventoryItem) {
  historyTarget.value = item
  historyEntries.value = []
  historyLoading.value = true
  showHistoryModal.value = true
  try {
    historyEntries.value = await inventory.getItemHistory(item.id)
  } catch {
    error.value = t('common.error')
  } finally {
    historyLoading.value = false
  }
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '–'
  return new Date(iso).toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric'})
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SecondaryButton @click="router.push({ name: 'inventory-manage' })">
        <font-awesome-icon :icon="['fas', 'chevron-left']" class="mr-2"/>
        {{ t('inventory.edit.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading && detail">
        <!-- Inventory settings -->
        <NeutralContainer class="space-y-4">
          <SubHeader>{{ t('inventory.edit.settings') }}</SubHeader>
          <div class="grid gap-4 sm:grid-cols-2">
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('inventory.manage.name') }}</label>
              <TextInput v-model="editName"/>
            </div>
            <div class="space-y-1">
              <label class="block text-sm font-medium">{{ t('inventory.manage.typeLabel') }}</label>
              <SelectInput v-model="editType">
                <option :value="InventoryTypes.INTERNAL">{{ t('inventory.manage.type.INTERNAL') }}</option>
                <option :value="InventoryTypes.EXTERNAL">{{ t('inventory.manage.type.EXTERNAL') }}</option>
                <option :value="InventoryTypes.MIXED">{{ t('inventory.manage.type.MIXED') }}</option>
              </SelectInput>
            </div>
          </div>
          <PrimaryButton :disabled="savingSettings || !editName.trim()" class="text-sm" @click="saveSettings">
            {{ savingSettings ? t('common.loading') : t('common.save') }}
          </PrimaryButton>
        </NeutralContainer>

        <!-- Sizes -->
        <NeutralContainer v-if="detail.hasSizes" class="space-y-4">
          <div class="flex items-center justify-between">
            <SubHeader>{{ t('inventory.edit.sizesTitle') }}</SubHeader>
            <SecondaryButton class="text-sm" @click="openAddSize">
              <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
              {{ t('inventory.edit.addSize') }}
            </SecondaryButton>
          </div>

          <DragList :items="detail.sizes ?? []" :key-fn="(s) => s.id" @reorder="onSizeReorder">
            <template #default="{ item: size }">
              <div
                  class="flex items-center justify-between px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 cursor-grab active:cursor-grabbing">
                <div class="flex items-center gap-3">
                  <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-(--text-muted) h-3.5 w-3.5"/>
                  <div>
                    <span class="text-sm font-medium">{{ size.label }}</span>
                    <span v-if="size.note" class="ml-2 text-xs text-(--text-muted)">{{ size.note }}</span>
                  </div>
                </div>
                <div class="flex items-center gap-1">
                  <EditButton @click="openEditSize(size)"/>
                  <DeleteButton @click="deleteSize(size)"/>
                </div>
              </div>
            </template>
          </DragList>

          <div v-if="(detail.sizes ?? []).length === 0" class="text-center text-(--text-muted) py-4 text-sm">
            {{ t('inventory.edit.noSizes') }}
          </div>
        </NeutralContainer>

        <!-- Items -->
        <NeutralContainer class="space-y-4">
          <div class="flex items-center justify-between">
            <SubHeader>{{ t('inventory.edit.itemsTitle') }}</SubHeader>
            <div class="flex items-center gap-2">
              <PrimaryButton v-if="detail.inventoryType === InventoryTypes.EXTERNAL || detail.inventoryType === InventoryTypes.MIXED" class="text-sm" @click="openQuickAssign">
                <font-awesome-icon :icon="['fas', 'user-plus']" class="mr-1"/>
                {{ t('inventory.edit.quickAssign') }}
              </PrimaryButton>
              <PrimaryButton v-if="detail.inventoryType !== InventoryTypes.EXTERNAL" class="text-sm" @click="openAddItem">
                <font-awesome-icon :icon="['fas', 'plus']" class="mr-2"/>
                {{ t('inventory.edit.addItem') }}
              </PrimaryButton>
            </div>
          </div>

          <p v-if="detail.inventoryType === InventoryTypes.EXTERNAL" class="text-xs text-(--text-muted)">
            {{ t('inventory.edit.externalItemsHint') }}
          </p>

          <div v-if="items.length === 0" class="text-center text-(--text-muted) py-4 text-sm">
            {{ t('inventory.edit.noItems') }}
          </div>

          <div v-if="items.length > 0" class="overflow-x-auto">
            <table class="w-full text-sm">
              <thead>
              <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                <th class="px-3 py-2 font-medium">{{ t('inventory.edit.colName') }}</th>
                <th class="px-3 py-2 font-medium">{{ t('inventory.edit.colId') }}</th>
                <th v-if="detail.hasSizes" class="px-3 py-2 font-medium">{{ t('inventory.edit.colSize') }}</th>
                <th v-if="detail.inventoryType === InventoryTypes.MIXED" class="px-3 py-2 font-medium">{{ t('inventory.edit.colSource') }}</th>
                <th class="px-3 py-2 font-medium">{{ t('inventory.edit.colAssigned') }}</th>
                <th class="px-3 py-2"></th>
              </tr>
              </thead>
              <tbody>
              <tr v-for="item in items" :key="item.id"
                  :class="item.lostAt ? 'opacity-60' : ''"
                  class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                <td class="px-3 py-2.5 font-medium">
                  {{ item.name }}
                  <span v-if="item.lostAt" class="ml-2 text-xs text-error font-normal">{{ t('inventory.edit.lost') }} ({{
                      formatDate(item.lostAt)
                    }})</span>
                </td>
                <td class="px-3 py-2.5 text-(--text-muted)">{{ item.internalId || '–' }}</td>
                <td v-if="detail.hasSizes" class="px-3 py-2.5 text-(--text-muted)">{{
                    getSizeLabel(item.sizeId) || '–'
                  }}
                </td>
                <td v-if="detail.inventoryType === InventoryTypes.MIXED" class="px-3 py-2.5">
                  <PrimaryBadge v-if="item.itemSource === ItemSource.INTERNAL">{{ t('inventory.edit.sourceInternal') }}</PrimaryBadge>
                  <SecondaryBadge v-else-if="item.itemSource === ItemSource.EXTERNAL">{{ t('inventory.edit.sourceExternal') }}</SecondaryBadge>
                  <span v-else class="text-(--text-muted)">–</span>
                </td>
                <td class="px-3 py-2.5">
                  <span v-if="item.assignedTo" class="text-primary font-medium">{{
                      getMemberName(item.assignedTo)
                    }}</span>
                  <span v-else class="text-(--text-muted)">–</span>
                </td>
                <td class="px-3 py-2.5 text-right">
                  <div class="flex items-center justify-end gap-0.5">
                    <IconButton v-if="!item.lostAt" :icon="['fas', 'user']"
                                :label="item.assignedTo ? t('inventory.edit.reassign') : t('inventory.edit.assign')"
                                class="text-primary hover:bg-primary/15" @click="openAssign(item)"/>
                    <IconButton v-if="item.assignedTo && !item.lostAt" :icon="['fas', 'right-from-bracket']"
                                :label="t('inventory.edit.unassign')"
                                class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
                                @click="unassignItem(item)"/>
                    <IconButton v-if="!item.lostAt" :icon="['fas', 'triangle-exclamation']"
                                :label="t('inventory.edit.markLost')" class="text-error hover:bg-error/15"
                                @click="doMarkLost(item)"/>
                    <IconButton v-if="item.lostAt" :icon="['fas', 'check']" :label="t('inventory.edit.markFound')"
                                class="text-success hover:bg-success/15" @click="doMarkFound(item)"/>
                    <IconButton :icon="['fas', 'clock-rotate-left']" :label="t('inventory.edit.historyTitle')"
                                class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
                                @click="openHistory(item)"/>
                    <EditButton @click="openEditItem(item)"/>
                    <DeleteButton @click="requestDeleteItem(item)"/>
                  </div>
                </td>
              </tr>
              </tbody>
            </table>
          </div>
        </NeutralContainer>
      </template>

      <!-- Size modal -->
      <Modal v-model="showSizeModal">
        <div class="space-y-4">
          <SectionHeader>{{ editingSize ? t('inventory.edit.editSize') : t('inventory.edit.addSize') }}</SectionHeader>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.edit.sizeLabel') }}</label>
            <TextInput v-model="sizeLabel" :placeholder="t('inventory.manage.sizeLabel')"/>
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.edit.sizeNote') }}</label>
            <TextInput v-model="sizeNote" :placeholder="t('inventory.edit.sizeNotePlaceholder')"/>
          </div>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showSizeModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="sizeSaving || !sizeLabel.trim()" @click="saveSize">
              {{ sizeSaving ? t('common.loading') : t('common.save') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Item modal -->
      <Modal v-model="showItemModal">
        <form class="space-y-4" @submit.prevent="saveItem">
          <SectionHeader>{{ editingItem ? t('inventory.edit.editItem') : t('inventory.edit.addItem') }}</SectionHeader>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.edit.itemInternalId') }}</label>
            <TextInput ref="itemInternalIdInput" v-model="itemInternalId"
                       :placeholder="t('inventory.edit.itemInternalIdPlaceholder')"/>
          </div>
          <div class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.edit.itemName') }}</label>
            <TextInput v-model="itemName" :placeholder="t('inventory.edit.itemNamePlaceholder')"/>
          </div>
          <div v-if="detail?.hasSizes" class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.edit.itemSize') }}</label>
            <SelectInput v-model="itemSizeId">
              <option value="">–</option>
              <option v-for="size in detail?.sizes ?? []" :key="size.id" :value="String(size.id)">{{
                  size.label
                }}
              </option>
            </SelectInput>
          </div>
          <div v-if="!editingItem" class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.edit.itemQuantity') }}</label>
            <NumberInput v-model="itemQuantity" :max="100" :min="1"/>
            <p class="text-xs text-(--text-muted)">{{ t('inventory.edit.itemQuantityHint') }}</p>
          </div>
          <div class="flex justify-end gap-3">
            <SecondaryButton type="button" @click="showItemModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="itemSaving || !itemName.trim()" type="submit">
              {{ itemSaving ? t('common.loading') : t('common.save') }}
            </PrimaryButton>
          </div>
        </form>
      </Modal>

      <!-- Assign modal -->
      <Modal v-model="showAssignModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('inventory.edit.assignTitle') }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">{{ t('inventory.edit.assignHint', {name: assignTarget?.name}) }}</p>
          <SelectInput v-model="assignMemberId">
            <option disabled value="">{{ t('inventory.edit.selectMember') }}</option>
            <option v-for="m in members" :key="m.id" :value="String(m.id)">{{ m.name || m.email }}</option>
          </SelectInput>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showAssignModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="!assignMemberId" @click="submitAssign">{{
                t('inventory.edit.assignSubmit')
              }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- History modal -->
      <Modal v-model="showHistoryModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('inventory.edit.historyTitle') }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">{{ historyTarget?.name }}</p>
          <Spinner v-if="historyLoading" size="md"/>
          <div v-if="!historyLoading && historyEntries.length === 0" class="text-center text-(--text-muted) py-4">
            {{ t('inventory.edit.noHistory') }}
          </div>
          <div v-if="!historyLoading && historyEntries.length > 0" class="space-y-2 max-h-80 overflow-y-auto">
            <div v-for="entry in historyEntries" :key="entry.id"
                 class="flex items-center justify-between rounded-lg px-3 py-2 border border-bg-light-accent dark:border-bg-dark-accent">
              <span class="text-sm font-medium">{{ entry.memberName || getMemberName(entry.memberId) }}</span>
              <div class="text-xs text-(--text-muted) text-right">
                <div>{{ t('inventory.edit.givenOut') }}: {{ formatDate(entry.givenOut) }}</div>
                <div v-if="entry.returned">{{ t('inventory.edit.returned') }}: {{ formatDate(entry.returned) }}</div>
                <div v-else class="text-primary font-medium">{{ t('inventory.edit.currentlyAssigned') }}</div>
              </div>
            </div>
          </div>
          <div class="flex justify-end">
            <SecondaryButton @click="showHistoryModal = false">{{ t('common.cancel') }}</SecondaryButton>
          </div>
        </div>
      </Modal>

      <!-- Quick assign modal (external inventories) -->
      <Modal v-model="showQuickAssignModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('inventory.edit.quickAssign') }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">{{ t('inventory.edit.quickAssignHint') }}</p>
          <SelectInput v-model="quickAssignMemberId">
            <option disabled value="">{{ t('inventory.edit.selectMember') }}</option>
            <option v-for="m in members" :key="m.id" :value="String(m.id)">{{ m.name || m.email }}</option>
          </SelectInput>
          <div v-if="detail?.hasSizes" class="space-y-1">
            <label class="block text-sm font-medium">{{ t('inventory.edit.itemSize') }}</label>
            <SelectInput v-model="quickAssignSizeId">
              <option value="">–</option>
              <option v-for="size in detail?.sizes ?? []" :key="size.id" :value="String(size.id)">{{
                  size.label
                }}
              </option>
            </SelectInput>
          </div>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showQuickAssignModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="!quickAssignMemberId" @click="submitQuickAssign">
              {{ t('inventory.edit.assignSubmit') }}
            </PrimaryButton>
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
