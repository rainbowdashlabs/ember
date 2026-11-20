/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import AssignItemModal from './AssignItemModal.vue'
import ProcurementModal from './ProcurementModal.vue'
import HistoryModal from './HistoryModal.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import ItemModals from '../editview/ItemModals.vue'
import ItemReturnAndLossModals from '../editview/ItemReturnAndLossModals.vue'
import { inventory, procurement } from '@/api'
import {InventoryType, type InventoryDetail, type InventoryItem} from '@/api/generated/schema'
import type { MemberLike } from '@/components/input/select/memberOption'
import { useModalTarget } from '@/composables/useModalTarget'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import {describeFailure, type Failure} from '@/util/failure'

const props = defineProps<{
  detail: InventoryDetail | null
  memberMap: Map<number, MemberLike>
  unassignedMembers: MemberLike[]
}>()

const emit = defineEmits<{
  reload: []
  error: [failure: Failure]
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

function fail(e: unknown) { emit('error', describeFailure(e, t)) }

const {show: showDeleteModal, target: deleteTarget, requestDelete: openDelete, confirm: confirmDelete, failure: deleteFailure} =
  useConfirmDelete<InventoryItem>({
    onDelete: item => inventory.deleteItem(item.id),
    onSuccess: () => emit('reload'),
  })
watch(deleteFailure, failure => { if (failure) emit('error', failure) })

/**
 * Hands the piece to the chosen member.
 *
 * <p>This used to swallow whatever came back and leave the modal standing, so a refused assignment
 * looked exactly like a slow one and the reader pressed the button again.
 */
async function submitAssign() {
  if (!assignItemId.value || !assignMemberId.value) return
  const mid = Number(assignMemberId.value)
  const m = props.memberMap.get(mid)
  try {
    await inventory.assignItem(assignItemId.value, { memberId: mid, memberName: m?.name ?? '' })
    showAssignModal.value = false
    emit('reload')
  } catch (e) { fail(e) }
}

/** Asks for a piece to be bought. Same as the assignment above: it used to fail without a word. */
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
  } catch (e) { fail(e) }
}

const returnAndLoss = ref<InstanceType<typeof ItemReturnAndLossModals> | null>(null)

function unassign(item: InventoryItem) { void returnAndLoss.value?.unassign(item) }
function markLost(item: InventoryItem) { returnAndLoss.value?.markLost(item) }

async function markFound(item: InventoryItem) {
  try { await inventory.markFound(item.id); emit('reload') }
  catch (e) { fail(e) }
}

async function fulfillProcurement(id: number) {
  try { await procurement.fulfill(id); emit('reload') }
  catch (e) { fail(e) }
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

  <ItemReturnAndLossModals
    ref="returnAndLoss"
    :external="detail?.inventoryType === InventoryType.EXTERNAL"
    @changed="emit('reload')"
    @error="emit('error', $event)"
  />
</template>
