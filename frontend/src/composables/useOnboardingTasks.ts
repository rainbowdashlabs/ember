/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, readonly} from 'vue'
import {onboarding} from '@/api'
import {
    OnboardingLevel,
    OnboardingTaskState,
    type OnboardingLevelName,
    type OnboardingTaskStateName,
} from '@/api/onboarding'
import type {OnboardingStatus, OnboardingTaskView} from '@/api/generated/schema'
import {flowFor} from '@/util/onboardingFlows'
import {clearActiveTask, onboardingState} from '@/util/onboardingState'

/**
 * The tasks Ember still asks of the reader, and the one being walked through right now.
 *
 * Nothing here decides whether a task is done. That is worked out on the server from the data
 * itself, so a task comes back when the thing behind it is undone, and reloading is what shows it.
 */
export function useOnboardingTasks() {
    const loading = useState('useOnboardingTasks.loading', () => false)
    const state = onboardingState()
    const {activeLevel, activeStep, activeTaskId, activeTaskKey, guideDismissed, onboardingStatus} = state

    const memberTasks = computed(() => onboardingStatus.value.MEMBER?.tasks ?? [])
    const stationTasks = computed(() => onboardingStatus.value.STATION?.tasks ?? [])
    const instanceTasks = computed(() => onboardingStatus.value.INSTANCE?.tasks ?? [])

    function openOf(level: OnboardingLevelName): OnboardingTaskView[] {
        return (onboardingStatus.value[level]?.tasks ?? []).filter(t => t.state === OnboardingTaskState.OPEN)
    }

    /**
     * Puts one level's answer beside the others.
     *
     * The merge happens after the answer is in hand, never around the wait for it: two levels asked
     * at once would otherwise both copy the same empty state and the later answer would wipe the
     * earlier one, which is what left a station manager looking at their own tasks and none of the
     * station's.
     */
    function keep(level: OnboardingLevelName, status: OnboardingStatus | undefined) {
        onboardingStatus.value = {...onboardingStatus.value, [level]: status}
    }

    async function load(level: OnboardingLevelName) {
        loading.value = true
        try {
            keep(level, await onboarding.getTasks(level))
        } catch {
            keep(level, undefined)
        } finally {
            loading.value = false
        }
    }

    async function mark(level: OnboardingLevelName, taskId: string, state: OnboardingTaskStateName) {
        keep(level, await onboarding.markTask(level, taskId, state))
        if (activeTaskId.value === taskId && state !== OnboardingTaskState.OPEN) stop()
    }

    /** Ticks off a task Ember cannot see for itself. */
    const confirm = (level: OnboardingLevelName, taskId: string) => mark(level, taskId, OnboardingTaskState.DONE)

    const skip = (level: OnboardingLevelName, taskId: string) => mark(level, taskId, OnboardingTaskState.SKIPPED)

    /**
     * Throws a passed-over task away for good. It leaves the list and does not come back, which is
     * how a list of first steps is finally done with rather than carrying its leftovers forever.
     */
    const discard = (level: OnboardingLevelName, taskId: string) => mark(level, taskId, OnboardingTaskState.DISMISSED)

    /** Puts a skipped task back on the list, which anyone sharing it may do. */
    const resume = (level: OnboardingLevelName, taskId: string) => mark(level, taskId, OnboardingTaskState.OPEN)

    /**
     * Begins walking a task. The guide takes over from here: it lights up the first step and moves
     * on as the reader does the work, and going somewhere else in between breaks nothing.
     *
     * Nothing is navigated. Walking somebody to the page they were meant to find is the one thing
     * this tour must not do: the first step points at the way there, and the reader takes it.
     */
    function start(level: OnboardingLevelName, task: OnboardingTaskView) {
        if (flowFor(task.key).length === 0) return
        activeLevel.value = level
        activeTaskId.value = task.id
        activeTaskKey.value = task.key
        activeStep.value = 0
        guideDismissed.value = false
    }

    /**
     * Starts the first open task that has a way to be walked, which is what the resume button does.
     * A task that happens outside Ember, such as putting a bookmark somewhere, is passed over here:
     * there would be nothing to point at.
     */
    function startNext(level: OnboardingLevelName) {
        const next = openOf(level).find(task => flowFor(task.key).length > 0)
        if (next) start(level, next)
    }

    function stop() {
        clearActiveTask(state)
    }

    /** Brings back the guide the reader waved away, on the task it was walking. */
    function reopenGuide() {
        guideDismissed.value = false
    }

    return {
        loading: readonly(loading),
        status: readonly(onboardingStatus),
        memberTasks,
        stationTasks,
        instanceTasks,
        activeTaskId: readonly(activeTaskId),
        activeTaskKey: readonly(activeTaskKey),
        activeLevel: readonly(activeLevel),
        activeStep: readonly(activeStep),
        guideDismissed: readonly(guideDismissed),
        reopenGuide,
        openOf,
        load,
        confirm,
        skip,
        discard,
        resume,
        start,
        startNext,
        stop,
        levels: OnboardingLevel,
    }
}
