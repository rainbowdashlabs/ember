/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {inventory} from '@/api'
import type {InventoryItem} from '@/api/generated/schema'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Taking a piece back from its member and marking it lost, the two item actions every inventory list
 * offers, with the questions they ask.
 *
 * <p>A piece of an external inventory exists only while somebody holds it, so taking it back deletes
 * it and asks first. A piece of the station's own stock goes back into the store, which assigning it
 * again undoes, so it does not ask. Marking a piece lost always asks. Shift skips either question.
 */
const props = defineProps<{
  /** Whether the pieces belong to an external inventory, whose pieces are deleted when taken back. */
  external: boolean
}>()

const emit = defineEmits<{
  changed: []
  error: [failure: Failure]
}>()

const {t} = useI18n()

const returnExternal = useConfirmDelete<InventoryItem>({
  onDelete: item => inventory.deleteItem(item.id),
  onSuccess: () => emit('changed'),
})

const lost = useConfirmAction<InventoryItem>({
  onConfirm: async item => {
    await inventory.markLost(item.id)
  },
  onSuccess: () => emit('changed'),
})

watch(returnExternal.failure, failure => { if (failure) emit('error', failure) })
watch(lost.failure, failure => { if (failure) emit('error', failure) })

const {show: showReturnExternal, target: returnTarget, confirm: confirmReturnExternal} = returnExternal
const {show: showMarkLost, target: lostTarget, confirm: confirmMarkLost} = lost

/** Takes the piece back from its member, asking first where that deletes it. */
async function unassign(item: InventoryItem) {
  if (props.external) {
    returnExternal.requestDelete(item)
    return
  }
  try {
    await inventory.assignItem(item.id, {memberId: null, memberName: ''})
    emit('changed')
  } catch (e) {
    emit('error', describeFailure(e, t))
  }
}

/** Marks the piece lost, after asking unless shift is held. */
function markLost(item: InventoryItem) {
  lost.request(item)
}

defineExpose({unassign, markLost})
</script>

<template>
  <ConfirmDeleteModal
      v-model="showReturnExternal"
      :message="t('inventory.edit.unassignExternalConfirm', {name: returnTarget?.name ?? ''})"
      :confirm-label="t('inventory.edit.unassign')"
      @confirm="confirmReturnExternal"
  />

  <ConfirmDeleteModal
      v-model="showMarkLost"
      :message="t('inventory.edit.markLostConfirm', {name: lostTarget?.name ?? ''})"
      :confirm-label="t('inventory.edit.markLostAction')"
      @confirm="confirmMarkLost"
  />
</template>
