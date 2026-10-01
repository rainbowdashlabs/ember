/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
import {uploadFile} from './upload'
import {downloadAuthed} from '@/util/downloadAuthed'
import {prepareImageUpload} from '@/util/imageUpload'
import type {
    CatalogListResponse,
    CatalogTransfer,
    components,
    CsvDraft,
    CsvDraftRequest,
    CsvMappings,
    FrozenQuestionDetail,
    MyQuizAttempt,
    QuestionConfigByType,
    QuizAccessRequest,
    QuizAnswerRequest,
    QuizAttemptDetail,
    QuizAvailableTest,
    QuizCatalog,
    QuizCatalogDetail,
    QuizCatalogRequest,
    QuizCategory,
    QuizCategoryRequest,
    QuizGradeRequest,
    QuizQuestion,
    QuizQuestionRead,
    QuizQuestionReport,
    QuizQuestionRequest,
    QuizReportRequest,
    QuizSectionRequest,
    QuizSuccessResponse,
    QuizTest,
    QuizTestAttempt,
    QuizTestDetail,
    QuizTestRequest,
    QuizTestRestrictions,
    QuizTestRestrictionsRequest,
    QuizTestSection,
    QuizTestSummary,
    RemoteCatalogDetail,
    ReplaceQuestionRequest,
} from './generated/schema'

export type QuizQuestionTypeName = components['schemas']['QuizQuestionType']

export const QuizQuestionTypes = {
    MULTIPLE_CHOICE: 'MULTIPLE_CHOICE',
    FILL_IN_THE_BLANK: 'FILL_IN_THE_BLANK',
    FREE_ANSWER: 'FREE_ANSWER',
    CONNECT: 'CONNECT',
    IMAGE_TEXT: 'IMAGE_TEXT',
    TRUE_FALSE: 'TRUE_FALSE',
    ORDERING: 'ORDERING',
    ENUMERATION: 'ENUMERATION',
} as const satisfies Record<QuizQuestionTypeName, QuizQuestionTypeName>

/** Whether a value names a kind of question, as a picker or a file may hand over any text. */
export function isQuizQuestionType(value: unknown): value is QuizQuestionTypeName {
    return typeof value === 'string' && Object.hasOwn(QuizQuestionTypes, value)
}

export type QuizTestStatusName = components['schemas']['TestStatus']

export const QuizTestStatus = {
    DRAFT: 'DRAFT',
    ACTIVE: 'ACTIVE',
    CLOSED: 'CLOSED',
} as const satisfies Record<QuizTestStatusName, QuizTestStatusName>

export type QuizAttemptStatusName = components['schemas']['AttemptStatus']

export const QuizAttemptStatus = {
    IN_PROGRESS: 'IN_PROGRESS',
    SUBMITTED: 'SUBMITTED',
    GRADED: 'GRADED',
} as const satisfies Record<QuizAttemptStatusName, QuizAttemptStatusName>

/**
 * A question of one kind, with the settings that kind has. The settings travel beside the kind
 * rather than naming it themselves, so {@link isQuizQuestionOf} is what tells them apart.
 */
export type QuizQuestionOf<T extends QuizQuestionTypeName> = Omit<QuizQuestion, 'quizQuestionType' | 'config'> & {
    quizQuestionType: T
    config: QuestionConfigByType[T]
}

/**
 * Whether a question is of the given kind, which is what lets a screen drawing one kind read its
 * settings as that kind's.
 */
export function isQuizQuestionOf<T extends QuizQuestionTypeName>(
    question: QuizQuestion,
    type: T,
): question is QuizQuestionOf<T> {
    return question.quizQuestionType === type
}

/**
 * Whether a question came with its answers. Somebody who may not see the catalog gets the question
 * as it is put to them, without them.
 */
export function isFullQuestion(question: QuizQuestionRead): question is QuizQuestion {
    return 'autoPoints' in question
}

/** Whether the member has an attempt at a test; one who never started has none. */
export function hasAttempt(attempt: MyQuizAttempt): attempt is QuizAttemptDetail {
    return 'attempt' in attempt
}

/**
 * Reads a catalog served by a federation partner. The partner is addressed by its station UUID
 * because a catalog id is only unique within the station that owns it.
 */
export async function getFederatedCatalog(stationUid: string, catalogId: number): Promise<RemoteCatalogDetail> {
    const res = await client.get<RemoteCatalogDetail>(`/federated/${stationUid}/quiz/catalogs/${catalogId}`)
    return res.data
}

const catalogs = createCrudResource<
    QuizCatalog,
    QuizCatalogRequest,
    QuizCatalogRequest,
    QuizCatalogDetail
>('/quiz/catalogs')

const categories = createCrudResource<
    QuizCategory,
    QuizCategoryRequest,
    QuizCategoryRequest,
    QuizCategory,
    QuizCategory,
    QuizSuccessResponse
>('/quiz/categories')

const catalogQuestions = createScopedCrudResource<
    QuizQuestion,
    QuizQuestionRequest
>((catalogId: number) => `/quiz/catalogs/${catalogId}/questions`)

const questions = createCrudResource<
    QuizQuestion,
    QuizQuestionRequest,
    QuizQuestionRequest,
    QuizQuestionRead,
    QuizQuestion,
    QuizQuestion
>('/quiz/questions')

const tests = createCrudResource<
    QuizTestSummary,
    QuizTestRequest,
    QuizTestRequest,
    QuizTestDetail,
    QuizTest
>('/quiz/tests')

// -- Catalogs --

export async function listCatalogs(): Promise<CatalogListResponse> {
    const res = await client.get<CatalogListResponse>('/quiz/catalogs')
    return res.data
}

export const getCatalog = catalogs.get
export const createCatalog = catalogs.create
export const updateCatalog = catalogs.update
export const deleteCatalog = catalogs.remove

// -- Categories (station-scoped) --

export const listCategories = categories.list
export const createCategory = categories.create
export const updateCategory = categories.update
export const deleteCategory = categories.remove

// -- Questions --

export const listQuestions = catalogQuestions.list
export const createQuestion = catalogQuestions.create
export const getQuestion = questions.get
export const updateQuestion = questions.update
export const deleteQuestion = questions.remove

// -- Tests --

export const listTests = tests.list
export const getTest = tests.get
export const createTest = tests.create
export const updateTest = tests.update
export const deleteTest = tests.remove

export async function listAvailableTests(): Promise<QuizAvailableTest[]> {
    const res = await client.get<QuizAvailableTest[]>('/quiz/tests/available')
    return res.data
}

export async function activateTest(id: number): Promise<QuizTest> {
    const res = await client.post<QuizTest>(`/quiz/tests/${id}/activate`)
    return res.data
}

export async function closeTest(id: number): Promise<QuizTest> {
    const res = await client.post<QuizTest>(`/quiz/tests/${id}/close`)
    return res.data
}

// -- Frozen Questions --

export async function generateFrozenQuestions(testId: number): Promise<FrozenQuestionDetail[]> {
    const res = await client.post<FrozenQuestionDetail[]>(`/quiz/tests/${testId}/generate-questions`)
    return res.data
}

export async function listFrozenQuestions(testId: number): Promise<FrozenQuestionDetail[]> {
    const res = await client.get<FrozenQuestionDetail[]>(`/quiz/tests/${testId}/frozen-questions`)
    return res.data
}

export async function replaceFrozenQuestion(testId: number, position: number, questionId: number): Promise<FrozenQuestionDetail[]> {
    const request: ReplaceQuestionRequest = {questionId}
    const res = await client.put<FrozenQuestionDetail[]>(`/quiz/tests/${testId}/frozen-questions/${position}`, request)
    return res.data
}

export async function randomReplaceFrozenQuestion(testId: number, position: number): Promise<FrozenQuestionDetail[]> {
    const res = await client.post<FrozenQuestionDetail[]>(`/quiz/tests/${testId}/frozen-questions/${position}/random`)
    return res.data
}

export async function listAvailableReplacements(testId: number): Promise<QuizQuestion[]> {
    const res = await client.get<QuizQuestion[]>(`/quiz/tests/${testId}/available-questions`)
    return res.data
}

// -- Sections --

export async function replaceSections(testId: number, sections: QuizSectionRequest[]): Promise<QuizTestSection[]> {
    const res = await client.put<QuizTestSection[]>(`/quiz/tests/${testId}/sections`, sections)
    return res.data
}

// -- Test Taking --

export async function startAttempt(testId: number): Promise<QuizAttemptDetail> {
    const res = await client.post<QuizAttemptDetail>(`/quiz/tests/${testId}/start`)
    return res.data
}

/** The member's attempt at a test, or nothing where they never started one. */
export async function getMyAttempt(testId: number): Promise<MyQuizAttempt> {
    const res = await client.get<MyQuizAttempt>(`/quiz/tests/${testId}/my-attempt`)
    return res.data
}

export async function saveAnswer(attemptId: number, questionId: number, answer: string): Promise<void> {
    const request: QuizAnswerRequest = {questionId, answer}
    await client.post(`/quiz/attempts/${attemptId}/answer`, request)
}

export async function submitAttempt(attemptId: number): Promise<QuizTestAttempt> {
    const res = await client.post<QuizTestAttempt>(`/quiz/attempts/${attemptId}/submit`)
    return res.data
}

// -- Grading --

export async function listAttempts(testId: number): Promise<QuizTestAttempt[]> {
    const res = await client.get<QuizTestAttempt[]>(`/quiz/tests/${testId}/attempts`)
    return res.data
}

export async function getAttemptDetail(attemptId: number): Promise<QuizAttemptDetail> {
    const res = await client.get<QuizAttemptDetail>(`/quiz/attempts/${attemptId}`)
    return res.data
}

export async function gradeAnswer(answerId: number, points: number): Promise<void> {
    const request: QuizGradeRequest = {points}
    await client.post(`/quiz/answers/${answerId}/grade`, request)
}

export async function gradeAttempt(attemptId: number): Promise<QuizTestAttempt> {
    const res = await client.post<QuizTestAttempt>(`/quiz/attempts/${attemptId}/grade`)
    return res.data
}

// -- Restrictions --

export async function getRestrictions(testId: number): Promise<QuizTestRestrictions> {
    const res = await client.get<QuizTestRestrictions>(`/quiz/tests/${testId}/restrictions`)
    return res.data
}

/**
 * Replaces whom a test is put to. Every list sent replaces the stored one, so a list the screen does
 * not edit has to be sent back as it was read.
 *
 * @returns the restrictions as stored
 */
export async function setRestrictions(testId: number, data: QuizTestRestrictionsRequest): Promise<QuizTestRestrictions> {
    const res = await client.put<QuizTestRestrictions>(`/quiz/tests/${testId}/restrictions`, data)
    return res.data
}

// -- Member Access --

export async function grantAccess(testId: number, memberId: number, closesAt?: string | null): Promise<void> {
    const request: QuizAccessRequest = {memberId, closesAt}
    await client.post(`/quiz/tests/${testId}/access`, request)
}

export async function revokeAccess(testId: number, memberId: number): Promise<void> {
    await client.delete(`/quiz/tests/${testId}/access/${memberId}`)
}

// -- Training --

export async function listTrainingCatalogs(): Promise<QuizCatalog[]> {
    const res = await client.get<QuizCatalog[]>('/quiz/training/catalogs')
    return res.data
}

export async function getTrainingQuestions(catalogId: number): Promise<QuizQuestion[]> {
    const res = await client.get<QuizQuestion[]>(`/quiz/training/catalogs/${catalogId}/questions`)
    return res.data
}

// -- Question Images --

export function questionImageUrl(questionId: number, size?: number): string {
    const base = `/quiz/questions/${questionId}/image`
    return size ? `${base}?size=${size}` : base
}

/**
 * Sends the picture for a question, redrawn to a format and a size the endpoint takes, so a photo
 * taken on the spot is not refused for being what a camera produces.
 */
export async function uploadQuestionImage(questionId: number, file: File): Promise<void> {
    await uploadFile(`/quiz/questions/${questionId}/image`, {image: await prepareImageUpload(file)})
}

export async function deleteQuestionImage(questionId: number): Promise<void> {
    await client.delete(`/quiz/questions/${questionId}/image`)
}

// -- PDF Export --

export async function downloadQuestionPdf(testId: number): Promise<void> {
    await downloadAuthed(`/quiz/tests/${testId}/export/questions`)
}

export async function downloadSolutionPdf(testId: number): Promise<void> {
    await downloadAuthed(`/quiz/tests/${testId}/export/solutions`)
}

// -- Import/Export --

export async function exportCatalog(catalogId: number): Promise<CatalogTransfer> {
    const res = await client.get<CatalogTransfer>(`/quiz/catalogs/${catalogId}/export`)
    return res.data
}

export async function importCatalog(data: CatalogTransfer): Promise<QuizCatalog> {
    const res = await client.post<QuizCatalog>('/quiz/catalogs/import', data)
    return res.data
}

/** Adds the questions a file carries to a catalog that already exists, behind the ones in it. */
export async function appendToCatalog(catalogId: number, data: CatalogTransfer): Promise<QuizCatalog> {
    const res = await client.post<QuizCatalog>(`/quiz/catalogs/${catalogId}/import`, data)
    return res.data
}

// -- Reading a sheet --

/** Reads a sheet into a draft without writing anything, so the wizard can show what would arrive. */
export async function draftFromCsv(content: string, mappings: CsvMappings): Promise<CsvDraft> {
    const request: CsvDraftRequest = {content, mappings}
    const res = await client.post<CsvDraft>('/quiz/catalogs/csv-draft', request)
    return res.data
}

/** Saves the shipped example of a format, which is a file that already imports as it stands. */
export async function downloadCatalogTemplate(format: 'csv' | 'json'): Promise<void> {
    await downloadAuthed(`/quiz/catalogs/template/${format}`)
}

// -- Reports on questions --

/** Reports a question from the training view. */
export async function reportQuestion(questionId: number, note: string): Promise<QuizQuestionReport> {
    const request: QuizReportRequest = {note}
    const res = await client.post<QuizQuestionReport>(`/quiz/questions/${questionId}/reports`, request)
    return res.data
}

/** Every open note on the questions of one catalog. */
export async function listCatalogReports(catalogId: number): Promise<QuizQuestionReport[]> {
    const res = await client.get<QuizQuestionReport[]>(`/quiz/catalogs/${catalogId}/reports`)
    return res.data
}

/** Acknowledges a note, which removes it. */
export async function acknowledgeReport(reportId: number): Promise<void> {
    await client.delete(`/quiz/reports/${reportId}`)
}
