/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'

defineProps<{
  disabled: boolean
  isSelected: (fragment: string) => boolean
}>()

const emit = defineEmits<{
  select: [fragment: string]
}>()

const eyeDirections = ['Left', 'Mid', 'Right'] as const
const eyeOpenness = ['Open', 'Half', 'Blink'] as const
const eyeMatrix: Record<string, Record<string, string>> = {
  Left:  { Open: 'fire_eyes_left',  Half: 'fire_eyes_left_half',  Blink: 'fire_blink_left' },
  Mid:   { Open: 'fire_eyes_mid',   Half: 'fire_eyes_mid_half',   Blink: 'fire_blink' },
  Right: { Open: 'fire_eyes_right', Half: 'fire_eyes_right_half', Blink: 'fire_blink_right' },
}

function onToggle(fragment: string) {
  emit('select', fragment)
}
</script>

<template>
  <div :class="{ 'opacity-40 pointer-events-none': disabled }">
    <p class="text-xs font-medium text-(--text-muted) mb-1">Augen</p>
    <div class="grid grid-cols-4 gap-1 text-xs">
      <div/>
      <div v-for="o in eyeOpenness" :key="o" class="text-center text-(--text-muted) font-medium py-1">{{ o }}</div>
      <template v-for="d in eyeDirections" :key="d">
        <div class="text-(--text-muted) font-medium flex items-center">{{ d }}</div>
        <SelectionToggleButton
          v-for="o in eyeOpenness"
          :key="eyeMatrix[d][o]"
          size="sm"
          :selected="isSelected(eyeMatrix[d][o])"
          @toggle="onToggle(isSelected(eyeMatrix[d][o]) ? '' : eyeMatrix[d][o])"
        >
          {{ o }}
        </SelectionToggleButton>
      </template>
    </div>
  </div>
</template>
