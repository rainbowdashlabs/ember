/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import { computed, onMounted, ref, watch, type Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { publicForms } from '@/api'
import { PublicFormState } from '@/api/publicForms'
import type { PublicForm, PublicFormQuestion } from '@/api/generated/schema'
import { usePublicAnswers } from '@/composables/usePublicAnswers'
import { presentQuestions } from '@/util/formShuffle'
import { useAsyncAction } from '@/composables/useAsyncAction'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useFormWalk } from '@/composables/useFormWalk'
import { useAnswerBaseline } from '@/composables/useAnswerBaseline'
import { clearFormDraft, readFormDraft, saveFormDraft } from '@/util/formDrafts'
import { FailureKind, type Failure } from '@/util/failure'

/** The refusal of a second answer to a form that takes one per reader. */
const FORM_ALREADY_ANSWERED = 'F-012'

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
  const submitted = ref(false)
  const validationError = ref('')

  const consentAccepted = ref(false)
  const consentVersion = ref('')
  const privacyVersion = ref('')
  const tosVersion = ref('')

  const pages = computed(() => form.value?.pages ?? [])
  const questions = computed(() => form.value?.questions ?? [])
  const walk = useFormWalk(pages, questions, answers)
  const baseline = useAnswerBaseline(answers, walk.path)

  /** The answers every question starts with, which alone are nothing worth keeping. */
  let blankAnswers = ''

  function startBlank(questionList: readonly PublicFormQuestion[]) {
    initAnswerDefaults(questionList)
    blankAnswers = JSON.stringify(answers.value)
  }

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
    startBlank(data?.questions ?? [])
    baseline.settle()
  }

  function shuffleSeeded() {
    if (!form.value) return
    form.value = presented(form.value)
    startBlank(form.value.questions)
    resumeDraft()
  }

  /** Where this form's half-filled answers are kept in the browser: by its link, or by its address. */
  const draftKey = computed(() => (shareToken.value ? `link:${shareToken.value}` : `${stationUid.value}/${publicUid.value}`))

  /** When the half-filled answers the form continues from were kept, or null where it started fresh. */
  const resumedFrom = ref<string | null>(null)

  /**
   * Continues from the answers this browser kept, where there are any, and takes the form as it then
   * stands as the way it opened: nothing is kept again until the reader changes something.
   */
  function resumeDraft() {
    resumedFrom.value = null
    continueKept()
    baseline.settle()
  }

  /** Puts back the kept answers and page. A form that no longer takes answers ends them: they could never be sent. */
  function continueKept() {
    if (!form.value) return
    if (form.value.state !== PublicFormState.OPEN) {
      clearFormDraft(draftKey.value)
      return
    }
    const kept = readFormDraft(draftKey.value)
    if (!kept) return
    for (const question of form.value.questions) {
      const answer = kept.answers[question.id]
      if (answer?.type === question.questionType) answers.value[question.id] = answer
    }
    if (kept.path.length > 0) walk.showAt(kept.path, {})
    resumedFrom.value = new Date(kept.savedAt).toISOString()
  }

  let keeping: ReturnType<typeof setTimeout> | null = null

  function stopKeeping() {
    if (keeping) clearTimeout(keeping)
    keeping = null
  }

  function answeredNothing(): boolean {
    return JSON.stringify(answers.value) === blankAnswers
  }

  /** Whether this visit kept anything, which taking every answer back forgets again. */
  let keptThisVisit = false

  /** Keeps the answers given so far, or forgets them where every answer is back to how it started. */
  function keepDraft() {
    keeping = null
    if (answeredNothing()) {
      forgetDraft()
      return
    }
    saveFormDraft(draftKey.value, {answers: answers.value, path: walk.path.value})
    keptThisVisit = true
  }

  function forgetDraft() {
    clearFormDraft(draftKey.value)
    keptThisVisit = false
  }

  /**
   * Keeps what is filled in so far once the reader pauses, and only once they changed something from
   * how the form opened. Taking every answer back forgets what this visit kept.
   */
  watch([answers, walk.path], () => {
    if (!form.value || submitted.value || form.value.state !== PublicFormState.OPEN) return
    stopKeeping()
    if (baseline.changed()) keeping = setTimeout(keepDraft, 1000)
    else if (keptThisVisit && answeredNothing()) forgetDraft()
  }, {deep: true})

  /** Throws the kept answers away and starts the form from its first page. */
  function startOver() {
    stopKeeping()
    forgetDraft()
    resumedFrom.value = null
    if (form.value) startBlank(form.value.questions)
    walk.restart()
    baseline.settle()
  }

  if (preloaded.value !== null) seed(preloaded.value)
  watch(preloaded, data => {
    seed(data)
    shuffleSeeded()
  })
  onMounted(shuffleSeeded)

  const {loading, failure: loadFailure, reload: fetchForm} = useAsyncLoader(async (isCurrent) => {
    submitted.value = false
    let data: PublicForm
    try {
      data = shareToken.value
        ? await publicForms.getSharedForm(shareToken.value)
        : await publicForms.getPublicForm(stationUid.value as string, publicUid.value as string)
    } catch (e) {
      if (isCurrent()) form.value = null
      throw e
    }
    if (!isCurrent()) return
    form.value = presented(data)
    startBlank(form.value.questions)
    resumeDraft()
  }, {autoLoad: false})

  async function load() {
    if (preloaded.value !== null) return
    if (!shareToken.value && (!stationUid.value || !publicUid.value)) {
      form.value = null
      return
    }
    await fetchForm()
    if (loadFailure.value) loadFailure.value = describeLoadFailure(loadFailure.value)
  }

  /**
   * A form that could not be fetched, in terms somebody without an account can act on.
   *
   * <p>Every failure here used to read as the form not existing, which is the one sentence that sends a
   * reader away for good. Somebody whose connection dropped, or who arrived while the server was down,
   * has a form that is still there and a link that still works, and telling them otherwise costs them
   * the answer. Only the server actually saying it is gone produces that sentence now.
   */
  function describeLoadFailure(described: Failure): Failure {
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
    stopKeeping()
    const payload = {
      answers: answers.value,
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
    stopKeeping()
    forgetDraft()
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
    const known = knownRefusal(described)
    if (!known) return described
    return {
      ...described,
      message: t(`publicForm.${known}`),
      guidance: t(`publicForm.${known}Guidance`),
      reportable: false,
    }
  })

  /**
   * Which of the three refusals this page words itself, if any.
   *
   * <p>An answer already given is told by its code rather than by its status alone, because legal
   * documents that changed while the form stood open answer with the same status, and that reader has
   * answered nothing yet: they have to reload and agree again, which the server's own sentence says.
   */
  function knownRefusal(failure: Failure): string | null {
    if (failure.status === 409 && failure.code === FORM_ALREADY_ANSWERED) return 'alreadyAnswered'
    if (failure.status === 429) return 'rateLimited'
    if (failure.status === 410) return 'closedWhileOpen'
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
    resumedFrom,
    startOver,
  }
}
