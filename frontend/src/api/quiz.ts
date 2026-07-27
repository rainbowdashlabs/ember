/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {createCrudResource, createScopedCrudResource} from './crud'
import type {
    QuizCatalog,
    QuizCatalogDetail,
    QuizCatalogExport,
    QuizCategory,
    QuizQuestion,
    QuizTest,
    QuizTestSummary,
    QuizTestDetail,
    QuizTestSection,
    QuizTestAttempt,
    QuizAttemptDetail,
    QuizAvailableTest,
} from './types'
import {uploadFile} from './upload'
import {downloadAuthed} from '@/util/downloadAuthed'

// -- Shared catalog entry from federation --

export interface SharedCatalogEntry {
    catalog: QuizCatalog
    stationName: string
    sourceStationId: string
}

export interface CatalogListResponse {
    catalogs: QuizCatalog[]
    sharedCatalogs: SharedCatalogEntry[]
}

interface CatalogRequest {
    name: string
    description?: string
    trainingEnabled?: boolean
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

// -- CSV Import --

export interface CsvMappings {
    questionColumn: string
    answerColumn: string
    categoryColumn: string
    typeColumn: string
    pointsColumn: string
    separator: string
    answerSeparator: string
    defaultType: string
}

export async function importCsv(catalogId: number, file: File, mappings: CsvMappings): Promise<{ imported: number }> {
    return uploadFile<{ imported: number }>(`/quiz/catalogs/${catalogId}/import-csv`, {
        file,
        mappings: JSON.stringify(mappings),
    })
}
