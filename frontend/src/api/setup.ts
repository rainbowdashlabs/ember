/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface SetupStep {
    id: string
    complete: boolean
    applicable: boolean
}

export interface SetupStatus {
    completedAt: string | null
    requiredSteps: SetupStep[]
    optionalSteps: SetupStep[]
}

export interface MissingStepsResponse {
    missingSteps: string[]
}

export async function getStatus(): Promise<SetupStatus> {
    const res = await client.get<SetupStatus>('/station/setup/status')
    return res.data
}

export async function complete(): Promise<SetupStatus> {
    const res = await client.post<SetupStatus>('/station/setup/complete')
    return res.data
}
