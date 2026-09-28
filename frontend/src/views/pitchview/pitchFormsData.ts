/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Form, FormQuestion, FormQuestionInfo, FormResultGroup} from '@/api/forms'
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
        createdBy: 1, createdAt: days(-20), updatedAt: days(-2), lastActivityAt: days(-1),
        purpose: 'INTERNAL', visibility: 'PUBLIC', publicUid: `uid-${id}`, responseCount, ...rest,
    }
}

export const FORMS: Form[] = [
    form(1, 'Zeltlager 2026 - Rückmeldung', 'Wie hat euch das letzte Lager gefallen?', 'OPEN', 23),
    form(2, 'Terminwunsch Grillfest', 'Wann passt es euch am besten?', 'OPEN', 14, {restricted: true}),
    form(3, 'Ausbildungsbedarf', 'Was möchtet ihr in diesem Jahr lernen?', 'DRAFT', 0),
]

const CAMPS = numberedOptions('Zeltlager', 'Berufsfeuerwehrtag', 'Kreisjugendtag')

function question(id: number, type: FormQuestion['formQuestionType'], title: string,
                  config: Record<string, unknown>, rest: Partial<FormQuestion> = {}): FormQuestion {
    return {
        id, formId: 1, position: id, formQuestionType: type, title, description: '',
        required: false, shuffle: false, config, ...rest,
    }
}

export const FORM_QUESTIONS: FormQuestion[] = [
    question(1, 'CHOICE', 'Woran hast du teilgenommen?',
        {options: CAMPS, multiSelect: true, allowOther: true},
        {required: true}),
    question(2, 'RATING', 'Wie hat dir das Lager insgesamt gefallen?', {scale: 5, icon: 'STAR'},
        {required: true}),
    question(3, 'RANKING', 'Bring die Programmpunkte in deine Reihenfolge',
        {options: numberedOptions('Nachtwanderung', 'Wasserspiele', 'Lagerfeuer', 'Geländespiel')}),
    question(4, 'LIKERT', 'Wie sehr stimmst du zu?',
        {
            statements: numberedOptions('Das Essen war gut', 'Die Zelte waren in Ordnung', 'Es war genug Freizeit'),
            scaleMin: 1, scaleMax: 5,
            labels: ['gar nicht', 'wenig', 'teils', 'ziemlich', 'völlig'],
        }),
]

/** The form as it stands half filled in, in the shape the fill view keeps its answers. */
export const FORM_FILL: PitchForm = {
    questions: FORM_QUESTIONS,
    answers: {
        1: {selected: ['o0', 'o2'], other: ''},
        2: {rating: 4},
        3: {order: ['o2', 'o0', 'o3', 'o1']},
        4: {ratings: {o0: 5, o1: 3, o2: 4}},
    },
}

const QUESTIONS: FormQuestionInfo[] = [
    {
        questionId: 1, questionType: 'CHOICE', title: 'Woran hast du teilgenommen?',
        config: {options: CAMPS, allowOther: true},
    },
    {
        questionId: 2, questionType: 'RATING', title: 'Wie hat dir das Lager insgesamt gefallen?',
        config: {scale: 5},
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
