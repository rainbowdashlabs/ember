/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Whether a request may not be sent again yet: the server answers with the moment it may, a day after
 * it last went out, and refuses before then.
 */
export function sendAgainTooSoon(sendAgainFrom: string | null): boolean {
    return sendAgainFrom !== null && new Date(sendAgainFrom).getTime() > Date.now()
}
