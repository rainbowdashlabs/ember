/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {
    FrozenQuestionDetail,
    QuizCatalog,
    QuizQuestion,
    QuizSectionDetail,
    QuizTest,
    QuizTestAnswer,
    QuizTestAttempt,
} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'

const STATION_ID = '00000000-0000-0000-0000-000000000000'
const CREATED_AT = '2026-03-02T18:00:00Z'

function test(id: number, title: string, description: string, status: QuizTest['status']): QuizTest {
    return {
        id,
        title,
        description,
        status,
        stationId: STATION_ID,
        createdAt: CREATED_AT,
        updatedAt: CREATED_AT,
        createdBy: 1,
        startAt: null,
        endAt: null,
        forced: false,
        restricted: false,
        restrictionMode: 'AND',
        shuffle: true,
        timeLimit: 30,
    }
}

function question(id: number, title: string, quizQuestionType: QuizQuestion['quizQuestionType'], points: number,
                  config: QuizQuestion['config']): QuizQuestion {
    return {
        id,
        title,
        quizQuestionType,
        points,
        config,
        description: '',
        catalogId: 1,
        categoryId: null,
        position: id,
        autoPoints: true,
        imageUrl: null,
        createdAt: CREATED_AT,
        updatedAt: CREATED_AT,
    }
}

/** An active test, a draft and a closed one that the reader already sat. */
export const sampleTests: QuizTest[] = [
    test(1, 'Brandschutz-Prüfung', 'Prüfung zum Thema Brandschutz', 'ACTIVE'),
    test(2, 'Erste-Hilfe-Test', 'Entwurf für den nächsten Übungsabend', 'DRAFT'),
    test(3, 'Knoten-Quiz', 'Abgeschlossener Test', 'CLOSED'),
]

/** The closed test is the one the reader has already handed in. */
export function sampleSubmitted(entry: QuizTest): boolean {
    return entry.status === 'CLOSED'
}

/** The test whose detail page the article walks through, still a draft so its questions can be swapped. */
export const sampleDraftTest: QuizTest = sampleTests[1]!

export const sampleCatalogs: QuizCatalog[] = [{
    id: 1,
    name: 'Brandschutz-Katalog',
    description: 'Grundlagen des vorbeugenden Brandschutzes',
    stationId: STATION_ID,
    metadata: {author: null, language: 'de', license: null, source: null},
    publicRender: false,
    trainingEnabled: true,
    createdAt: CREATED_AT,
    updatedAt: CREATED_AT,
}]

/** The catalog name a section source shows. */
export function sampleCatalogName(catalogId: number): string {
    return sampleCatalogs.find(catalog => catalog.id === catalogId)?.name ?? `#${catalogId}`
}

export const sampleSections: QuizSectionDetail[] = [{
    id: 1,
    testId: 2,
    position: 0,
    title: 'Grundwissen',
    description: 'Fragen zu den Grundlagen',
    sources: [{id: 1, sectionId: 1, catalogId: 1, categoryId: null, questionCount: 5}],
}]

/** One section as the test builder holds it while it is being written. */
export const sampleSectionDrafts = [{
    key: 'section-1',
    title: 'Grundwissen Brandschutz',
    description: 'Fragen zu den Grundlagen',
    sources: [{key: 'source-1', catalogId: 1, categoryId: null, questionCount: 10}],
}]

const freeAnswerQuestion = question(3, 'Womit löscht man einen Holzbrand?', 'FREE_ANSWER', 2,
    {answers: ['Wasser', 'Schaum'], lines: 2, pointsPerCorrect: 1})

export const sampleFrozenQuestions: FrozenQuestionDetail[] = [
    {position: 0, sectionId: 1, question: question(1, 'Was ist die Hauptaufgabe der Feuerwehr?', 'MULTIPLE_CHOICE', 2, {options: null, pointsPerCorrect: 1})},
    {position: 1, sectionId: 1, question: question(2, 'Die Feuerwehr ist nur für Brände zuständig.', 'TRUE_FALSE', 1, {correctAnswer: false})},
    {position: 2, sectionId: 1, question: freeAnswerQuestion},
]

export const sampleMembers: MemberLike[] = [
    {id: 1, name: 'Max Mustermann'},
    {id: 2, name: 'Lisa Beispiel'},
]

export const sampleAttempts: QuizTestAttempt[] = [
    {
        id: 1, testId: 1, memberId: 2, status: 'SUBMITTED',
        startedAt: '2026-03-05T18:00:00Z', submittedAt: '2026-03-05T18:24:00Z',
        gradedAt: null, gradedBy: null, totalPoints: 0, maxPoints: 10,
    },
    {
        id: 2, testId: 1, memberId: 1, status: 'GRADED',
        startedAt: '2026-03-05T18:02:00Z', submittedAt: '2026-03-05T18:21:00Z',
        gradedAt: '2026-03-06T09:00:00Z', gradedBy: 1, totalPoints: 8, maxPoints: 10,
    },
]

/** A free answer waiting for a grader, with the catalog's sample answers beside it. */
export const sampleReviewQuestion: QuizQuestion = freeAnswerQuestion

export const sampleReviewAnswer: QuizTestAnswer = {
    id: 1,
    attemptId: 1,
    questionId: 3,
    sectionId: 1,
    position: 2,
    answer: JSON.stringify({text: 'Wasser'}),
    graded: false,
    points: null,
}
