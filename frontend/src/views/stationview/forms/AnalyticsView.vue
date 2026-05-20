/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { BarChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import type { Form, FormAnalytics, FormQuestionAnalytics, FormResponse, FormAnswer, ProfileField } from '@/api/types'
import { QuestionTypes } from '@/api/types'
import { forms, profileFields, stationMembers } from '@/api'
import { useStations } from '@/composables/useStations'
import MemberName from '@/components/avatar/MemberName.vue'

use([CanvasRenderer, BarChart, PieChart, TitleComponent, TooltipComponent, LegendComponent, GridComponent])

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { currentStationId } = useStations()

const formId = computed(() => Number(route.params.id))
const loading = ref(true)
const error = ref('')
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

// Individual response browsing
const currentResponseIndex = ref(0)
const currentAnswers = ref<FormAnswer[]>([])
const loadingResponse = ref(false)

const currentResponse = computed(() => responses.value[currentResponseIndex.value] ?? null)
const currentMemberName = computed(() =>
    currentResponse.value ? (memberNames.value.get(currentResponse.value.memberId) ?? `#${currentResponse.value.memberId}`) : ''
)

async function loadResponseAnswers() {
  if (!currentResponse.value) return
  loadingResponse.value = true
  try {
    const detail = await forms.getResponseDetail(formId.value, currentResponse.value.id)
    currentAnswers.value = detail.answers
  } catch { /* ignore */ }
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

function formatAnswerDisplay(questionType: string, config: string, value: string): string {
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

// Export
const showExportModal = ref(false)
const allFields = ref<ProfileField[]>([])
const exportQuestionIds = ref<Set<number>>(new Set())
const exportFieldIds = ref<Set<number>>(new Set())

function openExportModal() {
  // Pre-select all questions
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

  // Load profile values for selected fields if needed
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

  // Load all answers for export
  const allResponseAnswers = new Map<number, FormAnswer[]>()
  for (const resp of responses.value) {
    try {
      const detail = await forms.getResponseDetail(formId.value, resp.id)
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

  // Build header
  const headerCols: string[] = [t('forms.analytics.exportMember')]
  const selectedQuestions = analytics.value.questions.filter(q => exportQuestionIds.value.has(q.questionId))
  for (const q of selectedQuestions) headerCols.push(q.title)
  const selectedFields = allFields.value.filter(f => exportFieldIds.value.has(f.id))
  for (const f of selectedFields) headerCols.push(f.name ?? '')

  // Build rows
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

// Charts
function parseConfig(config: string): Record<string, unknown> {
  try { return JSON.parse(config || '{}') } catch { return {} }
}

function parseValue(value: string): Record<string, unknown> {
  try { return JSON.parse(value || '{}') } catch { return {} }
}

function buildChoiceChart(q: FormQuestionAnalytics) {
  const cfg = parseConfig(q.config)
  const options = (cfg.options as string[]) || []
  const counts = new Array(options.length).fill(0)
  let otherCount = 0
  for (const v of q.values) {
    const parsed = parseValue(v) as { selected?: number[]; other?: string }
    if (parsed.selected) { for (const idx of parsed.selected) { if (idx < counts.length) counts[idx]++ } }
    if (parsed.other) otherCount++
  }
  const data = options.map((opt, i) => ({ name: opt, value: counts[i] }))
  if (cfg.allowOther && otherCount > 0) data.push({ name: 'Sonstiges', value: otherCount })
  return { tooltip: { trigger: 'item' }, series: [{ type: 'pie', radius: ['30%', '70%'], data, emphasis: { itemStyle: { shadowBlur: 10 } } }] }
}

function buildRatingChart(q: FormQuestionAnalytics) {
  const cfg = parseConfig(q.config)
  const scale = (cfg.scale as number) || 5
  const counts = new Array(scale).fill(0)
  for (const v of q.values) {
    const parsed = parseValue(v) as { rating?: number }
    if (parsed.rating && parsed.rating >= 1 && parsed.rating <= scale) counts[parsed.rating - 1]++
  }
  return { tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: Array.from({ length: scale }, (_, i) => String(i + 1)) }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: counts, itemStyle: { color: '#FF6421' } }] }
}

function buildRankingChart(q: FormQuestionAnalytics) {
  const cfg = parseConfig(q.config)
  const options = (cfg.options as string[]) || []
  const scores = new Array(options.length).fill(0)
  for (const v of q.values) {
    const parsed = parseValue(v) as { order?: number[] }
    if (parsed.order) { for (let rank = 0; rank < parsed.order.length; rank++) { scores[parsed.order[rank]] += (parsed.order.length - rank) } }
  }
  return { tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: options }, yAxis: { type: 'value' }, series: [{ type: 'bar', data: scores, itemStyle: { color: '#3694FF' } }] }
}

function buildLikertChart(q: FormQuestionAnalytics) {
  const cfg = parseConfig(q.config)
  const statements = (cfg.statements as string[]) || []
  const scaleMin = (cfg.scaleMin as number) || 1
  const scaleMax = (cfg.scaleMax as number) || 5
  const avgScores = new Array(statements.length).fill(0)
  const counts = new Array(statements.length).fill(0)
  for (const v of q.values) {
    const parsed = parseValue(v) as { ratings?: Record<string, number> }
    if (parsed.ratings) { for (const [si, rating] of Object.entries(parsed.ratings)) { const idx = Number(si); if (idx < statements.length) { avgScores[idx] += rating; counts[idx]++ } } }
  }
  const data = statements.map((stmt, i) => ({ name: stmt || `Option ${i + 1}`, value: counts[i] > 0 ? Math.round((avgScores[i] / counts[i]) * 10) / 10 : 0 }))
  return { tooltip: { trigger: 'axis' }, xAxis: { type: 'category', data: data.map(d => d.name) }, yAxis: { type: 'value', min: scaleMin, max: scaleMax }, series: [{ type: 'bar', data: data.map(d => d.value), itemStyle: { color: '#73CEFF' } }] }
}

function getTextResponses(q: FormQuestionAnalytics): string[] {
  return q.values.map(v => (parseValue(v) as { text?: string }).text).filter((t): t is string => !!t)
}

async function loadData() {
  loading.value = true
  try {
    const [f, a, r, fields, members] = await Promise.all([
      forms.getForm(formId.value),
      forms.getAnalytics(formId.value),
      forms.listResponses(formId.value),
      profileFields.listFields(),
      stationMembers.listMembers(currentStationId.value!),
    ])
    form.value = f
    analytics.value = a
    responses.value = r
    allFields.value = fields

    // Build member name map
    const names = new Map<number, string>()
    const accountIds = new Map<number, number>()
    for (const m of members) {
      names.set(m.id, m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`)
      if (m.accountId) accountIds.set(m.id, m.accountId)
    }
    memberNames.value = names
    memberAccountIds.value = accountIds

    // Load first response answers if available
    if (r.length > 0) {
      await loadResponseAnswers()
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6 max-w-4xl">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && form && analytics">
        <div class="flex items-center justify-between">
          <div>
            <h2 class="text-xl font-semibold">{{ form.title }}</h2>
            <p class="text-(--text-muted) text-sm">
              {{ t('forms.analytics.totalResponses') }}: {{ analytics.totalResponses }}
            </p>
          </div>
          <div class="flex gap-2">
            <SecondaryButton @click="openExportModal">
              <font-awesome-icon :icon="['fas', 'file-export']" class="mr-1" />
              {{ t('forms.analytics.export') }}
            </SecondaryButton>
            <SecondaryButton @click="router.push({ name: 'forms-list' })">{{ t('common.back') }}</SecondaryButton>
          </div>
        </div>

        <div v-if="analytics.totalResponses === 0" class="text-center text-(--text-muted) py-8">
          {{ t('forms.analytics.noResponses') }}
        </div>

        <template v-else>
          <TabBar v-model="activeTab" :tabs="tabs" />

          <!-- Charts Tab -->
          <div v-if="activeTab === 'charts'" class="space-y-6">
            <NeutralContainer v-for="q in analytics.questions" :key="q.questionId">
              <div class="space-y-3">
                <h3 class="font-medium">{{ q.title }}</h3>
                <p class="text-xs text-(--text-muted)">{{ q.values.length }} {{ t('forms.responses') }}</p>
                <VChart v-if="q.questionType === QuestionTypes.CHOICE" :option="buildChoiceChart(q)" autoresize style="height: 250px" />
                <VChart v-if="q.questionType === QuestionTypes.RATING" :option="buildRatingChart(q)" autoresize style="height: 200px" />
                <VChart v-if="q.questionType === QuestionTypes.RANKING" :option="buildRankingChart(q)" autoresize style="height: 250px" />
                <VChart v-if="q.questionType === QuestionTypes.LIKERT" :option="buildLikertChart(q)" autoresize style="height: 250px" />
                <div v-if="q.questionType === QuestionTypes.TEXT" class="space-y-1 max-h-60 overflow-y-auto">
                  <div v-for="(text, i) in getTextResponses(q)" :key="i"
                       class="text-sm px-3 py-2 rounded border border-bg-light-accent/50 dark:border-bg-dark-accent/50">{{ text }}</div>
                </div>
                <div v-if="q.questionType === QuestionTypes.DATE" class="space-y-1">
                  <div v-for="(v, i) in q.values" :key="i" class="text-sm text-(--text-muted)">
                    {{ (parseValue(v) as { date?: string }).date || '–' }}
                  </div>
                </div>
              </div>
            </NeutralContainer>
          </div>

          <!-- Individual Responses Tab -->
          <div v-if="activeTab === 'individual'" class="space-y-4">
            <div class="flex items-center justify-between">
              <span class="text-sm text-(--text-muted)">
                {{ t('forms.analytics.responseOf', { current: currentResponseIndex + 1, total: responses.length }) }}
              </span>
              <div class="flex items-center gap-2">
                <IconButton :icon="['fas', 'chevron-left']" :label="'Previous'" :disabled="currentResponseIndex === 0"
                            class="text-(--text-muted) hover:text-primary" @click="prevResponse" />
                <IconButton :icon="['fas', 'chevron-right']" :label="'Next'" :disabled="currentResponseIndex === responses.length - 1"
                            class="text-(--text-muted) hover:text-primary" @click="nextResponse" />
              </div>
            </div>

            <NeutralContainer v-if="currentResponse">
              <div class="space-y-1 mb-4">
                <p class="font-medium"><MemberName :name="currentMemberName" :member-id="currentResponse?.memberId"/></p>
                <p class="text-xs text-(--text-muted)">{{ new Date(currentResponse.submittedAt).toLocaleString('de-DE') }}</p>
                <p v-if="currentResponse.submittedByName" class="text-xs text-(--text-muted) italic">{{ t('common.submittedBy', { name: currentResponse.submittedByName }) }}</p>
              </div>

              <Spinner v-if="loadingResponse" size="sm" />
              <div v-else class="space-y-4">
                <div v-for="q in analytics.questions" :key="q.questionId"
                     class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50 pb-3 last:border-0">
                  <p class="text-xs text-(--text-muted) mb-1">{{ q.title }}</p>
                  <p class="text-sm font-medium">
                    {{ formatAnswerDisplay(q.questionType, q.config, getAnswerForQuestion(q.questionId)) }}
                  </p>
                </div>
              </div>
            </NeutralContainer>
          </div>
        </template>
      </template>

      <!-- Export Modal -->
      <Modal v-model="showExportModal">
        <div class="space-y-4">
          <h3 class="text-lg font-semibold">{{ t('forms.analytics.export') }}</h3>

          <!-- Question selection -->
          <div class="space-y-2">
            <div class="flex items-center justify-between">
              <label class="text-sm font-medium">{{ t('forms.analytics.exportQuestions') }}</label>
              <div class="flex gap-2 text-xs">
                <SecondaryButton class="text-xs !px-2 !py-0.5" @click="selectAllQuestions">{{ t('forms.analytics.selectAll') }}</SecondaryButton>
                <SecondaryButton class="text-xs !px-2 !py-0.5" @click="selectNoQuestions">{{ t('forms.analytics.selectNone') }}</SecondaryButton>
              </div>
            </div>
            <div class="max-h-40 overflow-y-auto space-y-1 border rounded border-bg-light-accent dark:border-bg-dark-accent p-2">
              <label v-for="q in analytics?.questions" :key="q.questionId"
                     class="flex items-center gap-2 text-sm cursor-pointer py-0.5">
                <input type="checkbox" :checked="exportQuestionIds.has(q.questionId)"
                       class="h-4 w-4 rounded accent-primary" @change="toggleExportQuestion(q.questionId)" />
                {{ q.title }}
              </label>
            </div>
          </div>

          <!-- Profile field selection -->
          <div class="space-y-2">
            <label class="text-sm font-medium">{{ t('forms.analytics.exportFields') }}</label>
            <div class="max-h-40 overflow-y-auto space-y-1 border rounded border-bg-light-accent dark:border-bg-dark-accent p-2">
              <label v-for="f in allFields" :key="f.id"
                     class="flex items-center gap-2 text-sm cursor-pointer py-0.5">
                <input type="checkbox" :checked="exportFieldIds.has(f.id)"
                       class="h-4 w-4 rounded accent-primary" @change="toggleExportField(f.id)" />
                {{ f.name }}
              </label>
              <p v-if="allFields.length === 0" class="text-xs text-(--text-muted)">–</p>
            </div>
          </div>

          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showExportModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton @click="performExport" :disabled="exportQuestionIds.size === 0">
              {{ t('forms.analytics.exportCsv') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
