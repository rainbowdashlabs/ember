/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Who a stock-taking opens with.
 *
 * <p>A starting point rather than a binding: rows can be struck out afterwards, and somebody the
 * choice left out can be added by hand.
 */
export const IntakeAudience = {
    ALL: 'all',
    USER_TYPE: 'userType',
    GROUP: 'group',
} as const

export type IntakeAudienceName = (typeof IntakeAudience)[keyof typeof IntakeAudience]
