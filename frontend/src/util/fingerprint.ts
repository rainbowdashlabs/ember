/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const PAIRS_PER_ROW = 8

/**
 * A certificate fingerprint written as colon-separated pairs, broken into rows of eight pairs, the
 * way it is compared by eye against the same fingerprint printed somewhere else.
 *
 * @param fingerprint the fingerprint, pairs separated by colons
 */
export function fingerprintRows(fingerprint: string): string[] {
    const pairs = fingerprint.split(':')
    const rows: string[] = []
    for (let start = 0; start < pairs.length; start += PAIRS_PER_ROW) {
        rows.push(pairs.slice(start, start + PAIRS_PER_ROW).join(':'))
    }
    return rows
}
