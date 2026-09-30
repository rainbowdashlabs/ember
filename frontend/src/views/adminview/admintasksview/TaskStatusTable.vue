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
import type {TaskStatus} from '@/api/adminTasks'
import type {DataTableApi} from '@/composables/useDataTable'
import TaskOutcomeBadge from './TaskOutcomeBadge.vue'

/** The background tasks, each with how its last run went and what the last failure said. */
defineProps<{
  table: DataTableApi<TaskStatus>
}>()

const {t} = useI18n()
</script>

<template>
  <RecordTable :table="table" row-test-id="task-status-entry" test-id="task-status-table">
    <template #cell-name="{text}">
      <span class="font-mono text-sm">{{ text }}</span>
    </template>
    <template #cell-outcome="{row}">
      <TaskOutcomeBadge :outcome="row.outcome"/>
    </template>
    <template #cell-lastFailureMessage="{row}">
      <MutedText v-if="row.lastFailureMessage" :title="row.lastFailureMessage" tag="div" class="max-w-xs truncate">
        {{ row.lastFailureMessage }}
      </MutedText>
    </template>
    <template #empty>
      <EmptyState>{{ t('adminTasks.empty') }}</EmptyState>
    </template>
  </RecordTable>
</template>
