/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface RequirementItem {
    id: number
    title: string
}

/**
 * A self-check waiting to be answered, one's own or one held for somebody in one's care.
 *
 * <p>It counts towards the badge and shows on the list, and it never stands in the doorway: a task
 * due in four weeks must not meet the reader with a wall every time they sign in.
 */
export interface SelfCheckRequirement {
    id: number
    memberId: number
    dueOn?: string | null
}

export interface RequirementsResponse {
    forcedForms: RequirementItem[]
    forcedQuizzes: RequirementItem[]
    profileIncomplete: boolean
    selfChecks: SelfCheckRequirement[]
}

export async function getRequirements(): Promise<RequirementsResponse> {
    const res = await client.get<RequirementsResponse>('/requirements')
    return res.data
}
