/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, onMounted, ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { publicForms } from '@/api'
import { PublicFormState, type PublicForm, type PublicFormQuestion } from '@/api/publicForms'
import type { AnswerValue } from '@/util/formAnswers'
import { usePublicAnswers } from '@/composables/usePublicAnswers'
import { presentQuestions } from '@/util/formShuffle'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useFormWalk } from '@/composables/useFormWalk'
import { describeFailure, FailureKind, type Failure } from '@/util/failure'

/**
 * Filling in and submitting a public form, shared by the standalone submission page and the form
 * cell embedded in a public page.
 *
 * Answers are keyed by question and shaped per question type, so an empty answer still has the
 * shape the server expects rather than being absent. The form is walked one page at a time, the same
 * way on the standalone page and in the cell, and the questions come in the order this reader gets. Consent is collected here too: a public
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
  const {answers, reset: initAnswerDefaults, toggleChoice, updateText, updateDate} = usePublicAnswers()
  const loading = ref(false)
  const loadFailure = ref<Failure | null>(null)
  const submitted = ref(false)
  const validationError = ref('')

  const consentAccepted = ref(false)
  const consentVersion = ref('')
  const privacyVersion = ref('')
  const tosVersion = ref('')

  const pages = computed(() => form.value?.pages ?? [])
  const questions = computed(() => form.value?.questions ?? [])
  const walk = useFormWalk(pages, questions, answers, question => question.questionType)

  /** The form with its questions in the order this reader gets them. */
  function presented(data: PublicForm): PublicForm {
    return {...data, questions: presentQuestions(data.questions ?? [], data.shuffleQuestions)}
  }

  /**
   * Takes a form that has already been fetched, so the page it is on draws it at once.
   *
   * <p>In the order it was written: the server and the browser each draw this page, and two shuffles
   * would draw two different ones. The shuffle comes once the page is up, before anything is typed.
   */
  function seed(data: PublicForm | null) {
    form.value = data
    submitted.value = false
    initAnswerDefaults(data?.questions ?? [])
  }

  function shuffleSeeded() {
    if (!form.value) return
    form.value = presented(form.value)
    initAnswerDefaults(form.value.questions)
  }

  if (preloaded.value !== null) seed(preloaded.value)
  watch(preloaded, data => {
    seed(data)
    shuffleSeeded()
  })
  onMounted(shuffleSeeded)

  async function load() {
    if (preloaded.value !== null) return
    if (!shareToken.value && (!stationUid.value || !publicUid.value)) {
      form.value = null
      return
    }
    loading.value = true
    loadFailure.value = null
    submitted.value = false
    try {
      const data = shareToken.value
        ? await publicForms.getSharedForm(shareToken.value)
        : await publicForms.getPublicForm(stationUid.value as string, publicUid.value as string)
      form.value = presented(data)
      initAnswerDefaults(form.value.questions)
    } catch (e) {
      form.value = null
      loadFailure.value = describeLoadFailure(e)
    } finally {
      loading.value = false
    }
  }

  /**
   * A form that could not be fetched, in terms somebody without an account can act on.
   *
   * <p>Every failure here used to read as the form not existing, which is the one sentence that sends a
   * reader away for good. Somebody whose connection dropped, or who arrived while the server was down,
   * has a form that is still there and a link that still works, and telling them otherwise costs them
   * the answer. Only the server actually saying it is gone produces that sentence now.
   */
  function describeLoadFailure(e: unknown): Failure {
    const described = describeFailure(e, t)
    if (described.kind !== FailureKind.GONE) return described
    return {
      ...described,
      message: t('publicForm.notFound'),
      guidance: t('publicForm.notFoundGuidance'),
      reportable: false,
    }
  }

  /** A form that is not taking answers shows why and offers nothing to fill in. */
  const open = computed(() => form.value?.state === PublicFormState.OPEN)

  const {running: submitting, failure: sendFailure, run: runSubmit} = useAsyncAction(async () => {
    if (!form.value) return
    const answerMap: Record<number, AnswerValue> = {}
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
    try {
      if (shareToken.value) {
        await publicForms.submitSharedResponse(shareToken.value, payload)
      } else if (stationUid.value && publicUid.value) {
        await publicForms.submitPublicResponse(stationUid.value, publicUid.value, payload)
      } else {
        return
      }
    } catch (e) {
      walk.showRefused(e)
      throw e
    }
    submitted.value = true
  })

  /**
   * An answer that could not be sent, and why.
   *
   * <p>Three refusals mean something this page words better than the server does, because it knows the
   * reader holds nothing but a link: the form was already answered, too many answers came in a row, or
   * the form closed while it stood open. Everything else keeps the description it arrived with, since a
   * refusal naming the question that was wrong is worth more than a sentence about sending in general,
   * and a server that fell over is the reader's cue to report rather than to try again differently.
   */
  const submitFailure = computed<Failure | null>(() => {
    const described = sendFailure.value
    if (!described) return null
    const known = knownRefusal(described.status)
    if (!known) return described
    return {
      ...described,
      message: t(`publicForm.${known}`),
      guidance: t(`publicForm.${known}Guidance`),
      reportable: false,
    }
  })

  function knownRefusal(status: number | undefined): string | null {
    if (status === 409) return 'alreadyAnswered'
    if (status === 429) return 'rateLimited'
    if (status === 410) return 'closedWhileOpen'
    return null
  }

  function submit() {
    if (!walk.checkCurrent()) return
    if (!consentAccepted.value) {
      validationError.value = t('publicConsent.required')
      return
    }
    validationError.value = ''
    void runSubmit()
  }

  /**
   * The one failure to show. A form that never loaded is the reason nothing can be sent, so it comes
   * first; the missing consent tick is the reader's own doing and is reported on its own, without the
   * offer to file a bug about it.
   */
  const failure = computed(() => loadFailure.value ?? submitFailure.value)

  return {
    form,
    open,
    answers,
    loading,
    loadFailure,
    submitted,
    validationError,
    consentAccepted,
    consentVersion,
    privacyVersion,
    tosVersion,
    submitting,
    submitFailure,
    failure,
    load,
    toggleChoice,
    updateText,
    updateDate,
    submit,
    walk,
  }
}
