/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Form, FormQuestion, FormQuestionAnalytics} from '@/api/forms'
import type {MemberIdentity} from '@/api/types'
import type {PitchForm, PitchFormAnalytics} from './pitchTypes'

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
        purpose: 'INTERNAL', publicUid: `uid-${id}`, responseCount, ...rest,
    }
}

export const FORMS: Form[] = [
    form(1, 'Zeltlager 2026 — Rückmeldung', 'Wie hat euch das letzte Lager gefallen?', 'OPEN', 23),
    form(2, 'Terminwunsch Grillfest', 'Wann passt es euch am besten?', 'OPEN', 14, {restricted: true}),
    form(3, 'Ausbildungsbedarf', 'Was möchtet ihr in diesem Jahr lernen?', 'DRAFT', 0),
]

function question(id: number, type: FormQuestion['formQuestionType'], title: string,
                  config: Record<string, unknown>, rest: Partial<FormQuestion> = {}): FormQuestion {
    return {
        id, formId: 1, position: id, formQuestionType: type, title, description: '',
        required: false, shuffle: false, config, ...rest,
    }
}

export const FORM_QUESTIONS: FormQuestion[] = [
    question(1, 'CHOICE', 'Woran hast du teilgenommen?',
        {options: ['Zeltlager', 'Berufsfeuerwehrtag', 'Kreisjugendtag'], multiSelect: true, allowOther: true},
        {required: true}),
    question(2, 'RATING', 'Wie hat dir das Lager insgesamt gefallen?', {scale: 5, icon: 'STAR'},
        {required: true}),
    question(3, 'RANKING', 'Bring die Programmpunkte in deine Reihenfolge',
        {options: ['Nachtwanderung', 'Wasserspiele', 'Lagerfeuer', 'Geländespiel']}),
    question(4, 'LIKERT', 'Wie sehr stimmst du zu?',
        {
            statements: ['Das Essen war gut', 'Die Zelte waren in Ordnung', 'Es war genug Freizeit'],
            scaleMin: 1, scaleMax: 5,
            labels: ['gar nicht', 'wenig', 'teils', 'ziemlich', 'völlig'],
        }),
]

/** The form as it stands half filled in, in the shape the fill view keeps its answers. */
export const FORM_FILL: PitchForm = {
    questions: FORM_QUESTIONS,
    answers: {
        1: {selected: [0, 2], other: ''},
        2: {rating: 4},
        3: {order: [2, 0, 3, 1]},
        4: {ratings: {0: 5, 1: 3, 2: 4}},
    },
}

function values(entries: unknown[]): string[] {
    return entries.map(entry => JSON.stringify(entry))
}

const ANALYTICS: FormQuestionAnalytics[] = [
    {
        questionId: 1, questionType: 'CHOICE', title: 'Woran hast du teilgenommen?',
        config: {options: ['Zeltlager', 'Berufsfeuerwehrtag', 'Kreisjugendtag'], allowOther: true},
        values: values([
            {selected: [0]}, {selected: [0, 1]}, {selected: [0, 2]}, {selected: [1]},
            {selected: [0, 1, 2]}, {selected: [0]}, {selected: [2]}, {selected: [0], other: 'Übungsdienst'},
        ]),
    },
    {
        questionId: 2, questionType: 'RATING', title: 'Wie hat dir das Lager insgesamt gefallen?',
        config: {scale: 5},
        values: values([{rating: 5}, {rating: 4}, {rating: 5}, {rating: 3}, {rating: 4}, {rating: 5},
            {rating: 4}, {rating: 2}, {rating: 5}, {rating: 4}]),
    },
]

const MISSING: MemberIdentity[] = [
    {stationUid: 'wache', memberUid: 'm-jonas', name: 'Jonas Behr'},
    {stationUid: 'wache', memberUid: 'm-mira', name: 'Mira Sand'},
    {stationUid: 'wache', memberUid: 'm-timo', name: 'Timo Reich'},
]

export const FORM_ANALYTICS_DATA: PitchFormAnalytics = {questions: ANALYTICS, missing: MISSING}
