/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface AiProviderConfig {
    id: number
    stationId: string
    provider: string
    apiKey: string
    model: string | null
}

export interface AiSettings {
    providers: AiProviderConfig[]
    prompt: string
    defaultPrompt: string
}

export interface AiModel {
    id: string
    name: string
}

export async function getSettings(): Promise<AiSettings> {
    const res = await client.get<AiSettings>('/ai/settings')
    return res.data
}

export async function savePrompt(prompt: string): Promise<void> {
    await client.put('/ai/settings/prompt', { prompt })
}

export async function saveProvider(provider: string, apiKey: string, model?: string | null): Promise<void> {
    await client.put(`/ai/providers/${provider}`, { apiKey, model })
}

export async function deleteProvider(provider: string): Promise<void> {
    await client.delete(`/ai/providers/${provider}`)
}

export async function fetchModels(provider: string, apiKey?: string | null): Promise<AiModel[]> {
    const res = await client.post<AiModel[]>(`/ai/providers/${provider}/models`, { apiKey: apiKey ?? null })
    return res.data
}

export async function generate(data: {
    provider: string
    apiKey?: string | null
    model?: string | null
    question: string
    correctAnswer: string
    count?: number
}): Promise<string[]> {
    const res = await client.post<{ answers: string[] }>('/ai/generate', data)
    return res.data.answers
}

export interface GenerateEntry {
    questionType: string
    count: number
    categoryId?: number | null
}

export interface GeneratedQuestion {
    title: string
    config: string
    questionType: string
    categoryId: number | null
}

export async function startGenerateQuestions(data: {
    provider: string
    apiKey?: string | null
    model?: string | null
    userPrompt?: string | null
    locale?: string | null
    catalogId?: number | null
    entries: GenerateEntry[]
}): Promise<string> {
    const res = await client.post<{ jobId: string }>('/ai/generate-questions', data)
    return res.data.jobId
}

export interface GenerationPollResult {
    questions: GeneratedQuestion[]
    done: boolean
}

export async function pollGenerateQuestions(jobId: string): Promise<GenerationPollResult> {
    const res = await client.get<GenerationPollResult>(`/ai/generate-questions/${jobId}`)
    return res.data
}

/** @deprecated Use startGenerateQuestions + pollGenerateQuestions instead */
export async function generateQuestions(data: {
    provider: string
    apiKey?: string | null
    model?: string | null
    userPrompt?: string | null
    locale?: string | null
    catalogId?: number | null
    entries: GenerateEntry[]
}): Promise<GeneratedQuestion[]> {
    const jobId = await startGenerateQuestions(data)
    const allQuestions: GeneratedQuestion[] = []
    while (true) {
        await new Promise(r => setTimeout(r, 2000))
        const poll = await pollGenerateQuestions(jobId)
        allQuestions.push(...poll.questions)
        if (poll.done) break
    }
    return allQuestions
}

export async function batchGenerate(catalogId: number, data: {
    provider: string
    apiKey?: string | null
    model?: string | null
    targetTotalOptions?: number
}): Promise<{ generatedCount: number; errors: string[] }> {
    const res = await client.post<{ generatedCount: number; errors: string[] }>(`/ai/batch-generate/${catalogId}`, data)
    return res.data
}
