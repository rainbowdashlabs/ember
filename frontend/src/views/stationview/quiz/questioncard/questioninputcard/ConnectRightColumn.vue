/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
defineProps<{
  items: string[]
  disabled: boolean
  selectedLeftIdx: number | null
  connectDragOver: number | null
  isRightConnected: (right: string) => boolean
}>()

const emit = defineEmits<{
  rightClick: [rightIdx: number]
  dragOver: [event: DragEvent, rightIdx: number]
  drop: [event: DragEvent, rightIdx: number]
}>()
</script>

<template>
  <div class="flex flex-col gap-2 flex-1">
    <button
      v-for="(right, rightIdx) in items"
      :key="rightIdx"
      type="button"
      :data-connect-right="rightIdx"
      class="px-3 py-2 rounded-lg border-2 text-sm font-medium text-left transition-colors cursor-pointer"
      :class="[
        connectDragOver === rightIdx
          ? 'border-primary bg-primary/10'
          : isRightConnected(right)
            ? 'border-success bg-success/5'
            : selectedLeftIdx !== null
              ? 'border-primary/50 hover:border-primary hover:bg-primary/5'
              : 'border-bg-light-accent dark:border-bg-dark-accent',
        disabled ? 'pointer-events-none opacity-60' : ''
      ]"
      @click="emit('rightClick', rightIdx)"
      @dragover="emit('dragOver', $event, rightIdx)"
      @drop="emit('drop', $event, rightIdx)"
    >
      {{ right }}
    </button>
  </div>
</template>
