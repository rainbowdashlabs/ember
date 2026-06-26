/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'

type RankingAnswer = { order: number[] }

const props = defineProps<{
  config: Record<string, unknown>
}>()

const answer = defineModel<RankingAnswer>({ required: true })

const options = computed(() => (props.config.options as string[]) || [])

function move(fromIdx: number, direction: -1 | 1) {
  const toIdx = fromIdx + direction
  if (toIdx < 0 || toIdx >= answer.value.order.length) return
  const temp = answer.value.order[fromIdx]
  answer.value.order[fromIdx] = answer.value.order[toIdx]
  answer.value.order[toIdx] = temp
}
</script>

<template>
  <div class="space-y-1">
    <div v-for="(optIdx, rank) in (answer.order ?? [])" :key="rank"
         class="flex items-center gap-2 px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent">
      <span class="text-xs text-(--text-muted) w-5">{{ rank + 1 }}.</span>
      <span class="flex-1 text-sm">{{ options[optIdx] }}</span>
      <MutedIconButton :icon="['fas', 'chevron-up']" :label="'Up'" @click="move(rank, -1)" />
      <MutedIconButton :icon="['fas', 'chevron-down']" :label="'Down'" @click="move(rank, 1)" />
    </div>
  </div>
</template>
