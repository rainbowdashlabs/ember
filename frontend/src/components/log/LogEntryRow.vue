/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedIconButton from '@/components/button/MutedIconButton.vue'
import type {LogEntry} from '@/api/applicationLog'

/**
 * One log line.
 *
 * The severity carries the colour because that is what a reader scans by. A stack trace is folded
 * away: it is the most useful thing on the line and the longest, and a list where every error opens
 * its trace is a list nobody can scroll.
 */
const props = defineProps<{
  entry: LogEntry
}>()

const {t} = useI18n()

const expanded = ref(false)

const tone = computed(() => {
  switch (props.entry.level) {
    case 'ERROR':
      return 'border-(--error)'
    case 'WARN':
      return 'border-(--warning)'
    case 'INFO':
      return 'border-(--accent)'
    default:
      return 'border-(--border)'
  }
})

const when = computed(() => new Date(props.entry.loggedAt).toLocaleString('de-DE'))

/** The last part of the logger name, which is what identifies it to a reader. */
const shortLogger = computed(() => props.entry.logger.split('.').pop() ?? props.entry.logger)
</script>

<template>
  <div class="rounded-lg border border-l-4 border-(--border) p-2 space-y-1" :class="tone">
    <div class="flex items-baseline gap-2 flex-wrap text-xs text-(--text-muted)">
      <span class="font-semibold">{{ entry.level }}</span>
      <span>{{ when }}</span>
      <span :title="entry.logger">{{ shortLogger }}</span>
      <span class="font-mono">{{ entry.thread }}</span>
      <MutedIconButton
          v-if="entry.throwable"
          :icon="['fas', expanded ? 'chevron-up' : 'chevron-down']"
          :label="t('applicationLog.showTrace')"
          hover="text"
          @click="expanded = !expanded"/>
    </div>
    <div class="text-sm break-words whitespace-pre-wrap font-mono">{{ entry.message }}</div>
    <pre v-if="expanded && entry.throwable"
         class="max-h-80 overflow-auto rounded bg-(--bg-accent) p-2 text-xs whitespace-pre">{{ entry.throwable }}</pre>
  </div>
</template>
