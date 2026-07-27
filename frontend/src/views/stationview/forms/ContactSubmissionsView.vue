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
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SubmissionList from './contactsubmissionsview/SubmissionList.vue'
import type {Form, FormResponse, FormAnswer, FormQuestion} from '@/api/types'
import {QuestionTypes} from '@/api/types'
import {forms} from '@/api'
import {FormAnalyticsBase, acknowledgeContactResponse} from '@/api/forms'
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

const questionsById = computed(() => {
    const map = new Map<number, FormQuestion>()
    for (const q of questions.value) map.set(q.id, q)
    return map
})

const {loading, error, reload} = useAsyncLoader(async () => {
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

async function acknowledge(submission: FormResponse) {
    if (submission.acknowledgedAt) return
    const next = new Set(ackInFlight.value)
    next.add(submission.id)
    ackInFlight.value = next
    try {
        await acknowledgeContactResponse(formId.value, submission.id)
        await reload()
    } catch {
        const cleared = new Set(ackInFlight.value)
        cleared.delete(submission.id)
        ackInFlight.value = cleared
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
        :title="t('pages.pages-forms-submissions.title')"
        :subtitle="t('pages.pages-forms-submissions.subtitle')"
    >
        <div class="space-y-6 max-w-3xl">
            <Spinner v-if="loading" size="lg"/>
            <Alert v-if="error" variant="error">{{ error }}</Alert>

            <EmptyState v-if="!loading && submissions.length === 0">
                {{ t('forms.contactSubmissions.empty') }}
            </EmptyState>

            <SubmissionList
                v-if="!loading && submissions.length > 0"
                :submissions="submissions"
                :answers-by-response="answersByResponse"
                :ack-in-flight="ackInFlight"
                :question-title="questionTitle"
                :format-answer="formatAnswer"
                :format-timestamp="formatDateTime"
                @acknowledge="acknowledge"
            />
        </div>
    </ViewContent>
</template>
