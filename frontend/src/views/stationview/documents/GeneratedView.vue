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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useDataTable} from '@/composables/useDataTable'
import {generatedColumns} from './generatedview/generatedColumns'

/**
 * Every document the station generated from a template: who has which document, from which template
 * and which version of it, and who generated it when. A template change never rewrites a document, so
 * the version says which wording a member holds.
 */
const PAGE_SIZE = 500

const {t} = useI18n()
const entries = ref<GeneratedDocumentEntry[]>([])
const more = ref(false)

async function nextPage(): Promise<GeneratedDocumentEntry[]> {
  const page = await documentTemplates.generationLog(PAGE_SIZE, entries.value.length)
  more.value = page.length === PAGE_SIZE
  return page
}

const {loading, failure} = useAsyncLoader(async () => {
  entries.value = []
  entries.value = await nextPage()
})

const loadingMore = useAsyncAction(async () => {
  entries.value = [...entries.value, ...await nextPage()]
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
      <FailureAlert :failure="loadingMore.failure.value"/>
      <SecondaryButton
          v-if="!loading && more"
          :disabled="loadingMore.running.value"
          data-testid="generated-documents-more"
          @click="loadingMore.run()"
      >
        {{ t('generatedDocuments.loadMore') }}
      </SecondaryButton>
    </div>
  </ViewContent>
</template>
