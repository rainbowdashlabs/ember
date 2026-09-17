/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/**
 * The thing a story went looking for, or a failure that says what was missing.
 *
 * <p>Most of these searches are a `find` over what the instance answered, and a story that carries
 * on without checking fails several lines later on a property of nothing, naming the property rather
 * than the search. This says which search came back empty, and is what lets the suite be
 * type-checked at all: a seeded row a story is sure of is still a row the types know might be
 * missing.
 *
 * @param value what was found, if anything
 * @param what  what was being looked for, in the words of the story
 */
export function must<T>(value: T | null | undefined, what: string): T {
    if (value === null || value === undefined) throw new Error(`The story needed ${what}, and there was none`)
    return value
}
