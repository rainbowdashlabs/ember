/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
import type {MemberIdentity} from './types'

export const QuizQuestionTypes = {
    MULTIPLE_CHOICE: 'MULTIPLE_CHOICE',
    FILL_IN_THE_BLANK: 'FILL_IN_THE_BLANK',
    FREE_ANSWER: 'FREE_ANSWER',
    CONNECT: 'CONNECT',
    IMAGE_TEXT: 'IMAGE_TEXT',
    TRUE_FALSE: 'TRUE_FALSE',
    ORDERING: 'ORDERING',
    ENUMERATION: 'ENUMERATION',
} as const

export type QuizQuestionTypeName = (typeof QuizQuestionTypes)[keyof typeof QuizQuestionTypes]

export const QuizTestStatus = {
    DRAFT: 'DRAFT',
    ACTIVE: 'ACTIVE',
    CLOSED: 'CLOSED',
} as const

export type QuizTestStatusName = (typeof QuizTestStatus)[keyof typeof QuizTestStatus]

export const QuizAttemptStatus = {
    IN_PROGRESS: 'IN_PROGRESS',
    SUBMITTED: 'SUBMITTED',
    GRADED: 'GRADED',
} as const

export type QuizAttemptStatusName = (typeof QuizAttemptStatus)[keyof typeof QuizAttemptStatus]

/** Where a catalog's questions came from, filled in by an import or a copy from a partner. */
export interface CatalogMetadata {
    language: string | null
    source: string | null
    author: string | null
    license: string | null
}

export interface QuizCatalog {
    id: number
    stationId: string
    name: string
    description: string
    trainingEnabled: boolean
    metadata: CatalogMetadata
    createdAt: string
    updatedAt: string
}

export interface QuizCategory {
    id: number
    stationId: string
    name: string
    description: string
    position: number
}

export interface QuizQuestion {
    id: number
    catalogId: number
    categoryId: number | null
    quizQuestionType: QuizQuestionTypeName
    title: string
    description: string
    imageUrl: string | null
    points: number
    autoPoints: boolean
    config: Record<string, unknown>
    position: number
    createdAt: string
    updatedAt: string
}

export interface QuizCatalogDetail {
    id: number
    stationId: string
    name: string
    description: string
    trainingEnabled: boolean
    metadata: CatalogMetadata
    questionCount: number
    questionTypeCounts: Record<string, number>
    categories: QuizCategory[]
    createdAt: string
    updatedAt: string
}

export interface QuizTest {
    id: number
    stationId: string
    title: string
    description: string
    status: QuizTestStatusName
    timeLimit: number | null
    shuffle: boolean
    forced?: boolean
    startAt: string | null
    endAt: string | null
    createdBy: number
    createdAt: string
    updatedAt: string
    restrictionMode?: string
    restricted?: boolean
}

export interface QuizAvailableTest {
    test: QuizTest
    attemptStatus: string | null
    startedAt: string | null
    submittedAt: string | null
}

export interface QuizTestSummary {
    test: QuizTest
    attemptCount: number
}

export interface QuizTestSection {
    id: number
    testId: number
    title: string
    description: string
    position: number
}

export interface QuizTestSectionSource {
    id: number
    sectionId: number
    catalogId: number
    categoryId: number | null
    questionCount: number
}

export interface QuizSectionDetail {
    id: number
    testId: number
    title: string
    description: string
    position: number
    sources: QuizTestSectionSource[]
}

export interface QuizTestDetail {
    test: QuizTest
    sections: QuizSectionDetail[]
    attemptCount: number
}

export interface QuizTestAttempt {
    id: number
    testId: number
    memberId: number
    status: QuizAttemptStatusName
    startedAt: string
    submittedAt: string | null
    gradedAt: string | null
    gradedBy: number | null
    totalPoints: number
    maxPoints: number
}

export interface QuizTestAttemptQuestion {
    id: number
    attemptId: number
    questionId: number
    sectionId: number | null
    position: number
}

export interface QuizTestAnswer {
    id: number
    attemptId: number
    questionId: number
    sectionId: number | null
    answer: string
    points: number | null
    graded: boolean
    position: number
}

export interface QuizAttemptDetail {
    attempt: QuizTestAttempt
    questions: QuizTestAttemptQuestion[]
    answers: QuizTestAnswer[]
    questionDetails?: QuizQuestion[] | null
    memberIdentity?: MemberIdentity | null
}

/**
 * The file a catalog is exported to and imported from. Categories are addressed by the key the
 * file itself defines, because a database id from the exporting station names nothing here.
 */
export interface QuizCatalogExport {
    formatVersion: number
    catalog: {
        name: string
        description: string
        trainingEnabled: boolean
        metadata: CatalogMetadata
    }
    categories: QuizCatalogExportCategory[]
    questions: QuizCatalogExportQuestion[]
}

export interface QuizCatalogExportCategory {
    key: string
    name: string
    description: string
    position: number
}

export interface QuizCatalogExportQuestion {
    categoryKey: string | null
    quizQuestionType: QuizQuestionTypeName
    title: string
    description: string
    imageUrl: string | null
    points: number
    autoPoints: boolean
    config: Record<string, unknown>
    position: number
}

/** One reason an uploaded file was refused, pointing at the place in the file. */
export interface CatalogTransferProblem {
    location: string
    message: string
}
import {uploadFile} from './upload'
import {downloadAuthed} from '@/util/downloadAuthed'

// -- Shared catalog entry from federation --

export interface SharedCatalogEntry {
    id: number
    name: string
    description: string | null
    stationName: string
    stationUid: string | null
}

/**
 * A catalog as a federation partner serves it: the catalog itself plus the categories and
 * questions it holds.
 */
export interface FederatedCatalogDetail {
    catalog: QuizCatalog
    categories: QuizCategory[]
    questions: QuizQuestion[]
}

/**
 * Reads a catalog served by a federation partner. The partner is addressed by its station UUID
 * because a catalog id is only unique within the station that owns it.
 */
export async function getFederatedCatalog(stationUid: string, catalogId: number): Promise<FederatedCatalogDetail> {
    const res = await client.get<FederatedCatalogDetail>(`/federated/${stationUid}/quiz/catalogs/${catalogId}`)
    return res.data
}

export interface CatalogListResponse {
    catalogs: QuizCatalog[]
    sharedCatalogs: SharedCatalogEntry[]
}

interface CatalogRequest {
    name: string
    description?: string
    trainingEnabled?: boolean
    metadata?: CatalogMetadata
}

interface CategoryRequest {
    name: string
    description?: string
    position?: number
}

interface TestCreateRequest {
    title: string
    description?: string
    timeLimit?: number | null
    shuffle?: boolean
    forced?: boolean
}

const catalogs = createCrudResource<
    QuizCatalog,
    CatalogRequest,
    CatalogRequest,
    QuizCatalogDetail
>('/quiz/catalogs')

const categories = createCrudResource<
    QuizCategory,
    CategoryRequest,
    CategoryRequest,
    QuizCategory,
    QuizCategory,
    void
>('/quiz/categories')

const catalogQuestions = createScopedCrudResource<
    QuizQuestion,
    Record<string, unknown>
>((catalogId: number) => `/quiz/catalogs/${catalogId}/questions`)

const questions = createCrudResource<QuizQuestion, Record<string, unknown>>('/quiz/questions')

const tests = createCrudResource<
    QuizTestSummary,
    TestCreateRequest,
    Record<string, unknown>,
    QuizTestDetail,
    QuizTest
>('/quiz/tests')

// -- Catalogs --

export async function listCatalogs(): Promise<CatalogListResponse> {
    const res = await client.get<CatalogListResponse>('/quiz/catalogs')
    return res.data
}

export async function searchCatalogs(query: string, federated: boolean): Promise<CatalogListResponse> {
    const res = await client.get<CatalogListResponse>('/quiz/catalogs/search', {
        params: { q: query, federated },
    })
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

export interface FrozenQuestionDetail {
    position: number
    sectionId: number | null
    question: QuizQuestion | null
}

export async function generateFrozenQuestions(testId: number): Promise<FrozenQuestionDetail[]> {
    const res = await client.post<FrozenQuestionDetail[]>(`/quiz/tests/${testId}/generate-questions`)
    return res.data
}

export async function listFrozenQuestions(testId: number): Promise<FrozenQuestionDetail[]> {
    const res = await client.get<FrozenQuestionDetail[]>(`/quiz/tests/${testId}/frozen-questions`)
    return res.data
}

export async function replaceFrozenQuestion(testId: number, position: number, questionId: number): Promise<FrozenQuestionDetail[]> {
    const res = await client.put<FrozenQuestionDetail[]>(`/quiz/tests/${testId}/frozen-questions/${position}`, { questionId })
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

export async function replaceSections(testId: number, sections: { title: string; description: string; sources: { catalogId: number; categoryId?: number | null; questionCount: number }[] }[]): Promise<QuizTestSection[]> {
    const res = await client.put<QuizTestSection[]>(`/quiz/tests/${testId}/sections`, sections)
    return res.data
}

// -- Test Taking --

export async function startAttempt(testId: number): Promise<QuizAttemptDetail> {
    const res = await client.post<QuizAttemptDetail>(`/quiz/tests/${testId}/start`)
    return res.data
}

export async function getMyAttempt(testId: number): Promise<QuizAttemptDetail> {
    const res = await client.get<QuizAttemptDetail>(`/quiz/tests/${testId}/my-attempt`)
    return res.data
}

export async function saveAnswer(attemptId: number, questionId: number, answer: string): Promise<void> {
    await client.post(`/quiz/attempts/${attemptId}/answer`, { questionId, answer })
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
    await client.post(`/quiz/answers/${answerId}/grade`, { points })
}

export async function gradeAttempt(attemptId: number): Promise<QuizTestAttempt> {
    const res = await client.post<QuizTestAttempt>(`/quiz/attempts/${attemptId}/grade`)
    return res.data
}

// -- Restrictions --

export interface QuizTestRestrictions {
    userTypes?: string[]
    groupIds: number[]
    tagIds: number[]
    mode?: string
}

export async function getRestrictions(testId: number): Promise<QuizTestRestrictions> {
    const res = await client.get<QuizTestRestrictions>(`/quiz/tests/${testId}/restrictions`)
    return res.data
}

export async function setRestrictions(testId: number, data: QuizTestRestrictions): Promise<void> {
    await client.put(`/quiz/tests/${testId}/restrictions`, data)
}

// -- Member Access --

export async function grantAccess(testId: number, memberId: number, closesAt?: string | null): Promise<void> {
    await client.post(`/quiz/tests/${testId}/access`, { memberId, closesAt })
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

export async function uploadQuestionImage(questionId: number, file: File): Promise<void> {
    await uploadFile(`/quiz/questions/${questionId}/image`, {image: file})
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

export async function exportCatalog(catalogId: number): Promise<QuizCatalogExport> {
    const res = await client.get<QuizCatalogExport>(`/quiz/catalogs/${catalogId}/export`)
    return res.data
}

export async function importCatalog(data: QuizCatalogExport): Promise<QuizCatalog> {
    const res = await client.post<QuizCatalog>('/quiz/catalogs/import', data)
    return res.data
}

/** Adds the questions a file carries to a catalog that already exists, behind the ones in it. */
export async function appendToCatalog(catalogId: number, data: QuizCatalogExport): Promise<QuizCatalog> {
    const res = await client.post<QuizCatalog>(`/quiz/catalogs/${catalogId}/import`, data)
    return res.data
}

// -- Reading a sheet --

/** Which column of a sheet carries which field. Everything but the question text is optional. */
export interface CsvMappings {
    questionColumn: string
    answerColumn: string
    categoryColumn: string
    typeColumn: string
    pointsColumn: string
    descriptionColumn: string
    imageColumn: string
    distractorColumn: string
    pointsPerCorrectColumn: string
    requiredCountColumn: string
    orderedRequiredColumn: string
    separator: string
    answerSeparator: string
    defaultType: QuizQuestionTypeName
}

/**
 * One row read into the shape a catalog file carries, alongside the answer cell it came from.
 * The wizard keeps that cell so it can offer to split it again on a different separator.
 */
export interface QuizCsvDraftQuestion {
    question: QuizCatalogExportQuestion
    rawAnswer: string
    answerSeparator: string
}

export interface QuizCsvDraft {
    categories: QuizCatalogExportCategory[]
    questions: QuizCsvDraftQuestion[]
}

/** Reads a sheet into a draft without writing anything, so the wizard can show what would arrive. */
export async function draftFromCsv(content: string, mappings: CsvMappings): Promise<QuizCsvDraft> {
    const res = await client.post<QuizCsvDraft>('/quiz/catalogs/csv-draft', {content, mappings})
    return res.data
}

/** Saves the shipped example of a format, which is a file that already imports as it stands. */
export async function downloadCatalogTemplate(format: 'csv' | 'json'): Promise<void> {
    await downloadAuthed(`/quiz/catalogs/template/${format}`)
}

// -- Reports on questions --

/**
 * A note somebody left on a question while training, saying that something about it is wrong, out
 * of date or ambiguous. It exists until somebody who maintains the catalog acknowledges it.
 */
export interface QuizQuestionReport {
    id: number
    questionId: number
    reporterName: string
    note: string
    createdAt: string
}

/** Reports a question from the training view. */
export async function reportQuestion(questionId: number, note: string): Promise<QuizQuestionReport> {
    const res = await client.post<QuizQuestionReport>(`/quiz/questions/${questionId}/reports`, {note})
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
