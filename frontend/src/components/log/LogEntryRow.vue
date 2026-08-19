/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {LogEntry} from '@/api/applicationLog'

/**
 * One log line.
 *
 * The severity carries the colour because that is what a reader scans by. A stack trace is folded
 * away: it is the most useful thing on the line and the longest, and a list where every error opens
 * its trace is a list nobody can scroll. That it has one is said outright, so the lines worth
 * opening can be picked out without opening any.
 */
const props = defineProps<{
  entry: LogEntry
}>()

const {t} = useI18n()

const expanded = ref(false)

interface Tone {
  border: string
  badge: string
}

const QUIET: Tone = {border: 'border-l-(--border)', badge: 'bg-(--bg-accent) text-(--text-muted)'}

const LEVEL_TONES: Record<string, Tone> = {
  ERROR: {border: 'border-l-(--error)', badge: 'bg-(--error) text-white'},
  WARN: {border: 'border-l-(--warning)', badge: 'bg-(--warning) text-black'},
  INFO: {border: 'border-l-(--accent)', badge: 'bg-(--accent) text-white'},
  DEBUG: QUIET,
  TRACE: QUIET,
}

const tone = computed<Tone>(() => LEVEL_TONES[props.entry.level] ?? QUIET)

const when = computed(() => new Date(props.entry.loggedAt).toLocaleString('de-DE'))

/** The last part of the logger name, which is what identifies it to a reader. */
const shortLogger = computed(() => props.entry.logger.split('.').pop() ?? props.entry.logger)
</script>

<template>
  <div :class="tone.border" class="rounded-lg border border-l-4 border-(--border) p-2 space-y-1">
    <div class="flex items-baseline gap-2 flex-wrap text-xs text-(--text-muted)">
      <span :class="tone.badge" class="rounded px-1.5 py-0.5 font-semibold">{{ entry.level }}</span>
      <span>{{ when }}</span>
      <span :title="entry.logger">{{ shortLogger }}</span>
      <span class="font-mono">{{ entry.thread }}</span>
      <button
          v-if="entry.throwable"
          class="flex items-center gap-1 rounded border border-(--error) px-1.5 py-0.5 font-semibold text-(--error) hover:bg-(--error) hover:text-white"
          type="button"
          @click="expanded = !expanded"
      >
        <font-awesome-icon :icon="['fas', 'triangle-exclamation']"/>
        {{ t('applicationLog.hasTrace') }}
        <font-awesome-icon :icon="['fas', expanded ? 'chevron-up' : 'chevron-down']"/>
      </button>
    </div>
    <div class="text-sm break-words whitespace-pre-wrap font-mono">{{ entry.message }}</div>
    <pre v-if="expanded && entry.throwable"
         class="max-h-80 overflow-auto rounded bg-(--bg-accent) p-2 text-xs whitespace-pre">{{ entry.throwable }}</pre>
  </div>
</template>
