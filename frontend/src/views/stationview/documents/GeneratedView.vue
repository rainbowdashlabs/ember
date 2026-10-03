/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import RecordTable from '@/components/table/RecordTable.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import {documentTemplates} from '@/api'
import type {GeneratedDocumentEntry} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useDataTable} from '@/composables/useDataTable'
import {generatedColumns} from './generatedview/generatedColumns'

/**
 * Every document the station generated from a template: who has which document, from which template
 * and which version of it, and who generated it when. A template change never rewrites a document, so
 * the version says which wording a member holds.
 */
const {t} = useI18n()
const entries = ref<GeneratedDocumentEntry[]>([])

const {loading, failure} = useAsyncLoader(async () => {
  entries.value = await documentTemplates.generationLog()
})

const table = useDataTable<GeneratedDocumentEntry>({
  id: 'generated-documents',
  rows: entries,
  columns: computed(() => generatedColumns(t)),
  rowKey: entry => entry.id,
  sort: {key: 'generatedAt', direction: 'desc'},
})
</script>

<template>
  <ViewContent :title="t('pages.documents-generated.title')" :subtitle="t('pages.documents-generated.subtitle')">
    <div class="space-y-4">
      <div class="flex justify-end">
        <TableColumnPicker :table="table"/>
      </div>
      <FailureAlert :failure="failure"/>
      <Spinner v-if="loading" size="lg"/>
      <RecordTable v-else :table="table" test-id="generated-documents" row-test-id="generated-document-row">
        <template #empty>
          <EmptyState>{{ t('generatedDocuments.empty') }}</EmptyState>
        </template>
      </RecordTable>
    </div>
  </ViewContent>
</template>
