/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {BeaconMetricsRow} from '@/api/beacon'

/**
 * The numbers, which are buckets rather than counts.
 *
 * <p>Nothing here names anybody: a row is an identifier used for this and nothing else, so the table
 * shows what there is and how much of it, and cannot show whose.
 */
defineProps<{rows: BeaconMetricsRow[]}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer v-if="rows.length === 0">
    <MutedText tag="div" size="sm">{{ t('beacon.noMetrics') }}</MutedText>
  </NeutralContainer>
  <NeutralContainer v-else class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <tr class="text-left">
          <th class="py-2 pr-4">{{ t('beacon.day') }}</th>
          <th class="py-2 pr-4">{{ t('beacon.subject') }}</th>
          <th class="py-2 pr-4">{{ t('sidebar.members') }}</th>
          <th class="py-2 pr-4">{{ t('sidebar.inventory') }}</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in rows" :key="`${row.metricsUid}-${row.day}`" class="border-t border-bg-light-accent dark:border-bg-dark-accent">
          <td class="py-2 pr-4">{{ row.day }}</td>
          <td class="py-2 pr-4">{{ row.subject }}</td>
          <td class="py-2 pr-4">{{ row.members ?? '-' }}</td>
          <td class="py-2 pr-4">{{ row.inventory ?? '-' }}</td>
        </tr>
      </tbody>
    </table>
  </NeutralContainer>
</template>
