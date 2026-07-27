/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import AssignItemModal from './AssignItemModal.vue'
import ProcurementModal from './ProcurementModal.vue'
import HistoryModal from './HistoryModal.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import ItemModals from '../editview/ItemModals.vue'
import { inventory, procurement } from '@/api'
import { InventoryTypes } from '@/api/types'
import type { InventoryDetail, InventoryItem, StationMember } from '@/api/types'
import { useModalTarget } from '@/composables/useModalTarget'

const props = defineProps<{
  detail: InventoryDetail | null
  memberMap: Map<number, StationMember>
  unassignedMembers: StationMember[]
}>()

const emit = defineEmits<{
  reload: []
  error: [msg: string]
}>()

const { t } = useI18n()

const itemModalsRef = ref<InstanceType<typeof ItemModals> | null>(null)

const assignMemberId = ref('')
const {isOpen: showAssignModal, target: assignItemId, open: openAssign} = useModalTarget<number>(() => {
  assignMemberId.value = ''
})

const procMemberId = ref('')
const procSizeId = ref('')
const procNotes = ref('')
const procCreated = ref(false)
const {isOpen: showProcurementModal, open: openProcurement} = useModalTarget<null>(() => {
  procMemberId.value = ''
  procSizeId.value = ''
  procNotes.value = ''
  procCreated.value = false
})

const {isOpen: showHistoryModal, target: historyTarget, open: openHistory} = useModalTarget<InventoryItem>()

const {isOpen: showDeleteModal, target: deleteTarget, open: openDelete} = useModalTarget<InventoryItem>()

function fail() { emit('error', t('common.error')) }

async function submitAssign() {
  if (!assignItemId.value || !assignMemberId.value) return
  const mid = Number(assignMemberId.value)
  const m = props.memberMap.get(mid)
  try {
    await inventory.assignItem(assignItemId.value, { memberId: mid, memberName: m?.name ?? '' })
    showAssignModal.value = false
    emit('reload')
  } catch { /* ignore */ }
}

async function submitProcurement() {
  if (!procMemberId.value || !props.detail) return
  try {
    await procurement.createProcurement({
      inventoryId: props.detail.id,
      memberId: Number(procMemberId.value),
      sizeId: procSizeId.value ? Number(procSizeId.value) : undefined,
      notes: procNotes.value || undefined,
    })
    procCreated.value = true
    emit('reload')
  } catch { /* ignore */ }
}

async function unassign(item: InventoryItem) {
  try {
    if (props.detail?.inventoryType === InventoryTypes.EXTERNAL) {
      await inventory.deleteItem(item.id)
    } else {
      await inventory.assignItem(item.id, { memberId: null, memberName: '' })
    }
    emit('reload')
  } catch { fail() }
}

async function markLost(item: InventoryItem) {
  try { await inventory.markLost(item.id); emit('reload') }
  catch { fail() }
}

async function markFound(item: InventoryItem) {
  try { await inventory.markFound(item.id); emit('reload') }
  catch { fail() }
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await inventory.deleteItem(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    emit('reload')
  } catch { fail() }
}

async function fulfillProcurement(id: number) {
  try { await procurement.fulfill(id); emit('reload') }
  catch { fail() }
}

function openQuickAssign() { itemModalsRef.value?.openQuickAssign() }
function openEdit(item: InventoryItem) { itemModalsRef.value?.openEdit(item) }
function openAdd() { itemModalsRef.value?.openAdd() }

defineExpose({
  openAssign,
  openProcurement,
  openEdit,
  unassign,
  markLost,
  markFound,
  openHistory,
  openDelete,
  fulfillProcurement,
  openQuickAssign,
  openAdd,
})
</script>

<template>
  <AssignItemModal
    v-model="showAssignModal"
    v-model:member-id="assignMemberId"
    :members="unassignedMembers"
    @submit="submitAssign"
  />

  <ProcurementModal
    v-model="showProcurementModal"
    v-model:member-id="procMemberId"
    v-model:size-id="procSizeId"
    v-model:notes="procNotes"
    :created="procCreated"
    :has-sizes="detail?.hasSizes ?? false"
    :sizes="detail?.sizes"
    :members="unassignedMembers"
    @submit="submitProcurement"
  />

  <ItemModals
    v-if="detail"
    ref="itemModalsRef"
    :detail="detail"
    :members="unassignedMembers"
    @items-changed="emit('reload')"
    @error="emit('error', $event)"
  />

  <HistoryModal
    v-model="showHistoryModal"
    :item="historyTarget"
    :member-map="memberMap"
  />

  <ConfirmDeleteModal
    v-model="showDeleteModal"
    :message="t('inventory.edit.deleteConfirm', { name: deleteTarget?.name })"
    @confirm="confirmDelete"
  />
</template>
