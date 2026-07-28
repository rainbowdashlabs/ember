/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'

const props = defineProps<{
  config: Record<string, unknown>
  disabled: boolean
  orderItems: number[]
}>()

const emit = defineEmits<{
  reorderItems: [fromIndex: number, toIndex: number]
  moveOrderItem: [index: number, direction: -1 | 1]
}>()

const { t } = useI18n()

const orderedItems = computed<string[]>(() => {
  const items = (props.config.items as string[]) ?? []
  if (props.orderItems.length === items.length) return props.orderItems.map(i => items[i])
  return items
})

const dragIndex = ref<number | null>(null)
const dragOverIndex = ref<number | null>(null)

function onDragStart(e: DragEvent, index: number) {
  dragIndex.value = index
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
  }
}

function onDragOver(e: DragEvent, index: number) {
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
  dragOverIndex.value = index
}

function onDrop(e: DragEvent, toIndex: number) {
  e.preventDefault()
  if (dragIndex.value !== null && dragIndex.value !== toIndex) {
    emit('reorderItems', dragIndex.value, toIndex)
  }
  dragIndex.value = null
  dragOverIndex.value = null
}

function onDragEnd() {
  dragIndex.value = null
  dragOverIndex.value = null
}
</script>

<template>
  <div class="space-y-2">
    <div
      v-for="(item, i) in orderedItems"
      :key="i"
      :draggable="!disabled"
      class="flex items-center gap-2 p-2 rounded-lg border transition-colors"
      :class="[
        dragOverIndex === i && dragIndex !== i
          ? 'border-primary bg-primary/5'
          : 'border-bg-light-accent dark:border-bg-dark-accent',
        dragIndex === i ? 'opacity-50' : '',
        !disabled ? 'cursor-grab active:cursor-grabbing' : ''
      ]"
      @dragstart="onDragStart($event, i)"
      @dragover="onDragOver($event, i)"
      @drop="onDrop($event, i)"
      @dragend="onDragEnd"
    >
      <MutedIcon v-if="!disabled" :icon="['fas', 'grip-vertical']" size="inline" class="shrink-0"/>
      <span class="text-xs text-(--text-muted) w-5 text-right shrink-0">{{ i + 1 }}.</span>
      <span class="flex-1 text-sm">{{ item }}</span>
      <div v-if="!disabled" class="flex flex-col gap-0.5 shrink-0">
        <IconButton :icon="['fas', 'chevron-up']" :label="t('quiz.attempt.moveUp')" :disabled="i === 0" class="text-xs" @click="emit('moveOrderItem', i, -1)" />
        <IconButton :icon="['fas', 'chevron-down']" :label="t('quiz.attempt.moveDown')" :disabled="i === orderedItems.length - 1" class="text-xs" @click="emit('moveOrderItem', i, 1)" />
      </div>
    </div>
  </div>
</template>
