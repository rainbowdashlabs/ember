/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TrainingQuestionCard from '@/components/quiz/TrainingQuestionCard.vue'
import * as publicQuiz from '@/api/publicQuiz'
import {isQuizQuestionOf, QuizQuestionTypes} from '@/api/quiz'
import type {PublicQuizQuestion, QuizQuestion} from '@/api/generated/schema'
import {moveWithin} from '@/util/reorder'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

/**
 * Public {@code QUIZ_TEASER} renderer. Mirrors the in-app training experience: the visitor
 * answers the question with the full type-specific input (multiple choice, true/false, free
 * text, fill-in-the-blank, ordering, connect, image-text, enumeration) and reveals the
 * correct answer on demand. Each "Next" pull fetches another random question from the
 * configured public catalogs.
 */
const props = defineProps<{
    title?: string | null
    description?: string | null
    catalogIds?: number[] | null
    stationUid?: string | null
}>()

const {t} = useI18n()

const question = ref<PublicQuizQuestion | null>(null)
const showAnswer = ref(false)

const userAnswer = ref('')
const userMcSelections = ref<Set<number>>(new Set())
const userTfAnswer = ref<boolean | null>(null)
const userOrderItems = ref<number[]>([])
const userConnectPairs = ref<Record<string, string>>({})
const userFillGaps = ref<Record<string, string>>({})
const mcDisplayOrder = ref<number[]>([])
const connectRightOrder = ref<number[]>([])

function shuffle<T>(array: T[]): T[] {
    const result = [...array]
    for (let i = result.length - 1; i > 0; i--) {
        const j = Math.floor(Math.random() * (i + 1))
        ;[result[i], result[j]] = [result[j]!, result[i]!]
    }
    return result
}

function resetUserInput() {
    showAnswer.value = false
    userAnswer.value = ''
    userMcSelections.value = new Set()
    userTfAnswer.value = null
    userOrderItems.value = []
    userConnectPairs.value = {}
    userFillGaps.value = {}
    mcDisplayOrder.value = []
    connectRightOrder.value = []
}

function initQuestionState(q: QuizQuestion) {
    if (isQuizQuestionOf(q, QuizQuestionTypes.MULTIPLE_CHOICE)) {
        mcDisplayOrder.value = shuffle((q.config.options ?? []).map((_, i) => i))
    }
    if (isQuizQuestionOf(q, QuizQuestionTypes.ORDERING)) {
        userOrderItems.value = shuffle((q.config.items ?? []).map((_, i) => i))
    } else if (isQuizQuestionOf(q, QuizQuestionTypes.CONNECT)) {
        userConnectPairs.value = {}
        connectRightOrder.value = shuffle((q.config.pairs ?? []).map((_, i) => i))
    }
}

/** The public question in the shape the training card draws, with nothing to score it by. */
function asQuizQuestion(q: PublicQuizQuestion): QuizQuestion {
    return {
        id: q.id,
        catalogId: 0,
        categoryId: null,
        quizQuestionType: q.questionType,
        title: q.title,
        description: q.description,
        imageUrl: q.imageUrl,
        points: 0,
        autoPoints: false,
        config: q.config,
        position: 0,
        createdAt: '',
        updatedAt: '',
    }
}

const {loading, error, reload: fetchQuestion} = useAsyncLoader(async (isCurrent) => {
    let next: PublicQuizQuestion
    try {
        next = await publicQuiz.getRandomPublicQuestion(props.stationUid as string, props.catalogIds as number[])
    } catch (e) {
        if (isCurrent()) question.value = null
        throw e
    }
    if (!isCurrent()) return
    question.value = next
    initQuestionState(asQuizQuestion(next))
}, {autoLoad: false})

async function loadQuestion() {
    resetUserInput()
    if (!props.stationUid || !props.catalogIds || props.catalogIds.length === 0) {
        question.value = null
        return
    }
    await fetchQuestion()
}

onMounted(loadQuestion)
watch(() => [props.stationUid, JSON.stringify(props.catalogIds ?? [])], loadQuestion)

const adaptedQuestion = computed<QuizQuestion | null>(() => (question.value ? asQuizQuestion(question.value) : null))

function toggleMcOption(idx: number) {
    if (showAnswer.value) return
    const next = new Set(userMcSelections.value)
    if (next.has(idx)) next.delete(idx)
    else next.add(idx)
    userMcSelections.value = next
}
function reorderItems(from: number, to: number) {
    userOrderItems.value = moveWithin(userOrderItems.value, from, to)
}
function setConnectPair(leftIndex: number, rightValue: string) {
    userConnectPairs.value = {...userConnectPairs.value, [String(leftIndex)]: rightValue}
}
function setFillGap(gapIndex: number, value: string) {
    userFillGaps.value = {...userFillGaps.value, [String(gapIndex)]: value}
}
</script>

<template>
    <div class="rounded-theme border border-(--border) p-4 space-y-3">
        <div class="flex items-center gap-3">
            <font-awesome-icon :icon="['fas', 'graduation-cap']" class="text-2xl text-primary"/>
            <p class="font-semibold flex-1">{{ title || t('stationPages.editor.quizDefaultTitle') }}</p>
        </div>
        <p v-if="description" class="text-sm text-(--text-muted)">{{ description }}</p>

        <EmptyHint v-if="!catalogIds || catalogIds.length === 0">
            {{ t('stationPages.editor.quizCatalogsHint') }}
        </EmptyHint>
        <EmptyHint v-else-if="!loading && (error || !adaptedQuestion)">
            {{ t('stationPages.editor.quizNoQuestions') }}
        </EmptyHint>

        <div v-else-if="adaptedQuestion" class="space-y-3">
            <TrainingQuestionCard
                :question="adaptedQuestion"
                :show-answer="showAnswer"
                :user-answer="userAnswer"
                :user-mc-selections="userMcSelections"
                :user-tf-answer="userTfAnswer"
                :user-order-items="userOrderItems"
                :user-connect-pairs="userConnectPairs"
                :user-fill-gaps="userFillGaps"
                :mc-display-order="mcDisplayOrder"
                :connect-right-order="connectRightOrder"
                :direct-image-src="adaptedQuestion.imageUrl"
                @toggle-mc-option="toggleMcOption"
                @update:user-tf-answer="(v: boolean | null) => userTfAnswer = v"
                @update:user-answer="(v: string) => userAnswer = v"
                @reorder-items="reorderItems"
                @set-connect-pair="setConnectPair"
                @set-fill-gap="setFillGap"
            />

            <div class="flex justify-end gap-2">
                <SecondaryButton v-if="showAnswer" @click="loadQuestion">
                    {{ t('stationPages.editor.quizNext') }}
                </SecondaryButton>
                <PrimaryButton v-else @click="showAnswer = true">
                    {{ t('stationPages.editor.quizReveal') }}
                </PrimaryButton>
            </div>
        </div>
    </div>
</template>
