/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getItem} from '@/api/storage'

/** The AI provider settings a member stores locally for question generation. */
export interface AiCredentials {
    provider: string
    apiKey: string
    model: string
}

/**
 * Reads the locally stored AI provider settings. An empty {@code apiKey} means the
 * member has not configured a provider yet and generation must not be attempted.
 */
export function readAiCredentials(): AiCredentials {
    return {
        provider: getItem('ai_provider') || 'openai',
        apiKey: getItem('ai_api_key') || '',
        model: getItem('ai_model') || '',
    }
}
