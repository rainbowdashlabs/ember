/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import EmptyState from '@/components/feedback/EmptyState.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import type {DocumentTemplateSummary} from '@/api/generated/schema'
import type {DataTableApi} from '@/composables/useDataTable'

/** The templates of the station, each name opening its editor. */
defineProps<{
  table: DataTableApi<DocumentTemplateSummary>
}>()

const {t} = useI18n()
</script>

<template>
  <RecordTable :table="table" test-id="document-templates" row-test-id="document-template-row">
    <template #cell-name="{row}">
      <RowLink :to="{name: 'member-document-template-edit', params: {id: row.id}}">
        <span class="font-semibold">{{ row.name }}</span>
      </RowLink>
    </template>
    <template #empty>
      <EmptyState>{{ t('documentTemplates.empty') }}</EmptyState>
    </template>
  </RecordTable>
</template>
