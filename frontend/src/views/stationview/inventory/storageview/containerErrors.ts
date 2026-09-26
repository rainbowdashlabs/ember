/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ComposerTranslation} from 'vue-i18n'
import {describeFailure, type Failure} from '@/util/failure'

const SERVER_TO_KEY: Array<[RegExp, string]> = [
    [/cannot be moved under one of its descendants/i, 'inventory.storage.errors.cycle'],
    [/name may not contain '\/'/i, 'inventory.storage.errors.nameSlash'],
    [/name is required/i, 'inventory.storage.errors.nameRequired'],
]

/** The rule the server named, in the reader's language, or nothing where it named none we know. */
function namedRule(t: ComposerTranslation, e: unknown): string | undefined {
    const serverMessage = (e as {response?: {data?: {message?: unknown}}})?.response?.data?.message
    const message = typeof serverMessage === 'string'
        ? serverMessage
        : (e as {message?: unknown})?.message
    if (typeof message !== 'string' || message.length === 0) return undefined
    for (const [pattern, key] of SERVER_TO_KEY) {
        if (pattern.test(message)) return t(key)
    }
    return undefined
}

/**
 * The same refusal, described, for the places with room to say what to do about it.
 *
 * <p>Where the server named a rule this screen knows, that sentence wins and nothing is offered to
 * report: a shelf that cannot be put inside itself is the product working as intended, and a bug
 * report filed against it buries the real ones.
 *
 * <p>Everything else is described as it stands. No screen wording is put over the top, because what
 * a described failure says instead, whose problem this is and what to do next, is the more useful of
 * the two on a page that is about one shelf anyway.
 *
 * @param t the translator
 * @param e the thing that was thrown
 * @return the refusal, described
 */
export function mapContainerFailure(t: ComposerTranslation, e: unknown): Failure {
    const described = describeFailure(e, t)
    const rule = namedRule(t, e)
    return rule ? {...described, message: rule, reportable: false} : described
}
