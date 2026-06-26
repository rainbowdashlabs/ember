/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type { TestProtocolSection } from '@/api/protocol'

defineProps<{
  sections: TestProtocolSection[]
  currentIndex: number
  doneSections: Set<number>
  checkedScore: (sectionId: number) => number
  maxScore: (sectionId: number) => number
}>()

defineEmits<{
  (e: 'select', index: number): void
}>()
</script>

<template>
  <div class="flex flex-wrap gap-1.5 mb-4">
    <SelectionToggleButton
      v-for="(sec, idx) in sections"
      :key="sec.id"
      :selected="idx === currentIndex"
      @toggle="$emit('select', idx)"
    >
      <font-awesome-icon v-if="doneSections.has(sec.id)" :icon="['fas', 'circle-check']" class="w-3 h-3 text-[var(--success)] mr-1" />
      {{ sec.name }}
      <span class="ml-1 font-mono">{{ checkedScore(sec.id) }}/{{ maxScore(sec.id) }}</span>
    </SelectionToggleButton>
  </div>
</template>
