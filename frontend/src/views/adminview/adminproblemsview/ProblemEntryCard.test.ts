/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import {ProblemLevel, ProblemState, type StoredProblem} from '@/api/generated/schema'
import ProblemEntryCard from './ProblemEntryCard.vue'

const PROBLEM: StoredProblem = {
    id: 7, fingerprint: 'abc', level: ProblemLevel.ERROR, logger: 'dev.chojo.ember.X', message: 'broken',
    exceptionClass: null, exceptionMessage: null, stacktrace: null, distinctMessages: ['broken'], count: 1,
    firstSeen: '2026-11-01T08:00:00Z', lastSeen: '2026-11-01T08:00:00Z', state: ProblemState.OPEN,
    stateChangedAt: null, reopenedCount: 0,
}

/**
 * A problem's card opens from a click and from its chevron button, and a click inside what it opened
 * leaves it open.
 */
describe('ProblemEntryCard', () => {
    function card(expanded: boolean) {
        return mount(ProblemEntryCard, {
            props: {entry: PROBLEM, expanded},
            global: {stubs: {ProblemContextPanel: {template: '<div data-testid="panel">lines</div>'}}},
        })
    }

    it('opens from its chevron button, which says whether it is open', async () => {
        const wrapper = card(false)
        const chevron = wrapper.find('button[aria-expanded]')

        expect(chevron.attributes('aria-expanded')).toBe('false')
        await chevron.trigger('click')

        expect(wrapper.emitted('toggle')).toEqual([[7]])
    })

    it('stays open when what it opened is clicked', async () => {
        const wrapper = card(true)

        await wrapper.find('[data-testid="panel"]').trigger('click')

        expect(wrapper.emitted('toggle')).toBeUndefined()
    })
})
