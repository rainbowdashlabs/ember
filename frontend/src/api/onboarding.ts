/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {OnboardingLevel, OnboardingStatus, OnboardingTaskState} from './generated/schema'

const path: Record<OnboardingLevel, string> = {
    MEMBER: '/onboarding/member',
    STATION: '/onboarding/station',
    INSTANCE: '/onboarding/instance',
}

export async function getTasks(level: OnboardingLevel): Promise<OnboardingStatus> {
    const res = await client.get<OnboardingStatus>(path[level])
    return res.data
}

/**
 * Sets the state of one task. `DISMISSED` throws a task away for good: it is sent to the server but
 * never received, because such a task is not listed again.
 */
export async function markTask(
    level: OnboardingLevel,
    taskId: string,
    state: OnboardingTaskState,
): Promise<OnboardingStatus> {
    const res = await client.put<OnboardingStatus>(`${path[level]}/${encodeURIComponent(taskId)}`, {state})
    return res.data
}
