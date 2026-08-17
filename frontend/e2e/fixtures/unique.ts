/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */

/**
 * A name no other worker will produce. Every story that creates something uses one, because the
 * suite runs fully parallel against one seeded database and two workers creating "Test-Ticket"
 * would otherwise assert on each other's row.
 */
let counter = 0

export function unique(prefix: string): string {
    const worker = process.env.TEST_WORKER_INDEX ?? '0'
    counter += 1
    return `${prefix}-w${worker}-${counter}-${Date.now().toString(36)}`
}

/**
 * A short uppercase key, for the places that ask for one and insist it is unique - a board is
 * addressed by its key, so two runs picking the same one would collide.
 */
export function uniqueKey(): string {
    const worker = process.env.TEST_WORKER_INDEX ?? '0'
    counter += 1
    return `T${worker}${counter}${Date.now().toString(36).slice(-3)}`.toUpperCase()
}
