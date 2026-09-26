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
import { useSession } from '@/composables/useSession'
import { StationPermission } from '@/api/types'
import ViewContent from '@/components/layout/ViewContent.vue'
import FormShareLink from '@/components/public/FormShareLink.vue'
import AnalyticsBody from '@/views/stationview/forms/analyticsview/AnalyticsBody.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import AnalyticsHeader from '@/views/stationview/forms/analyticsview/AnalyticsHeader.vue'
import ExportModal from '@/views/stationview/forms/analyticsview/ExportModal.vue'
import {useResultView} from '@/views/stationview/forms/analyticsview/useResultView'
import {FormAnalyticsBase, FormPurpose, type Form, type FormAnalytics, type FormAnalyticsBaseName, type FormAnswer, type FormResponse} from '@/api/forms'
import type { ProfileField } from '@/api/profileFields'
import { forms, profileFields, stationMembers } from '@/api'
import { describeFailure, type Failure } from '@/util/failure'
import { presentFile } from '@/util/documentFile'
import type { ExportFormat, ExportSeparator } from '@/util/exportFormat'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { hasPermission } = useSession()

const analyticsBase = computed<FormAnalyticsBaseName>(() => {
  const meta = route.meta?.formAnalyticsBase as FormAnalyticsBaseName | undefined
  return meta ?? FormAnalyticsBase.FORMS
})

/**
 * The list this form was opened from, which is the one going back leads to.
 *
 * <p>The same results screen serves the internal forms and the polls put on a public page, and it
 * used to lead back to the internal list from both, so leaving the results of a public poll landed
 * on a list that does not hold it.
 */
const listRoute = computed(() =>
    analyticsBase.value === FormAnalyticsBase.PAGE_POLLS ? 'pages-polls' : 'forms-list')

/**
 * Where writing this form happens, which is the screen beside the one the results are on. Reading
 * the answers is most of what makes somebody want to change the question.
 */
const editRoute = computed(() =>
    analyticsBase.value === FormAnalyticsBase.PAGE_POLLS ? 'pages-polls-edit' : 'forms-edit')

const canEdit = computed(() => hasPermission(StationPermission.POLL_CREATE))

/** A form answered from outside carries a link, and the results are where somebody goes looking for it. */
const sendable = computed(() => form.value !== null && form.value.purpose !== FormPurpose.INTERNAL)

const formId = computed(() => Number(route.params.id))
const form = ref<Form | null>(null)

/**
 * Whose results these are, with the part after the name, so the results of two forms open at once
 * are two tabs that say which is which.
 */
const pageTitle = computed(() => form.value
    ? t('pages.forms-analytics.titleNamed', {name: form.value.title})
    : t('pages.forms-analytics.title'))
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

/**
 * Why one response could not be opened.
 *
 * <p>This was swallowed, so a response whose answers would not load looked exactly like a response
 * somebody had left blank. Reading a survey off an empty card that is only empty because the request
 * failed is worse than being told nothing at all.
 */
const responseFailure = ref<Failure | null>(null)

async function loadResponseAnswers() {
  if (!currentResponse.value) return
  loadingResponse.value = true
  responseFailure.value = null
  try {
    const detail = await forms.getResponseDetail(formId.value, currentResponse.value.id, analyticsBase.value)
    currentAnswers.value = detail.answers
  } catch (e) {
    currentAnswers.value = []
    responseFailure.value = {...describeFailure(e, t), message: t('forms.analytics.responseFailed')}
  }
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

const { loading, failure } = useAsyncLoader(async () => {
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
      :title="pageTitle"
      :subtitle="t('pages.forms-analytics.subtitle')"
  >
    <div class="space-y-6 max-w-4xl">
      <Spinner v-if="loading" size="lg" />
      <FailureAlert :failure="failure ?? responseFailure"/>

      <template v-if="!loading && form && analytics">
        <AnalyticsHeader
          :title="form.title"
          :total-responses="analytics.totalResponses"
          :can-edit="canEdit"
          @export="openExportModal"
          @edit="router.push({ name: editRoute, params: { id: formId } })"
          @back="router.push({ name: listRoute })"
        />

        <FormShareLink v-if="sendable && form" :form="form"/>

        <AnalyticsBody
          :form="form"
          :analytics="analytics"
          :shown="shown"
          :view="view"
          :groupable="groupable"
          :group-names="groupNames"
          :visible-responses="visibleResponses"
          :current-response="currentResponse"
          :current-response-index="currentResponseIndex"
          :loading-response="loadingResponse"
          :get-answer-for-question="getAnswerForQuestion"
          :missing="shown ? shown.missingResponses : []"
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
