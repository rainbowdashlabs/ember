/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {SetupStatus} from './generated/schema'

export async function getStatus(): Promise<SetupStatus> {
    const res = await client.get<SetupStatus>('/station/setup/status')
    return res.data
}

export async function complete(): Promise<SetupStatus> {
    const res = await client.post<SetupStatus>('/station/setup/complete')
    return res.data
}
