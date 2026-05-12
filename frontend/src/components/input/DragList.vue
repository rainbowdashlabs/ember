/*
*     SPDX-License-Identifier: AGPL-3.0-only
*
*     Copyright (C) RainbowDashLabs and Contributor
*/
<script generic="T" lang="ts" setup>
import {ref} from 'vue'

defineProps<{
  items: T[]
  keyFn: (item: T, index: number) => string | number
}>()

const emit = defineEmits<{
  reorder: [fromIndex: number, toIndex: number]
}>()

const dragIndex = ref<number | null>(null)
const dropIndicator = ref<number | null>(null)

function onDragStart(index: number, event: DragEvent) {
  dragIndex.value = index
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

  if (event.clientY < midY) {
    dropIndicator.value = index
  } else {
    dropIndicator.value = index + 1
  }
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
      <!-- Drop indicator line before item -->
      <div
          v-if="dropIndicator === index && dragIndex !== index && dragIndex !== index - 1"
          class="h-0.5 bg-primary rounded-full mx-2 my-1"
      />

      <div
          :class="{ 'opacity-40': dragIndex === index }"
          draggable="true"
          @dragend="onDragEnd"
          @dragleave="onDragLeave($event)"
          @dragover="onDragOver(index, $event)"
          @dragstart="onDragStart(index, $event)"
      >
        <slot :dragging="dragIndex === index" :index="index" :item="item"/>
      </div>
    </template>

    <!-- Drop indicator at the end -->
    <div
        v-if="dropIndicator === items.length && dragIndex !== items.length - 1"
        class="h-0.5 bg-primary rounded-full mx-2 my-1"
    />
  </div>
</template>
