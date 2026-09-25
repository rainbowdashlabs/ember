/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubmissionList from './contactsubmissionsview/SubmissionList.vue'
import {acknowledgeContactResponse, FormAnalyticsBase, QuestionTypes, type Form, type FormAnswer, type FormQuestion, type FormResponse} from '@/api/forms'
import {forms} from '@/api'
import {describeFailure, type Failure} from '@/util/failure'
import {formatDateTime} from '@/util/format'

/**
 * Lightweight list view for CONTACT-form submissions. Unlike POLL analytics this surface is
 * intentionally chart-free: contact forms are read as individual messages, sorted by arrival
 * time, with an "Acknowledge" button that records who handled the request and when.
 */
const {t} = useI18n()
const route = useRoute()

const formId = computed(() => Number(route.params.id))
const form = ref<Form | null>(null)
const questions = ref<FormQuestion[]>([])
const submissions = ref<FormResponse[]>([])
const answersByResponse = ref<Map<number, FormAnswer[]>>(new Map())
const ackInFlight = ref<Set<number>>(new Set())

/**
 * Which contact form these came in through, with the part after its name, so a station running
 * several of them can tell one tab of submissions from another.
 */
const pageTitle = computed(() => form.value
    ? t('pages.pages-forms-submissions.titleNamed', {name: form.value.title})
    : t('pages.pages-forms-submissions.title'))

const questionsById = computed(() => {
    const map = new Map<number, FormQuestion>()
    for (const q of questions.value) map.set(q.id, q)
    return map
})

const {loading, failure: loadFailure, reload} = useAsyncLoader(async () => {
    const [f, qs, list] = await Promise.all([
        forms.getForm(formId.value),
        forms.getQuestions(formId.value),
        forms.listResponses(formId.value, FormAnalyticsBase.PAGE_FORMS),
    ])
    form.value = f
    questions.value = qs
    submissions.value = list
    const answersMap = new Map<number, FormAnswer[]>()
    for (const sub of list) {
        try {
            const detail = await forms.getResponseDetail(formId.value, sub.id, FormAnalyticsBase.PAGE_FORMS)
            answersMap.set(sub.id, detail.answers)
        } catch {
            answersMap.set(sub.id, [])
        }
    }
    answersByResponse.value = answersMap
})

/**
 * Marking a message handled, and the list refresh that follows, which are two things and not one.
 *
 * <p>They shared an attempt and the failure was swallowed whole: a message that really was marked,
 * followed by a list that failed to come back, left the tick springing open again with no word about
 * why, and a message that could not be marked at all looked exactly the same. Neither reader learned
 * anything, and both had to guess whether the message was still waiting for somebody.
 */
const ackFailure = ref<Failure | null>(null)

async function acknowledge(submission: FormResponse) {
    if (submission.acknowledgedAt) return
    const next = new Set(ackInFlight.value)
    next.add(submission.id)
    ackInFlight.value = next
    ackFailure.value = null
    try {
        await acknowledgeContactResponse(formId.value, submission.id)
    } catch (e) {
        ackFailure.value = describeFailure(e, t)
        const cleared = new Set(ackInFlight.value)
        cleared.delete(submission.id)
        ackInFlight.value = cleared
        return
    }
    await reload()
    if (loadFailure.value) {
        ackFailure.value = {...loadFailure.value, message: t('failure.staleAfterAction')}
        loadFailure.value = null
    }
}

function questionTitle(answer: FormAnswer): string {
    return questionsById.value.get(answer.questionId)?.title ?? `#${answer.questionId}`
}

function parseValue(value: string): Record<string, unknown> {
    try { return JSON.parse(value || '{}') } catch { return {} }
}

/**
 * Renders a stored {@code FormAnswer.value} (JSON-encoded per the question type) as the bare
 * human-readable form the contact-submission viewer wants. Falls back to the raw value when the
 * question type is unknown so the manager still sees *something* rather than an empty cell.
 */
function formatAnswer(answer: FormAnswer): string {
    const question = questionsById.value.get(answer.questionId)
    const parsed = parseValue(answer.value)
    if (!question) return answer.value
    const cfg = question.config ?? {}
    switch (question.formQuestionType) {
        case QuestionTypes.TEXT:
            return (parsed as {text?: string}).text || '–'
        case QuestionTypes.DATE:
            return (parsed as {date?: string}).date || '–'
        case QuestionTypes.RATING:
            return String((parsed as {rating?: number}).rating ?? '–')
        case QuestionTypes.CHOICE: {
            const selected = (parsed as {selected?: number[]}).selected ?? []
            const options = (cfg.options as string[] | undefined) ?? []
            const labels = selected.map(i => options[i] ?? `#${i}`)
            const other = (parsed as {other?: string}).other
            if (other) labels.push(`Sonstige: ${other}`)
            return labels.join(', ') || '–'
        }
        default:
            return answer.value
    }
}
</script>

<template>
    <ViewContent
        :title="pageTitle"
        :subtitle="t('pages.pages-forms-submissions.subtitle')"
    >
        <div class="space-y-6 max-w-3xl">
            <FailureAlert :failure="ackFailure"/>
            <AsyncSection
                :empty="submissions.length === 0"
                :empty-message="t('forms.contactSubmissions.empty')"
                :failure="loadFailure"
                :loading="loading"
            >
                <SubmissionList
                    :submissions="submissions"
                    :answers-by-response="answersByResponse"
                    :ack-in-flight="ackInFlight"
                    :question-title="questionTitle"
                    :format-answer="formatAnswer"
                    :format-timestamp="formatDateTime"
                    @acknowledge="acknowledge"
                />
            </AsyncSection>
        </div>
    </ViewContent>
</template>
