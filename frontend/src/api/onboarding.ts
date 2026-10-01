/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {components, OnboardingStatus} from './generated/schema'

type Schemas = components['schemas']

export type OnboardingLevelName = Schemas['OnboardingLevel']

export const OnboardingLevel = {
    MEMBER: 'MEMBER',
    STATION: 'STATION',
    INSTANCE: 'INSTANCE',
} as const satisfies Record<OnboardingLevelName, OnboardingLevelName>

export type OnboardingTaskStateName = Schemas['OnboardingTaskState']

export const OnboardingTaskState = {
    OPEN: 'OPEN',
    DONE: 'DONE',
    SKIPPED: 'SKIPPED',
    /** Thrown away for good. Sent to the server, never received: such a task is not listed again. */
    DISMISSED: 'DISMISSED',
} as const satisfies Record<OnboardingTaskStateName, OnboardingTaskStateName>

const path: Record<OnboardingLevelName, string> = {
    MEMBER: '/onboarding/member',
    STATION: '/onboarding/station',
    INSTANCE: '/onboarding/instance',
}

export async function getTasks(level: OnboardingLevelName): Promise<OnboardingStatus> {
    const res = await client.get<OnboardingStatus>(path[level])
    return res.data
}

export async function markTask(
    level: OnboardingLevelName,
    taskId: string,
    state: OnboardingTaskStateName,
): Promise<OnboardingStatus> {
    const res = await client.put<OnboardingStatus>(`${path[level]}/${encodeURIComponent(taskId)}`, {state})
    return res.data
}
