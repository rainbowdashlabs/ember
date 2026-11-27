/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import type {OnboardingLevel, OnboardingStatus, OnboardingTaskView} from '@/api/generated/schema'
import {onboardingState} from '@/util/onboardingState'
import {useOnboardingTasks} from './useOnboardingTasks'

vi.mock('@/api', () => ({
    onboarding: {getTasks: vi.fn(), markTask: vi.fn()},
}))

function task(key: string, overrides: Partial<OnboardingTaskView> = {}): OnboardingTaskView {
    return {
        id: key,
        key,
        subject: null,
        subjectId: null,
        state: 'OPEN',
        confirmable: false,
        actorName: null,
        changedAt: null,
        elsewhere: false,
        ready: true,
        ...overrides,
    }
}

function status(level: OnboardingLevel, tasks: OnboardingTaskView[]): OnboardingStatus {
    return {level, tasks, open: tasks.length, done: 0, skipped: 0}
}

/**
 * The resume button and the end of the setup wizard both start "the next task". It has to be one the
 * page shows, under the level it belongs to, or the walk would point at a task nobody can see and
 * mark it on the wrong list.
 */
describe('useOnboardingTasks.startNext', () => {
    beforeEach(() => {
        const state = onboardingState()
        state.onboardingStatus.value = {}
        state.activeTaskId.value = null
        state.activeTaskKey.value = null
        state.activeLevel.value = null
    })

    it('starts the station task the page shows first, under the station', () => {
        onboardingState().onboardingStatus.value = {
            STATION: status('STATION', [task('station.mail')]),
            MEMBER: status('MEMBER', [task('member.tour')]),
        }
        const tasks = useOnboardingTasks()

        tasks.startNext('MEMBER')

        expect(tasks.activeLevel.value).toBe('STATION')
        expect(tasks.activeTaskId.value).toBe('station.mail')
    })

    it('passes over a task with nothing to point at', () => {
        onboardingState().onboardingStatus.value = {
            MEMBER: status('MEMBER', [task('member.bookmark'), task('member.tour')]),
        }
        const tasks = useOnboardingTasks()

        tasks.startNext('MEMBER')

        expect(tasks.activeTaskId.value).toBe('member.tour')
    })

    it('never starts a task that still waits for another', () => {
        onboardingState().onboardingStatus.value = {
            MEMBER: status('MEMBER', [task('member.bookmark'), task('member.profile', {ready: false})]),
        }
        const tasks = useOnboardingTasks()

        tasks.startNext('MEMBER')

        expect(tasks.activeTaskId.value).toBeNull()
    })

    it('never starts a task behind the first few shown', () => {
        onboardingState().onboardingStatus.value = {
            STATION: status('STATION', [
                task('station.mail', {state: 'DONE'}),
                task('station.memberTypes', {state: 'DONE'}),
            ]),
            MEMBER: status('MEMBER', [
                task('member.bookmark'),
                task('member.bookmark:1', {key: 'member.bookmark'}),
                task('member.bookmark:2', {key: 'member.bookmark'}),
                task('member.tour'),
            ]),
        }
        const tasks = useOnboardingTasks()

        tasks.startNext('MEMBER')

        expect(tasks.activeTaskId.value).toBeNull()
    })
})
