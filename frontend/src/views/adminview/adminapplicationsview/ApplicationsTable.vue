/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import MutedText from '@/components/typography/MutedText.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import type {StationApplication} from '@/api/stationApplications'
import type {DataTableApi} from '@/composables/useDataTable'
import ApplicationActions from './ApplicationActions.vue'
import ApplicationStatusBadge from './ApplicationStatusBadge.vue'

/** The station applications, each with its introduction under the name and what can be done with it. */
defineProps<{
  table: DataTableApi<StationApplication>
  processing: boolean
}>()

const emit = defineEmits<{
  accept: [app: StationApplication]
  deny: [app: StationApplication]
}>()

const {t} = useI18n()
</script>

<template>
  <RecordTable :table="table" row-test-id="application-entry" test-id="applications-table">
    <template #cell-name="{row, text}">
      <div class="font-medium">{{ text }}</div>
      <MutedText v-if="row.introduction" :title="row.introduction" tag="div" class="mt-0.5 max-w-xs truncate">
        {{ row.introduction }}
      </MutedText>
    </template>
    <template #cell-status="{row}">
      <ApplicationStatusBadge :status="row.status"/>
    </template>
    <template #actions="{row}">
      <ApplicationActions :app="row" :processing="processing" @accept="emit('accept', $event)" @deny="emit('deny', $event)"/>
    </template>
    <template #empty>
      <EmptyState>{{ t('adminApplications.empty') }}</EmptyState>
    </template>
  </RecordTable>
</template>
