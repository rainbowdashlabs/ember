/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script generic="T" lang="ts" setup>
import {ref, watch, type Ref} from 'vue'
import {VueDraggable, type SortableEvent} from 'vue-draggable-plus'
import DragListRow from './draglist/DragListRow.vue'
import {useFinePointer} from '@/composables/useFinePointer'

/**
 * A list whose rows can be put in a different order, the one way that is done anywhere in Ember.
 *
 * <p>Every row carries the same two arrows, because they are the only thing that works with a finger.
 * Where there is a mouse, the grip between them picks the row up as well, which is faster over a long
 * list. Dragging hangs off the grip rather than the row itself, so a row holding a text field can still
 * be typed in and its text selected.
 *
 * <p>Dragging is the mouse's shortcut and nothing more, so the grip and the drop area carry no
 * meaning for a screen reader or a keyboard: the arrows are the control, and the grip is hidden
 * from assistive technology while the drop area is marked as presentation.
 *
 * <p>The dragging itself is the one drag library the application uses, the same one the kanban board
 * runs on. It moves a copy of the rows while the row is in the air; the order that counts is the
 * caller's, so the copy is put back to it once the row is dropped, and the caller hears the move as
 * one `reorder`.
 */
const props = defineProps<{
  items: T[]
  keyFn: (item: T, index: number) => string | number
  /**
   * Set where the order is not the reader's to choose: a list sorted by name orders itself, and
   * offering to move a row would promise something the next sort undoes.
   */
  disabled?: boolean
}>()

const emit = defineEmits<{
  reorder: [fromIndex: number, toIndex: number]
}>()

const {finePointer} = useFinePointer()

const rows = ref([]) as Ref<T[]>
watch(() => [...props.items], items => {
  rows.value = items
}, {immediate: true})

const dragIndex = ref<number | null>(null)

function move(index: number, direction: -1 | 1) {
  const to = index + direction
  if (to < 0 || to >= props.items.length) return
  emit('reorder', index, to)
}

function onStart(event: SortableEvent) {
  dragIndex.value = event.oldIndex ?? null
}

function onEnd(event: SortableEvent) {
  dragIndex.value = null
  rows.value = [...props.items]
  const {oldIndex, newIndex} = event
  if (oldIndex === undefined || newIndex === undefined || oldIndex === newIndex) return
  emit('reorder', oldIndex, newIndex)
}
</script>

<template>
  <VueDraggable
      v-model="rows"
      :animation="150"
      :disabled="disabled || !finePointer"
      ghost-class="opacity-40"
      handle="[data-drag-grip]"
      role="presentation"
      @end="onEnd"
      @start="onStart"
  >
    <DragListRow
        v-for="(item, index) in rows"
        :key="keyFn(item, index)"
        :disabled="disabled"
        :dragging="dragIndex === index"
        :fine-pointer="finePointer"
        :index="index"
        :total="rows.length"
        @move="move"
    >
      <slot :dragging="dragIndex === index" :index="index" :item="item"/>
    </DragListRow>
  </VueDraggable>
</template>
