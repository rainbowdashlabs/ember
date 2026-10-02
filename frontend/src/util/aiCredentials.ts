/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {getAiCredential, getSettings} from '@/api/ai'
import {AiVendor} from '@/api/generated/schema'

/**
 * Which AI provider and model generation asks, and whether there is a key to ask with at all.
 *
 * The key itself never reaches the browser: the server uses the person's own key where they keep
 * one, and the station's otherwise.
 */
export interface AiCredentials {
    provider: AiVendor
    model: string
    available: boolean
}

/** The keys under which earlier versions kept the AI settings, the key among them, in the browser. */
const LEGACY_AI_KEYS = ['ai_provider', 'ai_model', 'ai_api_key']

/**
 * Asks the server which provider generation will use. The person's own key comes first; without
 * one, the first provider the station keeps a key for. {@code available} false means generation
 * must not be attempted.
 */
export async function loadAiCredentials(): Promise<AiCredentials> {
    const personal = await getAiCredential()
    if (personal.usable && personal.provider) {
        return {provider: personal.provider, model: personal.model ?? '', available: true}
    }
    const station = (await getSettings().catch(() => null))?.providers[0]
    if (station) return {provider: station.provider, model: station.model ?? '', available: true}
    return {provider: AiVendor.OPENAI, model: '', available: false}
}

/**
 * Removes the AI settings an earlier version kept in the browser. The key is a secret that has no
 * business lying around in local storage, and the server holds the settings now.
 */
export function forgetLegacyAiSettings(): void {
    if (typeof localStorage === 'undefined') return
    for (const key of LEGACY_AI_KEYS) localStorage.removeItem(key)
}
