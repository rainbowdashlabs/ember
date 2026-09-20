/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import AnalyticsHeader from '@/views/stationview/forms/analyticsview/AnalyticsHeader.vue'
import ChartsTab from '@/views/stationview/forms/analyticsview/ChartsTab.vue'
import IndividualResponseTab from '@/views/stationview/forms/analyticsview/IndividualResponseTab.vue'
import ExportModal from '@/views/stationview/forms/analyticsview/ExportModal.vue'
import MissingResponsesPanel from '@/views/stationview/forms/analyticsview/MissingResponsesPanel.vue'
import {FormAnalyticsBase, type Form, type FormAnalytics, type FormAnalyticsBaseName, type FormAnswer, type FormResponse} from '@/api/forms'
import { formatAnswerDisplay } from '@/util/formAnswerDisplay'
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
const activeTab = ref('charts')

const tabs = computed(() => [
  { key: 'charts', label: t('forms.analytics.tabCharts') },
  { key: 'individual', label: t('forms.analytics.tabIndividual') },
])

const currentResponseIndex = ref(0)
const currentAnswers = ref<FormAnswer[]>([])
const loadingResponse = ref(false)

const currentResponse = computed(() => responses.value[currentResponseIndex.value] ?? null)

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
  if (currentResponseIndex.value < responses.value.length - 1) {
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
  presentFile(await forms.exportResponses(formId.value, format, separator))
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

        <MissingResponsesPanel
          v-if="form.forced && analytics.missingResponses.length > 0"
          :members="analytics.missingResponses"
        />

        <EmptyState v-if="analytics.totalResponses === 0">{{ t('forms.analytics.noResponses') }}</EmptyState>

        <template v-else>
          <TabBar v-model="activeTab" :tabs="tabs" />
          <ChartsTab v-if="activeTab === 'charts'" :questions="analytics.questions" />
          <IndividualResponseTab
            v-if="activeTab === 'individual'"
            :responses="responses"
            :current-response="currentResponse"
            :current-response-index="currentResponseIndex"
            :questions="analytics.questions"
            :loading-response="loadingResponse"
            :format-answer="formatAnswerDisplay"
            :get-answer-for-question="getAnswerForQuestion"
            @prev="prevResponse"
            @next="nextResponse"
          />
        </template>
      </template>

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
    </div>
  </ViewContent>
</template>
