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
    type OnboardingStatus,
    type OnboardingTaskView,
} from '@/api/generated/schema'
import {flowFor} from '@/util/onboardingFlows'
import {hiddenCount, pageLevels, visibleTasks, type WindowTask} from '@/util/onboardingWindow'
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

    function openOf(level: OnboardingLevel): OnboardingTaskView[] {
        return (onboardingStatus.value[level]?.tasks ?? []).filter(t => t.state === OnboardingTaskState.OPEN)
    }

    /** The few tasks the page of this level shows, across every card on it. */
    function windowOf(level: OnboardingLevel): WindowTask[] {
        return visibleTasks(onboardingStatus.value, pageLevels(level))
    }

    /** The tasks of this level that are in its page's window, which is all its card lists. */
    function shownOf(level: OnboardingLevel): OnboardingTaskView[] {
        return windowOf(level).filter(entry => entry.level === level).map(entry => entry.task)
    }

    /** How many open tasks of the page wait behind the ones shown. */
    function hiddenOnPageOf(level: OnboardingLevel): number {
        return hiddenCount(onboardingStatus.value, pageLevels(level))
    }

    /** Whether anything on the page of this level is still open, on whichever card. */
    function anythingOpenOnPageOf(level: OnboardingLevel): boolean {
        return pageLevels(level).some(pageLevel => openOf(pageLevel).length > 0)
    }

    /**
     * Whether this level's card is the last one drawn on its page, which is where the line about the
     * tasks still to come belongs. A card is drawn while it has an open or a skipped task.
     */
    function lastCardOnPage(level: OnboardingLevel): boolean {
        const drawn = pageLevels(level).filter(pageLevel => (onboardingStatus.value[pageLevel]?.tasks ?? [])
            .some(task => task.state === OnboardingTaskState.OPEN || task.state === OnboardingTaskState.SKIPPED))
        return drawn.at(-1) === level
    }

    /**
     * Puts one level's answer beside the others.
     *
     * The merge happens after the answer is in hand, never around the wait for it: two levels asked
     * at once would otherwise both copy the same empty state and the later answer would wipe the
     * earlier one, which is what left a station manager looking at their own tasks and none of the
     * station's.
     */
    function keep(level: OnboardingLevel, status: OnboardingStatus | undefined) {
        onboardingStatus.value = {...onboardingStatus.value, [level]: status}
    }

    async function load(level: OnboardingLevel) {
        loading.value = true
        try {
            keep(level, await onboarding.getTasks(level))
        } catch {
            keep(level, undefined)
        } finally {
            loading.value = false
        }
    }

    async function mark(level: OnboardingLevel, taskId: string, state: OnboardingTaskState) {
        keep(level, await onboarding.markTask(level, taskId, state))
        if (activeTaskId.value === taskId && state !== OnboardingTaskState.OPEN) stop()
    }

    /** Ticks off a task Ember cannot see for itself. */
    const confirm = (level: OnboardingLevel, taskId: string) => mark(level, taskId, OnboardingTaskState.DONE)

    const skip = (level: OnboardingLevel, taskId: string) => mark(level, taskId, OnboardingTaskState.SKIPPED)

    /**
     * Throws a passed-over task away for good. It leaves the list and does not come back, which is
     * how a list of first steps is finally done with rather than carrying its leftovers forever.
     */
    const discard = (level: OnboardingLevel, taskId: string) => mark(level, taskId, OnboardingTaskState.DISMISSED)

    /** Puts a skipped task back on the list, which anyone sharing it may do. */
    const resume = (level: OnboardingLevel, taskId: string) => mark(level, taskId, OnboardingTaskState.OPEN)

    /**
     * Begins walking a task. The guide takes over from here: it lights up the first step and moves
     * on as the reader does the work, and going somewhere else in between breaks nothing.
     *
     * Nothing is navigated. Walking somebody to the page they were meant to find is the one thing
     * this tour must not do: the first step points at the way there, and the reader takes it.
     */
    function start(level: OnboardingLevel, task: OnboardingTaskView) {
        if (flowFor(task.key).length === 0) return
        activeLevel.value = level
        activeTaskId.value = task.id
        activeTaskKey.value = task.key
        activeStep.value = 0
        guideDismissed.value = false
    }

    /**
     * Starts the first task shown on the page of this level that has a way to be walked, which is
     * what the resume button does. It may belong to another card on the same page. A task that
     * happens outside Ember, such as putting a bookmark somewhere, is passed over here: there would
     * be nothing to point at. A task held back or behind the first few is never started.
     */
    function startNext(level: OnboardingLevel) {
        const next = windowOf(level).find(entry => flowFor(entry.task.key).length > 0)
        if (next) start(next.level, next.task)
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
        shownOf,
        hiddenOnPageOf,
        anythingOpenOnPageOf,
        lastCardOnPage,
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
