/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
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
} from './types'

// -- Shared catalog entry from federation --

export interface SharedCatalogEntry {
    catalog: QuizCatalog
    stationName: string
    sourceStationId: number
}

export interface CatalogListResponse {
    catalogs: QuizCatalog[]
    sharedCatalogs: SharedCatalogEntry[]
}

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

export async function getCatalog(id: number): Promise<QuizCatalogDetail> {
    const res = await client.get<QuizCatalogDetail>(`/quiz/catalogs/${id}`)
    return res.data
}

export async function createCatalog(data: { name: string; description?: string; trainingEnabled?: boolean }): Promise<QuizCatalog> {
    const res = await client.post<QuizCatalog>('/quiz/catalogs', data)
    return res.data
}

export async function updateCatalog(id: number, data: { name: string; description?: string; trainingEnabled?: boolean }): Promise<QuizCatalog> {
    const res = await client.put<QuizCatalog>(`/quiz/catalogs/${id}`, data)
    return res.data
}

export async function deleteCatalog(id: number): Promise<void> {
    await client.delete(`/quiz/catalogs/${id}`)
}

// -- Categories (station-scoped) --

export async function listCategories(): Promise<QuizCategory[]> {
    const res = await client.get<QuizCategory[]>('/quiz/categories')
    return res.data
}

export async function createCategory(data: { name: string; description?: string; position?: number }): Promise<QuizCategory> {
    const res = await client.post<QuizCategory>('/quiz/categories', data)
    return res.data
}

export async function updateCategory(id: number, data: { name: string; description?: string; position?: number }): Promise<void> {
    await client.put(`/quiz/categories/${id}`, data)
}

export async function deleteCategory(id: number): Promise<void> {
    await client.delete(`/quiz/categories/${id}`)
}

// -- Questions --

export async function listQuestions(catalogId: number): Promise<QuizQuestion[]> {
    const res = await client.get<QuizQuestion[]>(`/quiz/catalogs/${catalogId}/questions`)
    return res.data
}

export async function getQuestion(id: number): Promise<QuizQuestion> {
    const res = await client.get<QuizQuestion>(`/quiz/questions/${id}`)
    return res.data
}

export async function createQuestion(catalogId: number, data: Record<string, unknown>): Promise<QuizQuestion> {
    const res = await client.post<QuizQuestion>(`/quiz/catalogs/${catalogId}/questions`, data)
    return res.data
}

export async function updateQuestion(id: number, data: Record<string, unknown>): Promise<QuizQuestion> {
    const res = await client.put<QuizQuestion>(`/quiz/questions/${id}`, data)
    return res.data
}

export async function deleteQuestion(id: number): Promise<void> {
    await client.delete(`/quiz/questions/${id}`)
}

// -- Tests --

export async function listTests(): Promise<QuizTestSummary[]> {
    const res = await client.get<QuizTestSummary[]>('/quiz/tests')
    return res.data
}

export async function listAvailableTests(): Promise<QuizTest[]> {
    const res = await client.get<QuizTest[]>('/quiz/tests/available')
    return res.data
}

export async function getTest(id: number): Promise<QuizTestDetail> {
    const res = await client.get<QuizTestDetail>(`/quiz/tests/${id}`)
    return res.data
}

export async function createTest(data: { title: string; description?: string; timeLimit?: number | null; shuffle?: boolean }): Promise<QuizTest> {
    const res = await client.post<QuizTest>('/quiz/tests', data)
    return res.data
}

export async function updateTest(id: number, data: Record<string, unknown>): Promise<QuizTest> {
    const res = await client.put<QuizTest>(`/quiz/tests/${id}`, data)
    return res.data
}

export async function deleteTest(id: number): Promise<void> {
    await client.delete(`/quiz/tests/${id}`)
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

export async function getRestrictions(testId: number): Promise<{ roleIds: number[]; groupIds: number[]; tagIds: number[] }> {
    const res = await client.get<{ roleIds: number[]; groupIds: number[]; tagIds: number[] }>(`/quiz/tests/${testId}/restrictions`)
    return res.data
}

export async function setRestrictions(testId: number, data: { roleIds: number[]; groupIds: number[]; tagIds: number[] }): Promise<void> {
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
    const base = `${client.defaults.baseURL}/quiz/questions/${questionId}/image`
    return size ? `${base}?size=${size}` : base
}

export async function uploadQuestionImage(questionId: number, file: File): Promise<void> {
    const formData = new FormData()
    formData.append('image', file)
    await client.post(`/quiz/questions/${questionId}/image`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    })
}

export async function deleteQuestionImage(questionId: number): Promise<void> {
    await client.delete(`/quiz/questions/${questionId}/image`)
}

// -- PDF Export --

export async function downloadQuestionPdf(testId: number): Promise<void> {
    const res = await client.get(`/quiz/tests/${testId}/export/questions`, { responseType: 'blob' })
    downloadBlob(res)
}

export async function downloadSolutionPdf(testId: number): Promise<void> {
    const res = await client.get(`/quiz/tests/${testId}/export/solutions`, { responseType: 'blob' })
    downloadBlob(res)
}

function downloadBlob(res: { data: Blob; headers: { [key: string]: unknown } }) {
    const disposition = String(res.headers['content-disposition'] ?? '')
    const match = disposition.match(/filename="?([^"]+)"?/)
    const filename = match?.[1] ?? 'download'
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    a.click()
    URL.revokeObjectURL(url)
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
    const formData = new FormData()
    formData.append('file', file)
    formData.append('mappings', JSON.stringify(mappings))
    const res = await client.post<{ imported: number }>(`/quiz/catalogs/${catalogId}/import-csv`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
    })
    return res.data
}
