/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    AiCredentialRequest,
    AiCredentialSummary,
    AiGenerateRequest,
    AiGenerateResponse,
    AiPromptRequest,
    AiProviderRequest,
    AiSettingsResponse,
    BatchGenerateRequest,
    components,
    BatchResult,
    GeneratedQuestionWithMeta,
    GenerateQuestionsRequest,
    GenerationPollResponse,
    JobIdResponse,
    ModelInfo,
    TransientKeyRequest,
} from './generated/schema'

export type AiVendorName = components['schemas']['AiVendor']

/** The AI providers generation can ask, in the order a picker offers them. */
export const AiVendor = {
    OPENAI: 'OPENAI',
    GEMINI: 'GEMINI',
    CLAUDE: 'CLAUDE',
    DEEPSEEK: 'DEEPSEEK',
    MISTRAL: 'MISTRAL',
} as const satisfies Record<AiVendorName, AiVendorName>

/**
 * What a person may see of their own AI key: never the key itself, only which provider and model it
 * is for and its last four characters. {@code usable} is false where no key is stored, or where the
 * stored one no longer opens and has to be entered again.
 */
export async function getAiCredential(): Promise<AiCredentialSummary> {
    const res = await client.get<AiCredentialSummary>('/account/ai-credential')
    return res.data
}

export async function saveAiCredential(data: AiCredentialRequest): Promise<AiCredentialSummary> {
    const res = await client.put<AiCredentialSummary>('/account/ai-credential', data)
    return res.data
}

export async function deleteAiCredential(): Promise<void> {
    await client.delete('/account/ai-credential')
}

export async function getSettings(): Promise<AiSettingsResponse> {
    const res = await client.get<AiSettingsResponse>('/ai/settings')
    return res.data
}

export async function savePrompt(prompt: string): Promise<void> {
    const request: AiPromptRequest = {prompt}
    await client.put('/ai/settings/prompt', request)
}

export async function saveProvider(provider: AiVendorName, apiKey: string, model?: string | null): Promise<void> {
    const request: AiProviderRequest = {apiKey, model}
    await client.put(`/ai/providers/${provider}`, request)
}

export async function deleteProvider(provider: AiVendorName): Promise<void> {
    await client.delete(`/ai/providers/${provider}`)
}

export async function fetchModels(provider: AiVendorName, apiKey?: string | null): Promise<ModelInfo[]> {
    const request: TransientKeyRequest = {apiKey: apiKey ?? null}
    const res = await client.post<ModelInfo[]>(`/ai/providers/${provider}/models`, request)
    return res.data
}

export async function generate(data: AiGenerateRequest): Promise<string[]> {
    const res = await client.post<AiGenerateResponse>('/ai/generate', data)
    return res.data.answers
}

export async function startGenerateQuestions(data: GenerateQuestionsRequest): Promise<string> {
    const res = await client.post<JobIdResponse>('/ai/generate-questions', data)
    return res.data.jobId
}

export async function pollGenerateQuestions(jobId: string): Promise<GenerationPollResponse> {
    const res = await client.get<GenerationPollResponse>(`/ai/generate-questions/${jobId}`)
    return res.data
}

export async function generateQuestions(data: GenerateQuestionsRequest): Promise<GeneratedQuestionWithMeta[]> {
    const jobId = await startGenerateQuestions(data)
    const allQuestions: GeneratedQuestionWithMeta[] = []
    while (true) {
        await new Promise(r => setTimeout(r, 2000))
        const poll = await pollGenerateQuestions(jobId)
        allQuestions.push(...poll.questions)
        if (poll.done) break
    }
    return allQuestions
}

/**
 * The settings of a generated question, which the generator hands over as JSON text. Saving a
 * question takes them as an object: sent as text they would be stored as a string and read back as
 * no settings at all.
 */
export function generatedConfig(config: string): Record<string, unknown> {
    try {
        const parsed: unknown = JSON.parse(config)
        return typeof parsed === 'object' && parsed !== null && !Array.isArray(parsed) ? {...parsed} : {}
    } catch {
        return {}
    }
}

export async function batchGenerate(catalogId: number, data: BatchGenerateRequest): Promise<BatchResult> {
    const res = await client.post<BatchResult>(`/ai/batch-generate/${catalogId}`, data)
    return res.data
}
