/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {QuestionTypes} from '@/api/forms'
import type {FormAnswerValue, PublicFormQuestion} from '@/api/generated/schema'
import {emptyAnswer} from '@/util/formAnswers'

/**
 * The answers to a form in the public fields' shape, and the three things those fields report: text
 * typed, a date set, an option picked. Rating, ranking and Likert fields write into the answer they
 * are handed.
 *
 * <p>Shared by the public fill screens and the editor's preview, which draws the same fields.
 */
export function usePublicAnswers() {
    const answers = ref<Record<number, FormAnswerValue>>({})

    /** Every question starts with an answer of the shape the server expects. */
    function reset(questions: readonly PublicFormQuestion[]) {
        const defaults: Record<number, FormAnswerValue> = {}
        for (const q of questions) defaults[q.id] = emptyAnswer(q.questionType, q.config)
        answers.value = defaults
    }

    /**
     * Selects the option with this key. A single-select question also clears the free-text "other"
     * answer, since picking a listed option replaces it; an empty key, which the dropdown's blank entry
     * sends, clears the choice.
     */
    function toggleChoice(q: PublicFormQuestion, optionKey: string) {
        const answer = answers.value[q.id]
        if (answer?.type !== QuestionTypes.CHOICE) return
        if (!optionKey) {
            answer.selected = []
            return
        }
        const multiSelect = q.config.questionType === QuestionTypes.CHOICE && !!q.config.multiSelect
        if (!multiSelect) {
            answer.selected = [optionKey]
            answer.other = ''
            return
        }
        const existing = answer.selected.indexOf(optionKey)
        if (existing >= 0) answer.selected.splice(existing, 1)
        else answer.selected.push(optionKey)
    }

    function updateText(q: PublicFormQuestion, text: string) {
        const answer = answers.value[q.id]
        if (answer?.type === QuestionTypes.TEXT) answer.text = text
    }

    function updateDate(q: PublicFormQuestion, date: string) {
        const answer = answers.value[q.id]
        if (answer?.type === QuestionTypes.DATE) answer.date = date
    }

    return {answers, reset, toggleChoice, updateText, updateDate}
}
