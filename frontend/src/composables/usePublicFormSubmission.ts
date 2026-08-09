/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { publicForms } from '@/api'
import type { PublicForm, PublicFormQuestion } from '@/api/publicForms'
import { QuestionTypes } from '@/api/forms'
import { useAsyncAction } from '@/composables/useAsyncAction'

/**
 * Filling in and submitting a public form, shared by the standalone submission page and the form
 * cell embedded in a public page.
 *
 * Answers are keyed by question and shaped per question type, so an empty answer still has the
 * shape the server expects rather than being absent. Consent is collected here too: a public
 * submission comes from someone with no account, so the versions they agreed to travel with the
 * answers instead of being recorded against a profile.
 *
 * @param stationUid the station the form belongs to
 * @param publicUid  the form's public identifier; a missing one leaves the form unloaded
 */
export function usePublicFormSubmission(
  stationUid: Ref<string | null>,
  publicUid: Ref<string | null>,
) {
  const { t } = useI18n()

  const form = ref<PublicForm | null>(null)
  const answers = ref<Record<number, Record<string, unknown>>>({})
  const loading = ref(false)
  const loadError = ref('')
  const submitted = ref(false)
  const validationError = ref('')

  const consentAccepted = ref(false)
  const consentVersion = ref('')
  const privacyVersion = ref('')
  const tosVersion = ref('')

  function initAnswerDefaults(questions: PublicFormQuestion[]) {
    const defaults: Record<number, Record<string, unknown>> = {}
    for (const q of questions) {
      if (q.questionType === QuestionTypes.CHOICE) defaults[q.id] = {selected: [] as number[], other: ''}
      else if (q.questionType === QuestionTypes.TEXT) defaults[q.id] = {text: ''}
      else if (q.questionType === QuestionTypes.DATE) defaults[q.id] = {date: ''}
      else defaults[q.id] = {}
    }
    answers.value = defaults
  }

  async function load() {
    if (!stationUid.value || !publicUid.value) {
      form.value = null
      return
    }
    loading.value = true
    loadError.value = ''
    submitted.value = false
    try {
      const data = await publicForms.getPublicForm(stationUid.value, publicUid.value)
      form.value = data
      initAnswerDefaults(data.questions)
    } catch {
      form.value = null
      loadError.value = t('publicForm.notFound')
    } finally {
      loading.value = false
    }
  }

  /**
   * Selects an option. A single-select question also clears the free-text "other" answer, since
   * picking a listed option replaces it.
   */
  function toggleChoice(q: PublicFormQuestion, optionIndex: number) {
    const answer = answers.value[q.id] as {selected: number[]; other: string}
    if (!q.config.multiSelect) {
      answer.selected = [optionIndex]
      answer.other = ''
      return
    }
    const existing = answer.selected.indexOf(optionIndex)
    if (existing >= 0) answer.selected.splice(existing, 1)
    else answer.selected.push(optionIndex)
  }

  function updateText(q: PublicFormQuestion, text: string) {
    (answers.value[q.id] as {text: string}).text = text
  }

  function updateDate(q: PublicFormQuestion, date: string) {
    (answers.value[q.id] as {date: string}).date = date
  }

  const {running: submitting, error: submitError, run: runSubmit} = useAsyncAction(async () => {
    if (!form.value || !stationUid.value || !publicUid.value) return
    const answerMap: Record<number, Record<string, unknown>> = {}
    for (const q of form.value.questions) {
      const value = answers.value[q.id]
      if (value === undefined) continue
      answerMap[q.id] = {type: q.questionType, ...value}
    }
    await publicForms.submitPublicResponse(stationUid.value, publicUid.value, {
      answers: answerMap,
      consentVersion: consentVersion.value,
      privacyVersion: privacyVersion.value,
      tosVersion: tosVersion.value,
    })
    submitted.value = true
  }, {
    formatError: (e) => {
      const status = (e as {response?: {status?: number}}).response?.status
      if (status === 409) return t('publicForm.alreadyAnswered')
      if (status === 429) return t('publicForm.rateLimited')
      return t('publicForm.submitError')
    },
  })

  function submit() {
    if (!consentAccepted.value) {
      validationError.value = t('publicConsent.required')
      return
    }
    validationError.value = ''
    void runSubmit()
  }

  const error = computed(() => loadError.value || validationError.value || submitError.value)

  return {
    form,
    answers,
    loading,
    loadError,
    submitted,
    validationError,
    consentAccepted,
    consentVersion,
    privacyVersion,
    tosVersion,
    submitting,
    submitError,
    error,
    load,
    toggleChoice,
    updateText,
    updateDate,
    submit,
  }
}
