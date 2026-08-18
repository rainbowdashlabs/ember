/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {ActiveSession} from '@/api/session'
import type {PitchTrust} from './pitchTypes'

/**
 * What a member sees about their own account: where they are logged in, and what they can take
 * with them or have deleted. Both go to the application's own sections.
 */
function hoursAgo(hours: number): string {
    const date = new Date()
    date.setHours(date.getHours() - hours)
    return date.toISOString()
}

const SESSIONS: ActiveSession[] = [
    {
        id: 1, userAgent: 'Firefox 141 · Linux', location: 'Musterstadt',
        createdAt: hoursAgo(3), lastUsedAt: hoursAgo(0), expiresAt: hoursAgo(-24 * 30), isCurrent: true,
    },
    {
        id: 2, userAgent: 'Safari · iPhone', location: 'Musterstadt',
        createdAt: hoursAgo(72), lastUsedAt: hoursAgo(14), expiresAt: hoursAgo(-24 * 27),
    },
    {
        id: 3, userAgent: 'Chrome · Windows', location: 'Talbach',
        createdAt: hoursAgo(24 * 9), lastUsedAt: hoursAgo(24 * 4), expiresAt: hoursAgo(-24 * 21),
    },
]

export const TRUST_SESSIONS: PitchTrust = {sessions: SESSIONS}

export const TRUST_GDPR: PitchTrust = {
    gdpr: {managedMembers: [{id: 1, name: 'Lena Sommer'}, {id: 2, name: 'Tim Sommer'}]},
}
