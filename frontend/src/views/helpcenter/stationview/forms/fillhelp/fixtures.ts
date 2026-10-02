/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {PageTargetKind, QuestionTypes} from '@/api/forms'
import type {FormAnswerValue, FormPage, FormQuestion, FormQuestionConfig} from '@/api/generated/schema'
import type {Translate} from '@/util/failure'
import {numberedOptions} from '@/util/formOptions'

const FORM_ID = 1
const PAGE_KEY = 'p0'

/** The one page the sample form has. */
export function samplePages(): FormPage[] {
    return [{id: 1, formId: FORM_ID, key: PAGE_KEY, position: 0, title: '', description: '', after: {kind: PageTargetKind.NEXT}}]
}

/** One question of every kind the fill help shows: a text, a choice, a rating and a date. */
export function sampleQuestions(t: Translate): FormQuestion[] {
    return [
        question(1, t('helpCenter.sample.forms.likedLately'), true, {questionType: QuestionTypes.TEXT},
            t('helpCenter.sample.forms.ownWords')),
        question(2, t('helpCenter.sample.forms.likedMost'), false, {
            questionType: QuestionTypes.CHOICE,
            options: numberedOptions(t('helpCenter.formsFill.dummyChoiceOption1'), t('helpCenter.formsFill.dummyChoiceOption2')),
        }),
        question(3, t('helpCenter.formsFill.dummyRatingQuestion'), true, {questionType: QuestionTypes.RATING, scale: 5, icon: 'STAR'}),
        question(4, t('helpCenter.formsFill.dummyDateQuestion'), false, {questionType: QuestionTypes.DATE}),
    ]
}

/** Answers half given, so the help shows a picked option, a rating and a date next to an empty field. */
export function sampleAnswers(): Record<number, FormAnswerValue> {
    return {
        1: {type: QuestionTypes.TEXT, text: ''},
        2: {type: QuestionTypes.CHOICE, selected: ['o0']},
        3: {type: QuestionTypes.RATING, rating: 4},
        4: {type: QuestionTypes.DATE, date: '2026-06-15'},
    }
}

/** The reader themselves and one member they manage, as the selector of whom the form is for offers them. */
export function sampleFillTargets(t: Translate): {id: number | null; label: string}[] {
    return [{id: null, label: t('forms.fillForSelfDefault')}, {id: 1, label: 'Max Mustermann'}]
}

function question(id: number, title: string, required: boolean, config: FormQuestionConfig, description = ''): FormQuestion {
    return {
        id, formId: FORM_ID, pageKey: PAGE_KEY, position: id, title, description, required, shuffle: false,
        formQuestionType: config.questionType, config, branch: null,
    }
}
