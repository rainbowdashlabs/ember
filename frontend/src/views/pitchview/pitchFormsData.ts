/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Form, FormQuestion, FormQuestionConfig, FormQuestionInfo, FormResultGroup} from '@/api/generated/schema'
import type {MemberIdentity} from '@/api/types'
import type {PitchForm, PitchFormAnalytics} from './pitchTypes'
import {numberedOptions} from '@/util/formOptions'

/**
 * The survey a demonstration works through. Questions, answers and charts are handed to the
 * application's own form components, so every scale and every diagram is the real one.
 */
function days(offset: number): string {
    const date = new Date()
    date.setDate(date.getDate() + offset)
    return date.toISOString()
}

function form(id: number, title: string, description: string, status: Form['status'],
              responseCount: number, rest: Partial<Form> = {}): Form {
    return {
        id, stationId: 'wache', title, description, status, shuffleQuestions: false, allowEdit: true,
        forced: false, startAt: null, endAt: null, closedAt: null, acceptingResponses: status === 'OPEN',
        createdBy: 1, createdAt: days(-20), updatedAt: days(-2), lastActivityAt: days(-1),
        restrictionMode: 'AND', restricted: false, purpose: 'INTERNAL', visibility: 'PUBLIC',
        publicUid: `uid-${id}`, responseCount, completionMessage: null, completionLink: null,
        completionLinkLabel: null, ...rest,
    }
}

export const FORMS: Form[] = [
    form(1, 'Zeltlager 2026 - Rückmeldung', 'Wie hat euch das letzte Lager gefallen?', 'OPEN', 23),
    form(2, 'Terminwunsch Grillfest', 'Wann passt es euch am besten?', 'OPEN', 14, {restricted: true}),
    form(3, 'Ausbildungsbedarf', 'Was möchtet ihr in diesem Jahr lernen?', 'DRAFT', 0),
]

const CAMPS = numberedOptions('Zeltlager', 'Berufsfeuerwehrtag', 'Kreisjugendtag')

function question(id: number, title: string, config: FormQuestionConfig, rest: Partial<FormQuestion> = {}): FormQuestion {
    return {
        id, formId: 1, position: id, pageKey: 'p0', formQuestionType: config.questionType, title, description: '',
        required: false, shuffle: false, config, branch: null, ...rest,
    }
}

export const FORM_QUESTIONS: FormQuestion[] = [
    question(1, 'Woran hast du teilgenommen?',
        {questionType: 'CHOICE', options: CAMPS, multiSelect: true, allowOther: true},
        {required: true}),
    question(2, 'Wie hat dir das Lager insgesamt gefallen?', {questionType: 'RATING', scale: 5, icon: 'STAR'},
        {required: true}),
    question(3, 'Bring die Programmpunkte in deine Reihenfolge',
        {questionType: 'RANKING', options: numberedOptions('Nachtwanderung', 'Wasserspiele', 'Lagerfeuer', 'Geländespiel')}),
    question(4, 'Wie sehr stimmst du zu?',
        {
            questionType: 'LIKERT',
            statements: numberedOptions('Das Essen war gut', 'Die Zelte waren in Ordnung', 'Es war genug Freizeit'),
            scaleMin: 1, scaleMax: 5,
            scaleLabels: ['gar nicht', 'wenig', 'teils', 'ziemlich', 'völlig'],
        }),
]

/** The form as it stands half filled in, in the shape the fill view keeps its answers. */
export const FORM_FILL: PitchForm = {
    questions: FORM_QUESTIONS,
    answers: {
        1: {type: 'CHOICE', selected: ['o0', 'o2'], other: ''},
        2: {type: 'RATING', rating: 4},
        3: {type: 'RANKING', order: ['o2', 'o0', 'o3', 'o1']},
        4: {type: 'LIKERT', ratings: {o0: 5, o1: 3, o2: 4}},
    },
}

const QUESTIONS: FormQuestionInfo[] = [
    {
        questionId: 1, questionType: 'CHOICE', title: 'Woran hast du teilgenommen?',
        config: {questionType: 'CHOICE', options: CAMPS, allowOther: true},
    },
    {
        questionId: 2, questionType: 'RATING', title: 'Wie hat dir das Lager insgesamt gefallen?',
        config: {questionType: 'RATING', scale: 5},
    },
]

const EVERYONE: FormResultGroup = {
    key: 'all',
    label: '',
    responseCount: 10,
    tallies: [
        {questionId: 1, answerCount: 8, optionCounts: {o0: 6, o1: 3, o2: 3}, otherCount: 1},
        {questionId: 2, answerCount: 10, ratingCounts: [0, 1, 1, 4, 4]},
    ],
}

const MISSING: MemberIdentity[] = [
    {stationUid: 'wache', memberUid: 'm-jonas', name: 'Jonas Behr'},
    {stationUid: 'wache', memberUid: 'm-mira', name: 'Mira Sand'},
    {stationUid: 'wache', memberUid: 'm-timo', name: 'Timo Reich'},
]

export const FORM_ANALYTICS_DATA: PitchFormAnalytics = {questions: QUESTIONS, groups: [EVERYONE], missing: MISSING}
