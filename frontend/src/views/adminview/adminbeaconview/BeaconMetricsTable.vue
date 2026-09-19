/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import type {BeaconMetricsRow} from '@/api/beacon'
import {useDataTable} from '@/composables/useDataTable'
import {beaconMetricColumns} from './beaconMetricColumns'

/**
 * The numbers, which are buckets rather than counts.
 *
 * <p>Nothing here names anybody: a row is an identifier used for this and nothing else, so the table
 * shows what there is and how much of it, and cannot show whose.
 */
const props = defineProps<{rows: BeaconMetricsRow[]}>()

const {t} = useI18n()

const table = useDataTable<BeaconMetricsRow>({
  id: 'admin-beacon-figures',
  perStation: false,
  rows: () => props.rows,
  columns: computed(() => beaconMetricColumns(t)),
  rowKey: row => `${row.metricsUid}-${row.day}`,
})
</script>

<template>
  <NeutralContainer v-if="rows.length === 0">
    <MutedText tag="div" size="sm">{{ t('beacon.noMetrics') }}</MutedText>
  </NeutralContainer>
  <div v-else class="space-y-2">
    <div class="flex justify-end">
      <TableColumnPicker :table="table"/>
    </div>
    <RecordTable :table="table" test-id="beacon-metrics-table"/>
  </div>
</template>
