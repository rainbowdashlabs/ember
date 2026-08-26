/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {StationChoice} from '@/composables/useDemoAccounts'

/**
 * Which station's people are on offer.
 *
 * A row of tabs read as equals and grew with every station the demo gained. This says what each one
 * is and how many people it holds, which is what tells a station the demo builds in full from a
 * spare standing beside it.
 */
defineProps<{
  choices: StationChoice[]
  compact?: boolean
}>()

const active = defineModel<string>({required: true})
</script>

<template>
  <div class="flex flex-wrap gap-2" data-testid="demo-station-picker">
    <button
        v-for="choice in choices"
        :key="choice.key"
        type="button"
        data-testid="demo-station-choice"
        :class="[
          active === choice.key
            ? 'border-primary text-primary bg-primary/10'
            : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary hover:text-(--text)',
          compact ? 'px-2 py-1 text-xs gap-1.5' : 'px-3 py-1.5 text-sm gap-2',
        ]"
        class="inline-flex items-center rounded-theme border transition-colors"
        @click="active = choice.key"
    >
      <span class="font-medium">{{ choice.label }}</span>
      <span :class="compact ? 'text-[10px]' : 'text-xs'"
            class="rounded-full bg-secondary/15 text-secondary-accent px-1.5">{{ choice.memberCount }}</span>
    </button>
  </div>
</template>
