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

export interface RequirementsResponse {
    forcedForms: RequirementItem[]
    forcedQuizzes: RequirementItem[]
    profileIncomplete: boolean
}

export async function getRequirements(): Promise<RequirementsResponse> {
    const res = await client.get<RequirementsResponse>('/requirements')
    return res.data
}
