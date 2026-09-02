/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SubHeader from '@/components/typography/SubHeader.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import type {LineCheck} from '@/api/equipment'

/** One line as the screen holds it, before anything has been asked of anybody. */
export interface CollectedEntry {
  key: string
  owningStationId: string
  stationName: string
  inventoryId: number
  inventoryName: string
  artId: number | null
  label: string
  quantity: number
  needId: number | null
}

const props = defineProps<{
  entries: CollectedEntry[]
  checks: LineCheck[]
  sending: boolean
  /** Whether anything can be asked for at all, which needs the evening the list is being built for. */
  canSend: boolean
}>()

const emit = defineEmits<{
  remove: [key: string]
  send: []
}>()

const {t} = useI18n()

/**
 * The list grouped by the station it will be asked of. A request has a single owning station, so a
 * list spanning three stations is three letters, and a list that quietly became three letters is a
 * list nobody can follow up.
 */
const groups = computed(() => {
  const byStation = new Map<string, CollectedEntry[]>()
  for (const entry of props.entries) {
    const group = byStation.get(entry.owningStationId) ?? []
    group.push(entry)
    byStation.set(entry.owningStationId, group)
  }
  return [...byStation.entries()].map(([stationId, lines]) => ({
    stationId,
    stationName: lines[0]?.stationName ?? '',
    lines,
  }))
})

const changed = computed(() => props.checks.filter(check => check.changed))

function availableFor(entry: CollectedEntry): number | null {
  const check = props.checks.find(
      candidate =>
          candidate.line.owningStationId === entry.owningStationId
          && candidate.line.inventoryId === entry.inventoryId
          && candidate.line.artId === entry.artId)
  return check ? check.available : null
}
</script>

<template>
  <div class="space-y-4" data-testid="collected-list">
    <SubHeader>{{ t('lendingCollect.listTitle') }}</SubHeader>

    <p v-if="entries.length === 0" class="text-sm text-(--text-muted)" data-testid="collected-empty">
      {{ t('lendingCollect.listEmpty') }}
    </p>

    <div v-for="group in groups" :key="group.stationId" class="space-y-1" data-testid="collected-group">
      <span class="text-sm font-medium">{{ group.stationName }}</span>
      <div
          v-for="entry in group.lines"
          :key="entry.key"
          class="flex flex-wrap items-center gap-2 px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50"
          data-testid="collected-line"
      >
        <span class="text-sm">{{ entry.quantity }}x {{ entry.label }}</span>
        <ErrorBadge
            v-if="availableFor(entry) !== null && availableFor(entry)! < entry.quantity"
            data-testid="collected-line-changed"
        >
          {{ t('lendingCollect.onlyLeft', {count: availableFor(entry)}) }}
        </ErrorBadge>
        <DeleteButton data-testid="collected-remove" @click="emit('remove', entry.key)"/>
      </div>
    </div>

    <p v-if="changed.length > 0" class="text-sm" data-testid="collected-changed">
      {{ t('lendingCollect.somethingChanged', {count: changed.length}) }}
    </p>

    <PrimaryButton
        v-if="entries.length > 0"
        :disabled="sending || !canSend"
        data-testid="collected-send"
        @click="emit('send')"
    >
      {{ t('lendingCollect.send', {count: groups.length}) }}
    </PrimaryButton>
  </div>
</template>
