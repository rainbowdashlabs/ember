/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { publicForms } from '@/api'
import { PublicFormState, type PublicForm, type PublicFormQuestion } from '@/api/publicForms'
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
 * A form is reached in one of two ways and answered identically either way: by the link it was sent
 * with, which names no station because the reader holds nothing else, or by station and form where
 * it sits embedded in a public page. The link wins where both are given.
 *
 * @param stationUid the station the form belongs to, for a form embedded in a page
 * @param publicUid  the form's public identifier; a missing one leaves the form unloaded
 * @param shareToken the link the form was sent with, which reaches it on its own
 * @param preloaded  a form already fetched, which a page rendered on the server has. Given one, this
 *                   fetches nothing: the form is in the page the server sent, and asking for it
 *                   again would cost a second request and leave the page empty until it came back
 */
export function usePublicFormSubmission(
  stationUid: Ref<string | null>,
  publicUid: Ref<string | null>,
  shareToken: Ref<string | null> = ref(null),
  preloaded: Ref<PublicForm | null> = ref(null),
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

  /**
   * Every question starts with an answer of the shape the server expects, so an unanswered one is
   * empty rather than absent. A ranking starts in the order the options were written, since that is
   * what the reader is shown before they move anything.
   */
  function initAnswerDefaults(questions: PublicFormQuestion[]) {
    const defaults: Record<number, Record<string, unknown>> = {}
    for (const q of questions) {
      if (q.questionType === QuestionTypes.CHOICE) defaults[q.id] = {selected: [] as number[], other: ''}
      else if (q.questionType === QuestionTypes.TEXT) defaults[q.id] = {text: ''}
      else if (q.questionType === QuestionTypes.DATE) defaults[q.id] = {date: ''}
      else if (q.questionType === QuestionTypes.RATING) defaults[q.id] = {rating: 0}
      else if (q.questionType === QuestionTypes.RANKING) {
        defaults[q.id] = {order: ((q.config.options as string[]) ?? []).map((_, i) => i)}
      } else if (q.questionType === QuestionTypes.LIKERT) defaults[q.id] = {ratings: {}}
      else defaults[q.id] = {}
    }
    answers.value = defaults
  }

  /** Takes a form that has already been fetched, so the page it is on draws it at once. */
  function seed(data: PublicForm | null) {
    form.value = data
    submitted.value = false
    initAnswerDefaults(data?.questions ?? [])
  }

  if (preloaded.value !== null) seed(preloaded.value)
  watch(preloaded, seed)

  async function load() {
    if (preloaded.value !== null) return
    if (!shareToken.value && (!stationUid.value || !publicUid.value)) {
      form.value = null
      return
    }
    loading.value = true
    loadError.value = ''
    submitted.value = false
    try {
      const data = shareToken.value
        ? await publicForms.getSharedForm(shareToken.value)
        : await publicForms.getPublicForm(stationUid.value as string, publicUid.value as string)
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

  /** A form that is not taking answers shows why and offers nothing to fill in. */
  const open = computed(() => form.value?.state === PublicFormState.OPEN)

  const {running: submitting, error: submitError, run: runSubmit} = useAsyncAction(async () => {
    if (!form.value) return
    const answerMap: Record<number, Record<string, unknown>> = {}
    for (const q of form.value.questions) {
      const value = answers.value[q.id]
      if (value === undefined) continue
      answerMap[q.id] = {type: q.questionType, ...value}
    }
    const payload = {
      answers: answerMap,
      consentVersion: consentVersion.value,
      privacyVersion: privacyVersion.value,
      tosVersion: tosVersion.value,
    }
    if (shareToken.value) {
      await publicForms.submitSharedResponse(shareToken.value, payload)
    } else if (stationUid.value && publicUid.value) {
      await publicForms.submitPublicResponse(stationUid.value, publicUid.value, payload)
    } else {
      return
    }
    submitted.value = true
  }, {
    formatError: (e) => {
      const status = (e as {response?: {status?: number}}).response?.status
      if (status === 409) return t('publicForm.alreadyAnswered')
      if (status === 429) return t('publicForm.rateLimited')
      if (status === 400) return t('publicForm.closedWhileOpen')
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
    open,
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
