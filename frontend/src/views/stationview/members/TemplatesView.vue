/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import TableColumnPicker from '@/components/table/TableColumnPicker.vue'
import {documentTemplates} from '@/api'
import {DocumentTemplateKind, type DocumentTemplateSummary} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useDataTable} from '@/composables/useDataTable'
import TemplateTable from './templatesview/TemplateTable.vue'
import {templateColumns} from './templatesview/templateColumns'

/**
 * The document templates of the station: letters written in Ember and uploaded PDFs filled in place.
 * An archived template generates nothing more and stays for the documents it made; the switch lists
 * those instead of the ones in use.
 */
const {t} = useI18n()
const router = useRouter()

const showArchived = ref(false)
const templates = ref<DocumentTemplateSummary[]>([])

const {loading, failure, reload} = useAsyncLoader(async () => {
  templates.value = await documentTemplates.listTemplates(showArchived.value)
})

watch(showArchived, () => reload())

const table = useDataTable<DocumentTemplateSummary>({
  id: 'document-templates',
  rows: templates,
  columns: computed(() => templateColumns(t)),
  rowKey: template => template.id,
})
</script>

<template>
  <ViewContent :title="t('pages.member-document-templates.title')" :subtitle="t('pages.member-document-templates.subtitle')">
    <div class="space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <ButtonRow>
          <PrimaryButton :icon="['fas', 'plus']" data-testid="template-new"
                         @click="router.push({name: 'member-document-template-edit', params: {id: 'new'}})">
            {{ t('documentTemplates.create') }}
          </PrimaryButton>
          <SecondaryButton :icon="['fas', 'file-pdf']" data-testid="template-new-pdf"
                           @click="router.push({name: 'member-document-template-edit', params: {id: 'new'}, query: {kind: DocumentTemplateKind.PDF}})">
            {{ t('documentTemplates.createPdf') }}
          </SecondaryButton>
        </ButtonRow>
        <div class="flex flex-wrap items-center gap-3">
          <ToggleSetting v-model="showArchived" :label="t('documentTemplates.showArchived')"/>
          <TableColumnPicker :table="table"/>
        </div>
      </div>
      <FailureAlert :failure="failure"/>
      <Spinner v-if="loading" size="lg"/>
      <TemplateTable v-else :table="table"/>
    </div>
  </ViewContent>
</template>
