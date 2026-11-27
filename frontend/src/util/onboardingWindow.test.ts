/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import type {OnboardingLevel, OnboardingStatus, OnboardingTaskView} from '@/api/generated/schema'
import {hiddenCount, pageLevels, visibleTasks, type LoadedLevels} from './onboardingWindow'

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

/** Which tasks a page shows decides what a newcomer meets first, so the rules are pinned here. */
describe('onboardingWindow', () => {
    it('keeps the administration area to itself', () => {
        const loaded: LoadedLevels = {
            MEMBER: status('MEMBER', [task('member.tour')]),
            INSTANCE: status('INSTANCE', [task('instance.ownAccount'), task('instance.mail', {ready: false})]),
        }

        expect(visibleTasks(loaded, pageLevels('INSTANCE')).map(entry => entry.task.key)).toEqual(['instance.ownAccount'])
        expect(hiddenCount(loaded, pageLevels('INSTANCE'))).toBe(1)
    })

    it('fills the dashboard from the station first, whichever card asks', () => {
        const loaded: LoadedLevels = {
            STATION: status('STATION', [task('station.mail'), task('station.memberTypes')]),
            MEMBER: status('MEMBER', [task('member.tour'), task('member.profile', {ready: false})]),
        }

        expect(pageLevels('MEMBER')).toEqual(pageLevels('STATION'))
        expect(visibleTasks(loaded, pageLevels('MEMBER')).map(entry => entry.level))
            .toEqual(['STATION', 'STATION', 'MEMBER'])
        expect(hiddenCount(loaded, pageLevels('MEMBER'))).toBe(1)
    })

    it('counts neither settled tasks as shown nor as waiting', () => {
        const loaded: LoadedLevels = {
            MEMBER: status('MEMBER', [task('member.tour', {state: 'DONE'}), task('member.bookmark', {state: 'SKIPPED'})]),
        }

        expect(visibleTasks(loaded, pageLevels('MEMBER'))).toEqual([])
        expect(hiddenCount(loaded, pageLevels('MEMBER'))).toBe(0)
    })
})
