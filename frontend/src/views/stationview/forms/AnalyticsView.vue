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
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import AnalyticsHeader from '@/views/stationview/forms/analyticsview/AnalyticsHeader.vue'
import ChartsTab from '@/views/stationview/forms/analyticsview/ChartsTab.vue'
import IndividualResponseTab from '@/views/stationview/forms/analyticsview/IndividualResponseTab.vue'
import ExportModal from '@/views/stationview/forms/analyticsview/ExportModal.vue'
import type { Form, FormAnalytics, FormResponse, FormAnswer, ProfileField } from '@/api/types'
import { QuestionTypes } from '@/api/types'
import { forms, profileFields, stationMembers } from '@/api'
import { FormAnalyticsBase, type FormAnalyticsBaseName } from '@/api/forms'

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

function parseConfig(config: Record<string, unknown> | string): Record<string, unknown> {
  if (typeof config === 'object' && config !== null) return config
  try { return JSON.parse(config || '{}') } catch { return {} }
}

function parseValue(value: string): Record<string, unknown> {
  try { return JSON.parse(value || '{}') } catch { return {} }
}

function formatAnswerDisplay(questionType: string, config: string | Record<string, unknown>, value: string): string {
  if (!value) return '–'
  const parsed = parseValue(value)
  const cfg = parseConfig(config)

  if (questionType === QuestionTypes.TEXT) return (parsed as { text?: string }).text || '–'
  if (questionType === QuestionTypes.DATE) return (parsed as { date?: string }).date || '–'
  if (questionType === QuestionTypes.RATING) return String((parsed as { rating?: number }).rating ?? '–')
  if (questionType === QuestionTypes.CHOICE) {
    const selected = (parsed as { selected?: number[]; other?: string }).selected ?? []
    const options = (cfg.options as string[]) || []
    const labels = selected.map(i => options[i] ?? `#${i}`)
    const other = (parsed as { other?: string }).other
    if (other) labels.push(`Sonstige: ${other}`)
    return labels.join(', ') || '–'
  }
  if (questionType === QuestionTypes.RANKING) {
    const order = (parsed as { order?: number[] }).order ?? []
    const options = (cfg.options as string[]) || []
    return order.map((idx, rank) => `${rank + 1}. ${options[idx] ?? ''}`).join(', ')
  }
  if (questionType === QuestionTypes.LIKERT) {
    const ratings = (parsed as { ratings?: Record<string, number> }).ratings ?? {}
    const statements = (cfg.statements as string[]) || []
    return Object.entries(ratings)
        .map(([si, val]) => `${statements[Number(si)] || `Option ${Number(si) + 1}`}: ${val}`)
        .join(', ')
  }
  return value
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

function selectAllQuestions() {
  if (analytics.value) exportQuestionIds.value = new Set(analytics.value.questions.map(q => q.questionId))
}
function selectNoQuestions() { exportQuestionIds.value = new Set() }

async function performExport() {
  if (!analytics.value) return
  showExportModal.value = false

  const selectedFieldIds = [...exportFieldIds.value]
  const memberFieldValues = new Map<number, Map<number, string>>()

  if (selectedFieldIds.length > 0) {
    for (const resp of responses.value) {
      if (!memberFieldValues.has(resp.memberId)) {
        try {
          const vals = await profileFields.getValues(resp.memberId)
          const m = new Map<number, string>()
          for (const v of vals) m.set(v.fieldId, v.value ?? '')
          memberFieldValues.set(resp.memberId, m)
        } catch {
          memberFieldValues.set(resp.memberId, new Map())
        }
      }
    }
  }

  const allResponseAnswers = new Map<number, FormAnswer[]>()
  for (const resp of responses.value) {
    try {
      const detail = await forms.getResponseDetail(formId.value, resp.id, analyticsBase.value)
      allResponseAnswers.set(resp.id, detail.answers)
    } catch {
      allResponseAnswers.set(resp.id, [])
    }
  }

  const escapeCsv = (val: string) => {
    if (val.includes(';') || val.includes('"') || val.includes('\n')) {
      return `"${val.replace(/"/g, '""')}"`
    }
    return val
  }

  const headerCols: string[] = [t('forms.analytics.exportMember')]
  const selectedQuestions = analytics.value.questions.filter(q => exportQuestionIds.value.has(q.questionId))
  for (const q of selectedQuestions) headerCols.push(q.title)
  const selectedFields = allFields.value.filter(f => exportFieldIds.value.has(f.id))
  for (const f of selectedFields) headerCols.push(f.name ?? '')

  const rows: string[] = []
  for (const resp of responses.value) {
    const cols: string[] = [memberNames.value.get(resp.memberId) ?? `#${resp.memberId}`]
    const answers = allResponseAnswers.get(resp.id) ?? []

    for (const q of selectedQuestions) {
      const answer = answers.find(a => a.questionId === q.questionId)
      cols.push(formatAnswerDisplay(q.questionType, q.config, answer?.value ?? ''))
    }

    for (const f of selectedFields) {
      const raw = memberFieldValues.get(resp.memberId)?.get(f.id) ?? ''
      try { cols.push(JSON.parse(raw)) } catch { cols.push(raw) }
    }

    rows.push(cols.map(escapeCsv).join(';'))
  }

  const csv = [headerCols.map(escapeCsv).join(';'), ...rows].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${form.value?.title ?? 'formular'}-export.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
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
  <ViewContent>
    <div class="space-y-6 max-w-4xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && form && analytics">
        <AnalyticsHeader
          :title="form.title"
          :total-responses="analytics.totalResponses"
          @export="openExportModal"
          @back="router.push({ name: 'forms-list' })"
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
        @select-all-questions="selectAllQuestions"
        @select-no-questions="selectNoQuestions"
        @export="performExport"
      />
    </div>
  </ViewContent>
</template>
