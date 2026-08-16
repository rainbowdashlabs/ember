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
