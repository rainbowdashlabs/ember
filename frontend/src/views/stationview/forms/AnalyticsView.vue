/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import AnalyticsHeader from '@/views/stationview/forms/analyticsview/AnalyticsHeader.vue'
import AnalyticsTabs from '@/views/stationview/forms/analyticsview/AnalyticsTabs.vue'
import ExportModal from '@/views/stationview/forms/analyticsview/ExportModal.vue'
import MissingResponsesPanel from '@/views/stationview/forms/analyticsview/MissingResponsesPanel.vue'
import ResultFilterBar from '@/views/stationview/forms/analyticsview/ResultFilterBar.vue'
import {useResultView} from '@/views/stationview/forms/analyticsview/useResultView'
import {FormAnalyticsBase, FormPurpose, type Form, type FormAnalytics, type FormAnalyticsBaseName, type FormAnswer, type FormResponse} from '@/api/forms'
import type { ProfileField } from '@/api/profileFields'
import { forms, profileFields, stationMembers } from '@/api'
import { presentFile } from '@/util/documentFile'
import type { ExportFormat, ExportSeparator } from '@/util/exportFormat'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const analyticsBase = computed<FormAnalyticsBaseName>(() => {
  const meta = route.meta?.formAnalyticsBase as FormAnalyticsBaseName | undefined
  return meta ?? FormAnalyticsBase.FORMS
})

const formId = computed(() => Number(route.params.id))
const form = ref<Form | null>(null)
const analytics = ref<FormAnalytics | null>(null)
const responses = ref<FormResponse[]>([])
const memberNames = ref<Map<number, string>>(new Map())
const memberAccountIds = ref<Map<number, number>>(new Map())

/** Only an internal form is answered by signed-in members, so only its results can be grouped by them. */
const groupable = computed(() => analyticsBase.value === FormAnalyticsBase.FORMS && form.value?.purpose === FormPurpose.INTERNAL)
const view = useResultView(formId, groupable)

/** What the charts, the missing members and the individual answers show: the narrowed view if any. */
const shown = computed(() => view.narrowed.value ?? analytics.value)
const visibleResponses = computed(() => {
  const narrowed = view.narrowed.value
  if (!narrowed) return responses.value
  const ids = new Set(narrowed.responseIds)
  return responses.value.filter(response => ids.has(response.id))
})
const groupNames = computed(() => (view.narrowed.value?.groups ?? []).map(view.groupName))

const currentResponseIndex = ref(0)
const currentAnswers = ref<FormAnswer[]>([])
const loadingResponse = ref(false)

const currentResponse = computed(() => visibleResponses.value[currentResponseIndex.value] ?? null)

watch(visibleResponses, () => {
  currentResponseIndex.value = 0
  void loadResponseAnswers()
})

async function loadResponseAnswers() {
  if (!currentResponse.value) return
  loadingResponse.value = true
  try {
    const detail = await forms.getResponseDetail(formId.value, currentResponse.value.id, analyticsBase.value)
    currentAnswers.value = detail.answers
  } catch (e) { void e }
  loadingResponse.value = false
}

function prevResponse() {
  if (currentResponseIndex.value > 0) {
    currentResponseIndex.value--
    loadResponseAnswers()
  }
}

function nextResponse() {
  if (currentResponseIndex.value < visibleResponses.value.length - 1) {
    currentResponseIndex.value++
    loadResponseAnswers()
  }
}

function getAnswerForQuestion(questionId: number): string {
  const answer = currentAnswers.value.find(a => a.questionId === questionId)
  return answer?.value ?? ''
}

const showExportModal = ref(false)
const allFields = ref<ProfileField[]>([])
const exportQuestionIds = ref<Set<number>>(new Set())
const exportFieldIds = ref<Set<number>>(new Set())

function openExportModal() {
  if (analytics.value) {
    exportQuestionIds.value = new Set(analytics.value.questions.map(q => q.questionId))
  }
  exportFieldIds.value = new Set()
  showExportModal.value = true
}

function toggleExportQuestion(id: number) {
  const s = new Set(exportQuestionIds.value)
  if (s.has(id)) s.delete(id); else s.add(id)
  exportQuestionIds.value = s
}

function toggleExportField(id: number) {
  const s = new Set(exportFieldIds.value)
  if (s.has(id)) s.delete(id); else s.add(id)
  exportFieldIds.value = s
}

function selectExportQuestions(ids: number[]) {
  exportQuestionIds.value = new Set(ids)
}

/**
 * Asks the server for the answers, printed or as a spreadsheet.
 *
 * <p>They used to be assembled here, which made this the one export with no server side at all and
 * the one list that could not be printed. Both formats now come from the same rows as everything
 * else.
 */
async function performExport(format: ExportFormat, separator: ExportSeparator) {
  if (!formId.value) return
  showExportModal.value = false
  await presentFile(await forms.exportResponses(formId.value, format, separator))
}

const { loading, error } = useAsyncLoader(async () => {
  const [f, a, r, fields, members] = await Promise.all([
    forms.getForm(formId.value),
    forms.getAnalytics(formId.value, analyticsBase.value),
    forms.listResponses(formId.value, analyticsBase.value),
    profileFields.listFields(),
    analyticsBase.value === FormAnalyticsBase.FORMS
      ? stationMembers.listMembers()
      : Promise.resolve([] as Awaited<ReturnType<typeof stationMembers.listMembers>>),
  ])
  form.value = f
  analytics.value = a
  responses.value = r
  allFields.value = fields

  const names = new Map<number, string>()
  const accountIds = new Map<number, number>()
  for (const m of members) {
    names.set(m.id, m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`)
    if (m.accountId) accountIds.set(m.id, m.accountId)
  }
  memberNames.value = names
  memberAccountIds.value = accountIds

  await view.loadChoices()
  await view.refresh()

  if (r.length > 0) {
    await loadResponseAnswers()
  }
})
</script>

<template>
  <ViewContent
      :title="t('pages.forms-analytics.title')"
      :subtitle="t('pages.forms-analytics.subtitle')"
  >
    <div class="space-y-6 max-w-4xl">
      <Spinner v-if="loading" size="lg" />
      <FailureAlert :message="error"/>

      <template v-if="!loading && form && analytics">
        <AnalyticsHeader
          :title="form.title"
          :total-responses="analytics.totalResponses"
          @export="openExportModal"
          @back="router.push({ name: 'forms-list' })"
        />

        <ResultFilterBar
          v-if="groupable && analytics.totalResponses > 0"
          v-model:filter="view.filter.value"
          v-model:grouping="view.grouping.value"
          :groups="view.groups.value"
          :tags="view.tags.value"
          :fields="view.groupableFields.value"
          :matching="view.narrowed.value ? view.narrowed.value.totalResponses : null"
          :querying="view.querying.value"
          @reset="view.reset"
        />

        <MissingResponsesPanel
          v-if="form.forced && shown && shown.missingResponses.length > 0"
          :members="shown.missingResponses"
        />

        <EmptyState v-if="analytics.totalResponses === 0">{{ t('forms.analytics.noResponses') }}</EmptyState>

        <AnalyticsTabs
          v-else-if="shown"
          :results="shown"
          :grouped="!!(view.grouping.value && view.narrowed.value)"
          :names="groupNames"
          :series="view.series.value"
          :responses="visibleResponses"
          :current-response="currentResponse"
          :current-response-index="currentResponseIndex"
          :loading-response="loadingResponse"
          :get-answer-for-question="getAnswerForQuestion"
          @prev="prevResponse"
          @next="nextResponse"
        />
      </template>
    </div>

    <ExportModal
      v-model="showExportModal"
      :questions="analytics?.questions ?? []"
      :fields="allFields"
      :selected-question-ids="exportQuestionIds"
      :selected-field-ids="exportFieldIds"
      @toggle-question="toggleExportQuestion"
      @toggle-field="toggleExportField"
      @select-questions="selectExportQuestions"
      @export="performExport"
    />
  </ViewContent>
</template>
