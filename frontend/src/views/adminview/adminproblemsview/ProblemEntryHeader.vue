/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import type {ProblemEntry} from '@/api/problems'
import {formatDateTime} from '@/util/format'

const props = defineProps<{
  entry: ProblemEntry
  expanded: boolean
}>()

const emit = defineEmits<{
  ack: [id: number]
}>()

const {t} = useI18n()

const levelClass = computed(() => props.entry.level === 'ERROR'
  ? 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'
  : 'bg-yellow-100 dark:bg-yellow-900/30 text-yellow-700 dark:text-yellow-400')

function shortLogger(logger: string): string {
  const parts = logger.split('.')
  return parts[parts.length - 1] ?? logger
}

</script>

<template>
  <div class="flex items-start justify-between gap-3 overflow-hidden">
    <div class="flex-1 min-w-0 overflow-hidden">
      <div class="flex items-center gap-2 mb-1">
        <span class="text-xs font-mono px-1.5 py-0.5 rounded" :class="levelClass">{{ entry.level }}</span>
        <span class="text-xs font-mono text-[var(--text-muted)]">{{ shortLogger(entry.logger) }}</span>
        <span v-if="entry.count > 1"
              class="text-xs font-semibold px-1.5 py-0.5 rounded-full bg-[var(--bg-accent)]">
          {{ entry.count }}x
        </span>
      </div>
      <p class="text-sm font-medium truncate max-w-full">
        {{ entry.exceptionClass ? `${entry.exceptionClass}: ${entry.exceptionMessage}` : entry.distinctMessages[0] }}
      </p>
      <p class="text-xs text-[var(--text-muted)]">
        {{ formatDateTime(entry.firstOccurrence) }}
        <template v-if="entry.count > 1"> - {{ formatDateTime(entry.lastOccurrence) }}</template>
      </p>
    </div>
    <div class="flex items-center gap-1 shrink-0">
      <IconButton
        v-if="!entry.acknowledged"
        :icon="['fas', 'check']"
        :label="t('adminProblems.acknowledge')"
        @click.stop="emit('ack', entry.id)"
      />
      <font-awesome-icon
        :icon="['fas', expanded ? 'chevron-up' : 'chevron-down']"
        class="text-xs text-[var(--text-muted)]"
      />
    </div>
  </div>
</template>
