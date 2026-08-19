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

/**
 * The severity colours, as the level's own colour on a tint of it rather than as a filled block.
 *
 * A filled badge needs a foreground that contrasts with it, which is a different colour in each
 * theme and was hardcoded to white and black here. Colouring the text instead leaves the surface to
 * the theme, so both work without either being written down.
 */
const TRACE_TONE: Tone = {border: 'border-l-(--border)', badge: 'text-(--text-muted)'}

const LEVEL_TONES: Record<string, Tone> = {
  ERROR: {border: 'border-l-error', badge: 'bg-error/15 text-error'},
  WARN: {border: 'border-l-warning', badge: 'bg-warning/15 text-warning'},
  INFO: {border: 'border-l-(--text)', badge: 'text-(--text)'},
  DEBUG: {border: 'border-l-secondary', badge: 'bg-secondary/15 text-secondary'},
  TRACE: TRACE_TONE,
}

const tone = computed<Tone>(() => LEVEL_TONES[props.entry.level] ?? TRACE_TONE)

const when = computed(() => new Date(props.entry.loggedAt).toLocaleString('de-DE'))

/** The last part of the logger name, which is what identifies it to a reader. */
const shortLogger = computed(() => props.entry.logger.split('.').pop() ?? props.entry.logger)
</script>

<template>
  <div :class="tone.border" class="rounded-lg border border-l-4 border-(--border) p-2 space-y-1">
    <div class="flex items-baseline gap-2 flex-wrap text-xs text-(--text-muted)">
      <span :class="tone.badge" class="w-14 shrink-0 rounded px-1.5 py-0.5 text-center font-semibold">
        {{ entry.level }}
      </span>
      <span>{{ when }}</span>
      <span :title="entry.logger">{{ shortLogger }}</span>
      <span class="font-mono">{{ entry.thread }}</span>
      <button
          v-if="entry.throwable"
          class="flex items-center gap-1 rounded border border-error px-1.5 py-0.5 font-semibold text-error hover:bg-error/15"
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
