/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface UpdateStatus {
    /** The version this instance runs. */
    currentVersion: string
    /** The newest release found, null where no check has succeeded. */
    latestVersion: string | null
    /** Whether the newest release is ahead of the running one. */
    updateAvailable: boolean
}

export async function getUpdateStatus(): Promise<UpdateStatus> {
    const res = await client.get<UpdateStatus>('/system/update')
    return res.data
}
