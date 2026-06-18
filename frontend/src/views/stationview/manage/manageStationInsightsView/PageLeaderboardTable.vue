/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {PageLeaderboardEntry} from '@/api/insights'

const props = defineProps<{
  rows: PageLeaderboardEntry[]
  includeBots: boolean
  selectedPageId: number | null
}>()

const emit = defineEmits<{
  (e: 'select', pageId: number): void
}>()

const {t, n} = useI18n()

const sortedRows = computed(() => {
  const copy = [...props.rows]
  copy.sort((a, b) => totalFor(b) - totalFor(a))
  return copy
})

function totalFor(row: PageLeaderboardEntry): number {
  return props.includeBots ? row.hits + row.botHits : row.hits
}
</script>

<template>
  <div class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
      <tr class="text-left text-(--text-muted)">
        <th class="py-2 pr-3">{{ t('insights.table.page') }}</th>
        <th class="py-2 pr-3 text-right">{{ t('insights.table.hits') }}</th>
        <th class="py-2 pr-3 text-right">{{ t('insights.table.botHits') }}</th>
        <th class="py-2 pr-3"></th>
      </tr>
      </thead>
      <tbody>
      <tr v-for="row in sortedRows" :key="row.pageId" class="border-t border-(--border)">
        <td class="py-2 pr-3">
          <div class="font-semibold">{{ row.title }}</div>
          <MutedText tag="div" size="xs">/{{ row.slug }}</MutedText>
        </td>
        <td class="py-2 pr-3 text-right font-mono">{{ n(row.hits) }}</td>
        <td class="py-2 pr-3 text-right font-mono">{{ n(row.botHits) }}</td>
        <td class="py-2 pr-3 text-right">
          <SecondaryButton
              :class="selectedPageId === row.pageId ? 'ring-2 ring-(--accent)' : ''"
              @click="emit('select', row.pageId)">
            {{ t('insights.table.details') }}
          </SecondaryButton>
        </td>
      </tr>
      <tr v-if="rows.length === 0">
        <td class="py-3 text-center text-(--text-muted)" colspan="4">{{ t('insights.noPages') }}</td>
      </tr>
      </tbody>
    </table>
  </div>
</template>
