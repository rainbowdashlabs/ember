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
import BulkGenerateModal from '@/components/documents/bulk/BulkGenerateModal.vue'
import {DocumentTemplateKind, StationPermission, type DocumentTemplateSummary} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useDataTable} from '@/composables/useDataTable'
import {useSession} from '@/composables/useSession'
import TemplateTable from './templatesview/TemplateTable.vue'
import GenerationJobsPanel from './templatesview/GenerationJobsPanel.vue'
import {LAST_USED_COLUMN, templateColumns} from './templatesview/templateColumns'
import type {TemplateScreens} from './templateScreens'
import {useTemplateDuplication} from './useTemplateDuplication'

/**
 * The document templates of a station or an association: letters written in Ember and uploaded PDFs
 * filled in place. An archived template generates nothing more and stays for the documents it made; the
 * switch lists those instead of the ones in use. A station's list also holds the templates of its
 * association, marked as such, which it uses but does not change. The list opens on the templates used
 * last; any of them can be duplicated, and the editor opens on the copy.
 *
 * <p>At a station, whoever may file documents for members also generates a template for many members at
 * once here, and follows those runs below the list. An association has no members to generate for.
 */
const props = defineProps<{
  screens: TemplateScreens
}>()

const {t} = useI18n()
const router = useRouter()
const {hasPermission} = useSession()

const canGenerate = computed(() => props.screens.hasMembers && hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER))
const bulkOpen = ref(false)
const jobsPanel = ref<InstanceType<typeof GenerationJobsPanel> | null>(null)

const showArchived = ref(false)
const templates = ref<DocumentTemplateSummary[]>([])

const {loading, failure, reload} = useAsyncLoader(async () => {
  templates.value = await props.screens.source.list(showArchived.value)
})

watch(showArchived, () => reload())

const table = useDataTable<DocumentTemplateSummary>({
  id: 'document-templates',
  rows: templates,
  columns: computed(() => templateColumns(t, props.screens.useRoute !== null)),
  rowKey: template => template.id,
  sort: {key: LAST_USED_COLUMN, direction: 'desc'},
})

const duplication = useTemplateDuplication(props.screens)

function create(kind?: DocumentTemplateKind) {
  router.push({name: props.screens.editRoute, params: {id: 'new'}, query: kind ? {kind} : {}})
}
</script>

<template>
  <ViewContent :title="t(`pages.${screens.listRoute}.title`)" :subtitle="t(`pages.${screens.listRoute}.subtitle`)">
    <div class="space-y-4">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <ButtonRow>
          <PrimaryButton :icon="['fas', 'plus']" data-testid="template-new" @click="create()">
            {{ t('documentTemplates.create') }}
          </PrimaryButton>
          <SecondaryButton :icon="['fas', 'file-pdf']" data-testid="template-new-pdf" @click="create(DocumentTemplateKind.PDF)">
            {{ t('documentTemplates.createPdf') }}
          </SecondaryButton>
          <SecondaryButton v-if="canGenerate" :icon="['fas', 'users']" data-testid="template-bulk"
                           @click="bulkOpen = true">
            {{ t('documentTemplates.bulk.open') }}
          </SecondaryButton>
        </ButtonRow>
        <div class="flex flex-wrap items-center gap-3">
          <ToggleSetting v-model="showArchived" :label="t('documentTemplates.showArchived')"/>
          <TableColumnPicker :table="table"/>
        </div>
      </div>
      <FailureAlert :failure="failure ?? duplication.failure.value"/>
      <Spinner v-if="loading" size="lg"/>
      <TemplateTable v-else :table="table" :screens="screens" :duplicating="duplication.running.value"
                     @duplicate="duplication.run"/>
      <GenerationJobsPanel v-if="canGenerate" ref="jobsPanel"/>
    </div>
    <BulkGenerateModal v-if="bulkOpen" v-model="bulkOpen" @started="jobsPanel?.reload()"/>
  </ViewContent>
</template>
