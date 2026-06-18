/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type {HourlyTrafficRow} from '@/api/traffic'

const props = defineProps<{
  rows: HourlyTrafficRow[]
}>()

const {n} = useI18n()

const totals = computed(() => {
  let ingress = 0
  let egress = 0
  let requests = 0
  for (const row of props.rows) {
    ingress += row.ingressBytes
    egress += row.egressBytes
    requests += row.requests
  }
  return {ingress, egress, requests}
})

function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  const value = bytes / Math.pow(1024, i)
  return `${value.toFixed(value >= 100 ? 0 : 1)} ${units[i]}`
}
</script>

<template>
  <div class="grid grid-cols-1 md:grid-cols-3 gap-3">
    <NeutralContainer class="space-y-1">
      <MutedText tag="div" size="sm">{{ $t('traffic.totals.ingress') }}</MutedText>
      <div class="text-2xl font-semibold">{{ formatBytes(totals.ingress) }}</div>
    </NeutralContainer>
    <NeutralContainer class="space-y-1">
      <MutedText tag="div" size="sm">{{ $t('traffic.totals.egress') }}</MutedText>
      <div class="text-2xl font-semibold">{{ formatBytes(totals.egress) }}</div>
    </NeutralContainer>
    <NeutralContainer class="space-y-1">
      <MutedText tag="div" size="sm">{{ $t('traffic.totals.requests') }}</MutedText>
      <div class="text-2xl font-semibold">{{ n(totals.requests) }}</div>
    </NeutralContainer>
  </div>
</template>
