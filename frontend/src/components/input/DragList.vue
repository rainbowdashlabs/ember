/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script generic="T" lang="ts" setup>
import {ref} from 'vue'
import DragListRow from './draglist/DragListRow.vue'
import {useFinePointer} from '@/composables/useFinePointer'

/**
 * A list whose rows can be put in a different order, the one way that is done anywhere in Ember.
 *
 * <p>Every row carries the same two arrows, because they are the only thing that works with a finger.
 * Where there is a mouse, the grip between them picks the row up as well, which is faster over a long
 * list. Dragging hangs off the grip rather than the row itself, so a row holding a text field can still
 * be typed in and its text selected.
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

const dragIndex = ref<number | null>(null)
const dropIndicator = ref<number | null>(null)

function move(index: number, direction: -1 | 1) {
  const to = index + direction
  if (to < 0 || to >= props.items.length) return
  emit('reorder', index, to)
}

/**
 * Picks the row up by its grip, while dragging the whole row rather than the little icon that was
 * grabbed: what is being moved is the row, and a cursor towing one icon says otherwise.
 */
function onDragStart(index: number, event: DragEvent) {
  dragIndex.value = index
  const row = (event.currentTarget as HTMLElement).closest('[data-drag-row]')
  if (row && event.dataTransfer) {
    event.dataTransfer.setDragImage(row, 12, 12)
  }
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onDragOver(index: number, event: DragEvent) {
  event.preventDefault()
  if (dragIndex.value === null) return

  const target = event.currentTarget as HTMLElement
  const rect = target.getBoundingClientRect()
  const midY = rect.top + rect.height / 2

  dropIndicator.value = event.clientY < midY ? index : index + 1
}

function onDragLeave(event: DragEvent) {
  const target = event.currentTarget as HTMLElement
  if (!target.contains(event.relatedTarget as Node)) {
    dropIndicator.value = null
  }
}

function onDrop() {
  if (dragIndex.value !== null && dropIndicator.value !== null) {
    let toIndex = dropIndicator.value
    if (toIndex > dragIndex.value) {
      toIndex--
    }
    if (toIndex !== dragIndex.value) {
      emit('reorder', dragIndex.value, toIndex)
    }
  }
  dragIndex.value = null
  dropIndicator.value = null
}

function onDragEnd() {
  dragIndex.value = null
  dropIndicator.value = null
}

function onContainerDragOver(event: DragEvent) {
  event.preventDefault()
}
</script>

<template>
  <div @dragover="onContainerDragOver" @drop="onDrop">
    <template v-for="(item, index) in items" :key="keyFn(item, index)">
      <div
          v-if="dropIndicator === index && dragIndex !== index && dragIndex !== index - 1"
          class="h-0.5 bg-primary rounded-full mx-2 my-1"
      />

      <DragListRow
          :disabled="disabled"
          :dragging="dragIndex === index"
          :fine-pointer="finePointer"
          :index="index"
          :total="items.length"
          @dragend="onDragEnd"
          @dragleave="onDragLeave($event)"
          @dragover="onDragOver(index, $event)"
          @grab="onDragStart"
          @move="move"
      >
        <slot :dragging="dragIndex === index" :index="index" :item="item"/>
      </DragListRow>
    </template>

    <div
        v-if="dropIndicator === items.length && dragIndex !== items.length - 1"
        class="h-0.5 bg-primary rounded-full mx-2 my-1"
    />
  </div>
</template>
