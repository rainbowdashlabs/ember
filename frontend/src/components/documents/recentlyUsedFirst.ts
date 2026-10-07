/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** A template as a picker lists it: by name, with when it was last generated from, where it ever was. */
export interface UsedTemplate {
    name: string
    lastUsedAt?: string | null
}

function usedAt(template: UsedTemplate): number {
    return template.lastUsedAt ? Date.parse(template.lastUsedAt) : Number.NEGATIVE_INFINITY
}

/**
 * The templates a picker offers, the most recently used first, so the one generated last is at hand.
 * Templates never used follow, and templates used at the same moment or never are ordered by name.
 *
 * @param templates the templates, in any order
 * @returns a new list in picker order
 */
export function recentlyUsedFirst<T extends UsedTemplate>(templates: readonly T[]): T[] {
    return [...templates].sort((a, b) => usedAt(b) - usedAt(a) || a.name.localeCompare(b.name, 'de'))
}
