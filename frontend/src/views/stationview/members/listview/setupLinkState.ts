/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type { StationMember } from '@/api/types'

/**
 * Where a member stands between being entered and having an account of their own.
 *
 * <p>Four states rather than the two the list used to draw. A setup mail that was never sent and one
 * whose link has run out both leave the account unclaimed, and they ask for different things: the
 * first for the mail to go out, the second for it to go out again. How long a link lasts is set by
 * whoever runs the instance, so the answer comes from the date carried on the row and is never
 * counted out here.
 */
export const SetupLink = {
    DONE: 'DONE',
    NEVER_SENT: 'NEVER_SENT',
    VALID: 'VALID',
    EXPIRED: 'EXPIRED',
} as const

export type SetupLinkState = (typeof SetupLink)[keyof typeof SetupLink]

/**
 * The state of the setup link for one member, read off the row the list already holds.
 *
 * @param member the row
 * @param now    the moment to judge the expiry against, the present unless a test says otherwise
 */
export function setupLinkState(member: StationMember, now: Date = new Date()): SetupLinkState {
    if (!member.accountSetupPending) return SetupLink.DONE
    if (!member.setupMailExpiresAt) return SetupLink.NEVER_SENT
    const expiresAt = new Date(member.setupMailExpiresAt)
    if (Number.isNaN(expiresAt.getTime())) return SetupLink.NEVER_SENT
    return expiresAt.getTime() <= now.getTime() ? SetupLink.EXPIRED : SetupLink.VALID
}
