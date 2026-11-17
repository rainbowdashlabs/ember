/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {StationModule, StationPermission} from '@/api/generated/schema'
import {flowFor, ONBOARDING_FLOWS, stepTextKey, type StepReader} from '@/util/onboardingFlows'
import de from '@/i18n/de-DE'

/**
 * The walk past the station's areas shows only what the reader can open: nothing of a switched-off
 * module and nothing behind a right they do not hold.
 */
describe('the walk past the areas', () => {
    function reader(off: StationModule[], rights: StationPermission[] = []): StepReader {
        return {runs: module => !off.includes(module), can: permission => rights.includes(permission)}
    }

    function stops(off: StationModule[], rights: StationPermission[] = []): string[] {
        return flowFor('member.tour', reader(off, rights)).map(step => step.id ?? '')
    }

    it('walks a plain member past every area that is on', () => {
        expect(stops([])).toEqual([
            'welcome', 'dashboard', 'news', 'profile', 'absences', 'events', 'inventory', 'forms', 'knowledgeBase', 'helpCenter',
        ])
    })

    it('leaves out the areas of switched-off modules', () => {
        const walked = stops([StationModule.NEWS, StationModule.INVENTORY, StationModule.FORMS, StationModule.KNOWLEDGE_BASE])

        expect(walked).not.toContain('news')
        expect(walked).not.toContain('inventory')
        expect(walked).not.toContain('forms')
        expect(walked).not.toContain('knowledgeBase')
        expect(walked).toContain('events')
    })

    it('keeps absences while appointments or attendance run', () => {
        expect(stops([StationModule.EVENTS])).toContain('absences')
        expect(stops([StationModule.EVENTS, StationModule.ATTENDANCE])).not.toContain('absences')
    })

    it('shows management areas only to whoever holds the right, and only while their module runs', () => {
        expect(stops([], [StationPermission.EVENT_MANAGER])).toContain('eventManagement')
        expect(stops([StationModule.EVENTS], [StationPermission.EVENT_MANAGER])).not.toContain('eventManagement')
        expect(stops([], [StationPermission.STATION_ADMINISTRATOR])).toContain('station')
    })

    it('finds the words of a stop by its name, so leaving stops out does not shift them', () => {
        const steps = flowFor('member.tour', reader([StationModule.NEWS]))

        expect(stepTextKey('member.tour', steps[2]!, 2)).toBe('onboarding.steps.member.tour.profile')
        expect(stepTextKey('member.profile', flowFor('member.profile')[1]!, 1)).toBe('onboarding.steps.member.profile.1')
    })

    it('has a text for every step of every task', () => {
        const missing = Object.entries(ONBOARDING_FLOWS).flatMap(([task, steps]) =>
            steps.map((step, index) => stepTextKey(task, step, index))
                .filter(key => typeof key.split('.').reduce<unknown>(
                    (node, part) => (node as Record<string, unknown> | undefined)?.[part], de) !== 'string'))

        expect(missing).toEqual([])
    })
})
