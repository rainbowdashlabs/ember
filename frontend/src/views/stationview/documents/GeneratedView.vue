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
import {RequestState, type GeneratedDocumentEntry} from '@/api/generated/schema'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import SignatureAskStep from '@/components/documents/SignatureAskStep.vue'
import SignatureStateBadge from '@/components/documents/SignatureStateBadge.vue'
import SignatureRequestPanel from '@/components/documents/signatures/SignatureRequestPanel.vue'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useDataTable} from '@/composables/useDataTable'
import {generatedColumns} from './generatedview/generatedColumns'

/**
 * Every document the station generated from a template: who has which document, from which template
 * and which version of it, and who generated it when. A template change never rewrites a document, so
 * the version says which wording a member holds.
 *
 * <p>Each row says how the signatures asked for on the document stand. A row opens on click: before anybody
 * was asked, who the document asks to sign and what they confirm, with the button that asks them; after,
 * each field with its signer and the act that signed it, where a manager settles what no signing act will
 * and asks anew on a corrected document. A withdrawn request opens on asking again.
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

const opened = ref<GeneratedDocumentEntry | null>(null)

function openEntry(entry: GeneratedDocumentEntry) {
  if (entry.documentId !== null || entry.signature) opened.value = entry
}

/** The request the opened document's signatures stand on, unless it was withdrawn and can be asked anew. */
const managedRequest = computed(() => {
  const signature = opened.value?.signature
  if (!signature || signature.state === RequestState.WITHDRAWN) return null
  return signature.requestUid
})

/** Shows a change to the signatures in the list, reading the pages loaded so far again. */
const refreshing = useAsyncAction(async () => {
  const fresh: GeneratedDocumentEntry[] = []
  let page: GeneratedDocumentEntry[]
  do {
    page = await documentTemplates.generationLog(PAGE_SIZE, fresh.length)
    fresh.push(...page)
  } while (page.length === PAGE_SIZE && fresh.length < entries.value.length)
  entries.value = fresh
})

function asked() {
  opened.value = null
  void refreshing.run()
}

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
      <RecordTable v-else :table="table" test-id="generated-documents" row-test-id="generated-document-row"
                   clickable @row-click="openEntry">
        <template #cell-signature="{row}">
          <SignatureStateBadge v-if="row.signature" :summary="row.signature"/>
        </template>
        <template #empty>
          <EmptyState>{{ t('generatedDocuments.empty') }}</EmptyState>
        </template>
      </RecordTable>
      <FailureAlert :failure="loadingMore.failure.value ?? refreshing.failure.value"/>
      <SecondaryButton
          v-if="!loading && more"
          :disabled="loadingMore.running.value"
          data-testid="generated-documents-more"
          @click="loadingMore.run()"
      >
        {{ t('generatedDocuments.loadMore') }}
      </SecondaryButton>
    </div>
    <Modal :model-value="opened !== null" size="lg" @update:model-value="open => { if (!open) opened = null }">
      <SignatureRequestPanel v-if="managedRequest" :request-uid="managedRequest" :on-changed="refreshing.run"/>
      <SignatureAskStep v-else-if="opened" :generation-id="opened.id" @done="asked"/>
    </Modal>
  </ViewContent>
</template>
