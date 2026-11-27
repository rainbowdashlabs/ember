/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    OnboardingLevel,
    OnboardingTaskState,
    type OnboardingStatus,
    type OnboardingTaskView,
} from '@/api/generated/schema'

/** The most first steps shown at once, counted over every card on a page. */
export const VISIBLE_TASKS = 3

/** The tasks of every level loaded so far, as the shared onboarding state keeps them. */
export type LoadedLevels = Partial<Record<OnboardingLevel, OnboardingStatus>>

/** One task in the window, with the level it belongs to, which is where it is marked. */
export interface WindowTask {
    level: OnboardingLevel
    task: OnboardingTaskView
}

/**
 * The levels that share a page with this one, in the order their tasks take the free slots.
 *
 * The dashboard shows the station's setup above the reader's own first steps, and the station goes
 * first: a station that cannot send mail or has no members yet is what everything else waits for.
 * The instance has the administration area to itself.
 */
export function pageLevels(level: OnboardingLevel): OnboardingLevel[] {
    return level === OnboardingLevel.INSTANCE
        ? [OnboardingLevel.INSTANCE]
        : [OnboardingLevel.STATION, OnboardingLevel.MEMBER]
}

function openTasks(loaded: LoadedLevels, levels: OnboardingLevel[]): WindowTask[] {
    return levels.flatMap(level => (loaded[level]?.tasks ?? [])
        .filter(task => task.state === OnboardingTaskState.OPEN)
        .map(task => ({level, task})))
}

/**
 * The few tasks a page shows: the first ready ones, level by level in catalogue order. A task still
 * waiting for another is never among them, however few are ready.
 */
export function visibleTasks(loaded: LoadedLevels, levels: OnboardingLevel[]): WindowTask[] {
    return openTasks(loaded, levels)
        .filter(entry => entry.task.ready)
        .slice(0, VISIBLE_TASKS)
}

/** How many open tasks of the page are not shown, held back or simply behind the first few. */
export function hiddenCount(loaded: LoadedLevels, levels: OnboardingLevel[]): number {
    return openTasks(loaded, levels).length - visibleTasks(loaded, levels).length
}
