/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {nextTick, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import type {InventoryDetail, InventoryItem, InventoryItemHistory, StationMember} from '@/api/types'
import {ItemSource} from '@/api/types'
import {inventory} from '@/api'

const {t} = useI18n()

const props = defineProps<{
  detail: InventoryDetail
  members: StationMember[]
}>()

const emit = defineEmits<{
  itemsChanged: []
  error: [message: string]
}>()

function getMemberName(memberId: number | null | undefined): string {
  if (!memberId) return ''
  const m = props.members.find(mem => mem.id === memberId)
  return m ? (m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`) : `#${memberId}`
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return '\u2013'
  return new Date(iso).toLocaleDateString('de-DE', {day: '2-digit', month: '2-digit', year: 'numeric'})
}

// Item modal
const showItemModal = ref(false)
const editingItem = ref<InventoryItem | null>(null)
const itemName = ref('')
const itemInternalId = ref('')
const itemInternalIdInput = ref<InstanceType<typeof TextInput> | null>(null)
const itemSizeId = ref('')
const itemQuantity = ref(1)
const itemSaving = ref(false)

function openAdd() {
  editingItem.value = null
  itemName.value = props.detail.name ?? ''
  itemInternalId.value = ''
  itemSizeId.value = ''
  itemQuantity.value = 1
  showItemModal.value = true
  nextTick(() => itemInternalIdInput.value?.$el?.focus())
}

function openEdit(item: InventoryItem) {
  editingItem.value = item
  itemName.value = item.name ?? ''
  itemInternalId.value = item.internalId ?? ''
  itemSizeId.value = item.sizeId != null ? String(item.sizeId) : ''
  showItemModal.value = true
  nextTick(() => itemInternalIdInput.value?.$el?.focus())
}

async function saveItem() {
  itemSaving.value = true
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
        await inventory.createItem(props.detail.id, data)
      }
    }
    showItemModal.value = false
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  } finally {
    itemSaving.value = false
  }
}

// Assign modal
const showAssignModal = ref(false)
const assignTarget = ref<InventoryItem | null>(null)
const assignMemberId = ref('')

function openAssign(item: InventoryItem) {
  assignTarget.value = item
  assignMemberId.value = ''
  showAssignModal.value = true
}

async function submitAssign() {
  if (!assignTarget.value) return
  try {
    const memberId = assignMemberId.value ? Number(assignMemberId.value) : null
    const memberName = memberId ? getMemberName(memberId) : ''
    await inventory.assignItem(assignTarget.value.id, {memberId, memberName})
    showAssignModal.value = false
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
}

// Quick assign modal
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
  try {
    const memberId = Number(quickAssignMemberId.value)
    const memberName = getMemberName(memberId)
    const sizeId = quickAssignSizeId.value ? Number(quickAssignSizeId.value) : undefined
    const item = await inventory.createItem(props.detail.id, {
      name: props.detail.name ?? '',
      sizeId,
      metadata: '{}',
      itemSource: ItemSource.EXTERNAL,
    })
    await inventory.assignItem(item.id, {memberId, memberName})
    showQuickAssignModal.value = false
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
}

// History modal
const showHistoryModal = ref(false)
const historyTarget = ref<InventoryItem | null>(null)
const historyEntries = ref<InventoryItemHistory[]>([])
const historyLoading = ref(false)

async function openHistory(item: InventoryItem) {
  historyTarget.value = item
  historyEntries.value = []
  historyLoading.value = true
  showHistoryModal.value = true
  try {
    historyEntries.value = await inventory.getItemHistory(item.id)
  } catch {
    emit('error', t('common.error'))
  } finally {
    historyLoading.value = false
  }
}

// Delete
const showDeleteModal = ref(false)
const deleteTarget = ref<InventoryItem | null>(null)

function requestDelete(item: InventoryItem) {
  deleteTarget.value = item
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await inventory.deleteItem(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    emit('itemsChanged')
  } catch {
    emit('error', t('common.error'))
  }
}

defineExpose({openAdd, openEdit, openAssign, openQuickAssign, openHistory, requestDelete})
</script>

<template>
  <!-- Item modal -->
  <Modal v-model="showItemModal">
    <form class="space-y-4" @submit.prevent="saveItem">
      <SectionHeader>{{ editingItem ? t('inventory.edit.editItem') : t('inventory.edit.addItem') }}</SectionHeader>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemInternalId') }}</FieldLabel>
        <TextInput ref="itemInternalIdInput" v-model="itemInternalId"
                   :placeholder="t('inventory.edit.itemInternalIdPlaceholder')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemName') }}</FieldLabel>
        <TextInput v-model="itemName" :placeholder="t('inventory.edit.itemNamePlaceholder')"/>
      </div>
      <div v-if="detail.hasSizes" class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemSize') }}</FieldLabel>
        <SelectInput v-model="itemSizeId">
          <option value="">&#x2013;</option>
          <option v-for="size in detail.sizes ?? []" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>
      <div v-if="!editingItem" class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemQuantity') }}</FieldLabel>
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
        <PrimaryButton :disabled="!assignMemberId" @click="submitAssign">{{ t('inventory.edit.assignSubmit') }}</PrimaryButton>
      </div>
    </div>
  </Modal>

  <!-- History modal -->
  <Modal v-model="showHistoryModal">
    <div class="space-y-4">
      <SectionHeader>{{ t('inventory.edit.historyTitle') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ historyTarget?.name }}</p>
      <Spinner v-if="historyLoading" size="md"/>
      <EmptyState v-if="!historyLoading && historyEntries.length === 0" compact>{{ t('inventory.edit.noHistory') }}</EmptyState>
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

  <!-- Quick assign modal -->
  <Modal v-model="showQuickAssignModal">
    <div class="space-y-4">
      <SectionHeader>{{ t('inventory.edit.quickAssign') }}</SectionHeader>
      <p class="text-sm text-(--text-muted)">{{ t('inventory.edit.quickAssignHint') }}</p>
      <SelectInput v-model="quickAssignMemberId">
        <option disabled value="">{{ t('inventory.edit.selectMember') }}</option>
        <option v-for="m in members" :key="m.id" :value="String(m.id)">{{ m.name || m.email }}</option>
      </SelectInput>
      <div v-if="detail.hasSizes" class="space-y-1">
        <FieldLabel>{{ t('inventory.edit.itemSize') }}</FieldLabel>
        <SelectInput v-model="quickAssignSizeId">
          <option value="">&#x2013;</option>
          <option v-for="size in detail.sizes ?? []" :key="size.id" :value="String(size.id)">{{ size.label }}</option>
        </SelectInput>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="showQuickAssignModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!quickAssignMemberId" @click="submitQuickAssign">{{ t('inventory.edit.assignSubmit') }}</PrimaryButton>
      </div>
    </div>
  </Modal>

  <ConfirmDeleteModal
      v-model="showDeleteModal"
      :message="t('inventory.edit.deleteConfirm', { name: deleteTarget?.name })"
      @confirm="confirmDelete"
  />
</template>
