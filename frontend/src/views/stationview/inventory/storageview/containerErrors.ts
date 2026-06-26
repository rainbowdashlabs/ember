/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ComposerTranslation} from 'vue-i18n'

const SERVER_TO_KEY: Array<[RegExp, string]> = [
    [/cannot be moved under one of its descendants/i, 'inventory.storage.errors.cycle'],
    [/name may not contain '\/'/i, 'inventory.storage.errors.nameSlash'],
    [/name is required/i, 'inventory.storage.errors.nameRequired'],
]

export function mapContainerError(t: ComposerTranslation, e: unknown, fallbackKey: string): string {
    const serverMessage = (e as {response?: {data?: {message?: unknown}}})?.response?.data?.message
    const message = typeof serverMessage === 'string'
        ? serverMessage
        : (e as {message?: unknown})?.message
    if (typeof message === 'string' && message.length > 0) {
        for (const [pattern, key] of SERVER_TO_KEY) {
            if (pattern.test(message)) return t(key)
        }
    }
    return t(fallbackKey)
}
