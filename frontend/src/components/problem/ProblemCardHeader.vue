/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import ProblemLevelBadge from './ProblemLevelBadge.vue'
import {formatDateTime} from '@/util/format'

/**
 * The line a problem opens with, whether it happened on this instance or was reported here by
 * another one: how bad it is, where it came from, how often it was met and what it said.
 *
 * <p>The `badges` slot takes whatever a source knows on top of that, the `notes` slot the lines
 * that do not fit beside the heading, and the `actions` slot the buttons in front of the chevron.
 */
const props = withDefaults(defineProps<{
  level: string
  logger?: string | null
  title: string
  count: number
  firstOccurrence: string
  lastOccurrence: string
  expandable?: boolean
  expanded?: boolean
}>(), {
  logger: null,
  expandable: false,
  expanded: false,
})

const shortLogger = computed(() => props.logger?.split('.').pop() ?? '')
</script>

<template>
  <div class="flex items-start justify-between gap-3 overflow-hidden">
    <div class="flex-1 min-w-0 overflow-hidden">
      <div class="flex items-center gap-2 mb-1 flex-wrap">
        <ProblemLevelBadge :level="level"/>
        <span v-if="shortLogger" class="text-xs font-mono text-(--text-muted)">{{ shortLogger }}</span>
        <span v-if="count > 1" class="text-xs font-semibold px-1.5 py-0.5 rounded-full bg-(--bg-accent)">
          {{ count }}x
        </span>
        <slot name="badges"/>
      </div>
      <p class="text-sm font-medium max-w-full break-words line-clamp-3 sm:line-clamp-none sm:truncate">{{ title }}</p>
      <p class="text-xs text-(--text-muted)">
        {{ formatDateTime(firstOccurrence) }}
        <template v-if="count > 1"> - {{ formatDateTime(lastOccurrence) }}</template>
      </p>
      <slot name="notes"/>
    </div>
    <div class="flex items-center gap-1 shrink-0">
      <slot name="actions"/>
      <font-awesome-icon
          v-if="expandable"
          :icon="['fas', expanded ? 'chevron-up' : 'chevron-down']"
          class="text-xs text-(--text-muted)"
      />
    </div>
  </div>
</template>
