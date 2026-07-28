/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TableHeaderCell from '@/components/typography/TableHeaderCell.vue'

export interface StationTotal {
  stationId: string | null
  ingressBytes: number
  egressBytes: number
  requests: number
}

defineProps<{
  entries: StationTotal[]
  stationLabel: (stationId: string | null) => string
  formatBytes: (bytes: number) => string
}>()

const {t, n} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2">
    <SectionHeader>{{ t('traffic.stationLeaderboard.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('traffic.stationLeaderboard.hint') }}</MutedText>
    <table v-if="entries.length > 0" class="w-full text-sm">
      <thead>
      <tr class="text-left text-(--text-muted)">
        <TableHeaderCell>{{ t('traffic.stationLeaderboard.station') }}</TableHeaderCell>
        <TableHeaderCell>{{ t('traffic.totals.ingress') }}</TableHeaderCell>
        <TableHeaderCell>{{ t('traffic.totals.egress') }}</TableHeaderCell>
        <TableHeaderCell>{{ t('traffic.totals.requests') }}</TableHeaderCell>
      </tr>
      </thead>
      <tbody>
      <tr v-for="entry in entries"
          :key="entry.stationId ?? 'global'"
          class="border-t border-(--border)">
        <td class="py-1 pr-3 font-mono">{{ stationLabel(entry.stationId) }}</td>
        <td class="py-1 pr-3 whitespace-nowrap">{{ formatBytes(entry.ingressBytes) }}</td>
        <td class="py-1 pr-3 whitespace-nowrap">{{ formatBytes(entry.egressBytes) }}</td>
        <td class="py-1 pr-3 whitespace-nowrap">{{ n(entry.requests) }}</td>
      </tr>
      </tbody>
    </table>
    <MutedText v-else tag="div" size="sm">{{ t('traffic.noData') }}</MutedText>
  </NeutralContainer>
</template>
