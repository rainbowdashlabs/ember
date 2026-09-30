/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import BaseButton from '@/components/button/BaseButton.vue'

defineProps<{
  items: string[]
  disabled: boolean
  selectedLeftIdx: number | null
  connectPairs: Record<string, string>
}>()

const emit = defineEmits<{
  leftClick: [leftIdx: number]
  dragStart: [event: DragEvent, leftIdx: number]
  dragEnd: []
  removeConnection: [leftIdx: number]
}>()
</script>

<template>
  <div class="flex flex-col gap-2 flex-1">
    <BaseButton
      v-for="(left, leftIdx) in items"
      :key="leftIdx"
      :data-connect-left="leftIdx"
      :draggable="!disabled"
      class="!py-2 !rounded-lg border-2 text-left cursor-pointer"
      :class="[
        selectedLeftIdx === leftIdx
          ? 'border-primary bg-primary/10'
          : connectPairs[String(leftIdx)]
            ? 'border-success bg-success/5'
            : 'border-bg-light-accent dark:border-bg-dark-accent hover:border-primary/50',
        disabled ? 'pointer-events-none opacity-60' : ''
      ]"
      @click="emit('leftClick', leftIdx)"
      @dragstart="emit('dragStart', $event, leftIdx)"
      @dragend="emit('dragEnd')"
    >
      {{ left }}
      <font-awesome-icon
        v-if="connectPairs[String(leftIdx)]"
        :icon="['fas', 'xmark']"
        class="ml-1 text-xs text-(--text-muted) hover:text-error"
        @click.stop="emit('removeConnection', leftIdx)"
      />
    </BaseButton>
  </div>
</template>
