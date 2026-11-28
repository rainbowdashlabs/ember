/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mount, flushPromises} from '@vue/test-utils'
import OnboardingTaskCard from './OnboardingTaskCard.vue'
import type {OnboardingStatus, OnboardingTaskView} from '@/api/generated/schema'
import {onboardingState} from '@/util/onboardingState'

const getTasks = vi.fn()
const markTask = vi.fn()

vi.mock('@/api', () => ({
    onboarding: {
        getTasks: (...args: unknown[]) => getTasks(...args),
        markTask: (...args: unknown[]) => markTask(...args),
    },
}))

function task(overrides: Partial<OnboardingTaskView> = {}): OnboardingTaskView {
    return {
        id: 'member.profile',
        key: 'member.profile',
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

function status(tasks: OnboardingTaskView[], level: OnboardingStatus['level'] = 'MEMBER'): OnboardingStatus {
    return {level, tasks, open: tasks.length, done: 0, skipped: 0}
}

function numbered(count: number, overrides: Partial<OnboardingTaskView> = {}): OnboardingTaskView[] {
    const keys = ['member.tour', 'member.profile', 'member.notifications', 'member.push', 'member.eventAnswer']
    return keys.slice(0, count).map(key => task({id: key, key, ...overrides}))
}

function shownIds(wrapper: Awaited<ReturnType<typeof mountCard>>): string[] {
    return wrapper.findAll('[data-testid^="onboarding-task-member"]').map(item => item.attributes('data-testid') ?? '')
}

async function mountCard(tasks: OnboardingTaskView[]) {
    getTasks.mockResolvedValue(status(tasks))
    const wrapper = mount(OnboardingTaskCard, {
        props: {level: 'MEMBER'},
        global: {stubs: {LayeredEmberLogo: true}},
    })
    await flushPromises()
    return wrapper
}

/**
 * The card is where somebody picks up a task. What matters here is that it offers the right doors:
 * a walk for what Ember can point at, a tick only for what Ember cannot see for itself.
 */
describe('OnboardingTaskCard', () => {
    beforeEach(() => {
        onboardingState().onboardingStatus.value = {}
        getTasks.mockReset()
        markTask.mockReset()
    })

    it('offers to walk a task that has somewhere to point', async () => {
        const wrapper = await mountCard([task()])

        expect(wrapper.text()).toContain('Dein Profil durchsehen')
        expect(wrapper.text()).toContain('Los geht’s')
    })

    it('offers no walk for a task that happens outside Ember', async () => {
        const wrapper = await mountCard([task({id: 'member.bookmark', key: 'member.bookmark', confirmable: true})])

        expect(wrapper.text()).not.toContain('Los geht’s')
        expect(wrapper.text()).toContain('Erledigt')
    })

    it('offers no tick for a task that reads its own answer, as the notifications read the settings', async () => {
        const wrapper = await mountCard([task({id: 'member.notifications', key: 'member.notifications'})])

        expect(wrapper.text()).not.toContain('Erledigt')
    })

    it('shows nothing at all when there is nothing to ask', async () => {
        const wrapper = await mountCard([])

        expect(wrapper.text()).toBe('')
    })

    it('offers to take a skipped task up again', async () => {
        const wrapper = await mountCard([
            task({id: 'member.absence', key: 'member.absence', state: 'SKIPPED', actorName: 'Petra Sommer'}),
        ])

        expect(wrapper.text()).toContain('Wieder aufnehmen')
    })

    it('says when a task was passed over on another station', async () => {
        const wrapper = await mountCard([
            task({id: 'member.bookmark', key: 'member.bookmark', state: 'SKIPPED', elsewhere: true}),
        ])

        expect(wrapper.find('[data-testid="onboarding-task-elsewhere"]').text()).toBe('auf einer anderen Wache')
    })

    it('shows three tasks at most and counts the rest', async () => {
        const wrapper = await mountCard(numbered(5))

        expect(shownIds(wrapper)).toEqual([
            'onboarding-task-member.tour',
            'onboarding-task-member.profile',
            'onboarding-task-member.notifications',
        ])
        expect(wrapper.find('[data-testid="onboarding-later"]').text()).toBe('Danach warten noch 2 Schritte.')
    })

    it('never shows a task that still waits for another, and counts it with the rest', async () => {
        const wrapper = await mountCard([
            task({id: 'member.tour', key: 'member.tour'}),
            task({id: 'member.profile', key: 'member.profile', ready: false}),
        ])

        expect(shownIds(wrapper)).toEqual(['onboarding-task-member.tour'])
        expect(wrapper.find('[data-testid="onboarding-later"]').text()).toBe('Danach wartet noch ein Schritt.')
    })

    it('leaves the free places to the station first', async () => {
        onboardingState().onboardingStatus.value = {
            STATION: status([
                task({id: 'station.mail', key: 'station.mail'}),
                task({id: 'station.memberTypes', key: 'station.memberTypes'}),
            ], 'STATION'),
        }
        const wrapper = await mountCard(numbered(3))

        expect(shownIds(wrapper)).toEqual(['onboarding-task-member.tour'])
        expect(wrapper.find('[data-testid="onboarding-later"]').text()).toBe('Danach warten noch 2 Schritte.')
    })

    it('shows that it goes on later when the station takes every place', async () => {
        onboardingState().onboardingStatus.value = {
            STATION: status([
                task({id: 'station.mail', key: 'station.mail'}),
                task({id: 'station.memberTypes', key: 'station.memberTypes'}),
                task({id: 'station.groups', key: 'station.groups'}),
            ], 'STATION'),
        }
        const wrapper = await mountCard(numbered(2))

        expect(shownIds(wrapper)).toEqual([])
        expect(wrapper.find('[data-testid="onboarding-waiting"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="onboarding-later"]').text()).toBe('Danach warten noch 2 Schritte.')
    })

    it('shows no count of later tasks on the station card above the reader\'s own', async () => {
        onboardingState().onboardingStatus.value = {MEMBER: status(numbered(4))}
        getTasks.mockResolvedValueOnce(status([task({id: 'station.mail', key: 'station.mail'})], 'STATION'))
        const wrapper = mount(OnboardingTaskCard, {
            props: {level: 'STATION'},
            global: {stubs: {LayeredEmberLogo: true}},
        })
        await flushPromises()

        expect(wrapper.find('[data-testid="onboarding-task-station.mail"]').exists()).toBe(true)
        expect(wrapper.find('[data-testid="onboarding-later"]').exists()).toBe(false)
    })
})
