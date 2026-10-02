/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {RequirementsResponse} from './generated/schema'

export async function getRequirements(): Promise<RequirementsResponse> {
    const res = await client.get<RequirementsResponse>('/requirements')
    return res.data
}
