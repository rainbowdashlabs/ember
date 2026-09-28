/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { computed } from 'vue'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import type { RankingAnswer } from '@/api/forms'
import { optionLabel, optionsOf } from '@/util/formOptions'

const props = defineProps<{
  config: Record<string, unknown>
}>()

const answer = defineModel<RankingAnswer>({ required: true })

const options = computed(() => optionsOf(props.config))

function move(fromIdx: number, direction: -1 | 1) {
  const toIdx = fromIdx + direction
  const from = answer.value.order[fromIdx]
  const to = answer.value.order[toIdx]
  if (from === undefined || to === undefined) return
  answer.value.order[fromIdx] = to
  answer.value.order[toIdx] = from
}
</script>

<template>
  <div class="space-y-1">
    <div v-for="(optionKey, rank) in (answer.order ?? [])" :key="optionKey"
         class="flex items-center gap-2 px-3 py-2 rounded border border-bg-light-accent dark:border-bg-dark-accent">
      <span class="text-xs text-(--text-muted) w-5">{{ rank + 1 }}.</span>
      <span class="flex-1 text-sm">{{ optionLabel(options, optionKey) }}</span>
      <MutedIconButton :icon="['fas', 'chevron-up']" :label="'Up'" @click="move(rank, -1)" />
      <MutedIconButton :icon="['fas', 'chevron-down']" :label="'Down'" @click="move(rank, 1)" />
    </div>
  </div>
</template>
