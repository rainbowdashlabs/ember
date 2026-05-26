/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {PublicEvent} from '@/api/publicEvents'
import {marked} from 'marked'

defineProps<{
  events: PublicEvent[]
}>()

const {t} = useI18n()

const dayNames = ['', 'Montag', 'Dienstag', 'Mittwoch', 'Donnerstag', 'Freitag', 'Samstag', 'Sonntag']

function formatDate(iso?: string): string {
  if (!iso) return ''
  return new Date(iso).toLocaleDateString('de-DE', {weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric'})
}

function formatTime(iso?: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function isRecurring(eventType?: string): boolean {
  return eventType != null && eventType !== 'ONE_TIME'
}

function renderMd(md?: string): string {
  if (!md) return ''
  try { return marked.parse(md) as string } catch { return md }
}
</script>

<template>
  <MutedText v-if="events.length === 0" tag="div" size="sm" class="py-4">
    {{ t('publicStation.noEvents') }}
  </MutedText>

  <div v-else class="space-y-3">
    <NeutralContainer v-for="ev in events" :key="ev.id" class="space-y-2">
      <div class="flex items-center justify-between flex-wrap gap-2">
        <div class="flex items-center gap-2">
          <span class="font-semibold">{{ ev.name }}</span>
          <SecondaryBadge v-if="isRecurring(ev.eventType)">
            <font-awesome-icon :icon="['fas', 'rotate']" class="mr-1 h-3 w-3"/>
            {{ ev.categoryName }}
          </SecondaryBadge>
          <SecondaryBadge v-else-if="ev.categoryName">{{ ev.categoryName }}</SecondaryBadge>
        </div>
        <span class="text-sm text-(--text-muted)">
          <template v-if="isRecurring(ev.eventType) && ev.dayOfWeek">
            {{ dayNames[ev.dayOfWeek] }}, {{ formatTime(ev.startTime) }} – {{ formatTime(ev.endTime) }}
          </template>
          <template v-else>
            {{ formatDate(ev.startTime) }}, {{ formatTime(ev.startTime) }} – {{ formatTime(ev.endTime) }}
          </template>
        </span>
      </div>
      <div v-if="ev.description" class="prose prose-sm dark:prose-invert max-w-none" v-html="renderMd(ev.description)"/>
      <div v-if="ev.publicFields?.length" class="flex flex-wrap gap-3 text-xs text-(--text-muted)">
        <span v-for="f in ev.publicFields" :key="f.name">
          <span class="font-medium">{{ f.name }}:</span> {{ f.value }}
        </span>
      </div>
    </NeutralContainer>
  </div>
</template>
