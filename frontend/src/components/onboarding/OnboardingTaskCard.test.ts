/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mount, flushPromises} from '@vue/test-utils'
import OnboardingTaskCard from './OnboardingTaskCard.vue'
import type {OnboardingStatus, OnboardingTaskView} from '@/api/onboarding'
import {onboardingStatus} from '@/util/onboardingState'

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
        ...overrides,
    }
}

function status(tasks: OnboardingTaskView[]): OnboardingStatus {
    return {level: 'MEMBER', tasks, open: tasks.length, done: 0, skipped: 0}
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
        onboardingStatus.value = {}
        getTasks.mockReset()
        markTask.mockReset()
    })

    it('offers to walk a task that has somewhere to point', async () => {
        const wrapper = await mountCard([task()])

        expect(wrapper.text()).toContain('Dein Profil vervollständigen')
        expect(wrapper.text()).toContain('Los geht’s')
    })

    it('offers no walk for a task that happens outside Ember', async () => {
        const wrapper = await mountCard([task({id: 'member.bookmark', key: 'member.bookmark', confirmable: true})])

        expect(wrapper.text()).not.toContain('Los geht’s')
        expect(wrapper.text()).toContain('Erledigt')
    })

    it('offers no tick for a task that reads its own answer', async () => {
        const wrapper = await mountCard([task()])

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
})
