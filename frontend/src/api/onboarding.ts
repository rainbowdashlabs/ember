/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export const OnboardingLevel = {
    MEMBER: 'MEMBER',
    STATION: 'STATION',
    INSTANCE: 'INSTANCE',
} as const

export type OnboardingLevelName = (typeof OnboardingLevel)[keyof typeof OnboardingLevel]

export const OnboardingTaskState = {
    OPEN: 'OPEN',
    DONE: 'DONE',
    SKIPPED: 'SKIPPED',
    /** Thrown away for good. Sent to the server, never received: such a task is not listed again. */
    DISMISSED: 'DISMISSED',
} as const

export type OnboardingTaskStateName = (typeof OnboardingTaskState)[keyof typeof OnboardingTaskState]

/** One task as its reader sees it. */
export interface OnboardingTaskView {
    /** What is sent back to tick the task off or pass it over. Carries the member it is about where a task repeats. */
    id: string
    /** The catalogue key, which is what the text is looked up under. */
    key: string
    /** The first name of the member the task is about, or null. */
    subject: string | null
    /** The member the task is about, or null. */
    subjectId: number | null
    state: OnboardingTaskStateName
    /** Whether it can be ticked off by hand. A task read from the data cannot. */
    confirmable: boolean
    /** Who settled it, on the shared levels only. */
    actorName: string | null
    changedAt: string | null
}

export interface OnboardingStatus {
    level: OnboardingLevelName
    tasks: OnboardingTaskView[]
    open: number
    done: number
    skipped: number
}

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
