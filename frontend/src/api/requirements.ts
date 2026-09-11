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

/**
 * A registration short of an answer to a question its appointment gained after the sign-up.
 *
 * <p>Counted and listed like a self-check, and just as unable to stand in the doorway. The member
 * name is carried only when the registration is not the reader's own, so a guardian can tell the
 * members in their care apart.
 */
export interface RegistrationUpdateRequirement {
    registrationId: number
    eventId: number
    eventName: string
    eventDate: string
    memberId: number
    memberName?: string | null
}

export interface RequirementsResponse {
    forcedForms: RequirementItem[]
    forcedQuizzes: RequirementItem[]
    profileIncomplete: boolean
    selfChecks: SelfCheckRequirement[]
    registrationUpdates: RegistrationUpdateRequirement[]
}

export async function getRequirements(): Promise<RequirementsResponse> {
    const res = await client.get<RequirementsResponse>('/requirements')
    return res.data
}
