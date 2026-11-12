/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {mount} from '@vue/test-utils'
import {describe, expect, it} from 'vitest'
import {ProblemLevel, ProblemState, type StoredProblem} from '@/api/generated/schema'
import ProblemEntryHeader from './ProblemEntryHeader.vue'

function problem(state: ProblemState, level: ProblemLevel = ProblemLevel.ERROR, reopenedCount = 0): StoredProblem {
    return {
        id: 7, fingerprint: 'abc', level, logger: 'dev.chojo.ember.api.ExceptionMapping', message: 'broken',
        exceptionClass: null, exceptionMessage: null, stacktrace: null, distinctMessages: ['broken'], count: 3,
        firstSeen: '2026-11-01T08:00:00Z', lastSeen: '2026-11-01T09:00:00Z', state, stateChangedAt: null,
        reopenedCount,
    }
}

/**
 * The actions on a problem's card: each offered only from the state it starts from, and the beacon
 * never offered for a refusal.
 */
describe('ProblemEntryHeader', () => {
    function header(entry: StoredProblem, canSend = true) {
        return mount(ProblemEntryHeader, {props: {entry, expanded: false, canSend}})
    }

    function offered(wrapper: ReturnType<typeof header>) {
        return ['resolve', 'mute', 'unmute', 'delete']
            .filter(action => wrapper.find(`[data-testid="problem-${action}-7"]`).exists())
    }

    it('offers resolve, mute and delete on an open problem', () => {
        expect(offered(header(problem(ProblemState.OPEN)))).toEqual(['resolve', 'mute', 'delete'])
    })

    it('offers mute but not resolve on a resolved problem', () => {
        expect(offered(header(problem(ProblemState.RESOLVED)))).toEqual(['mute', 'delete'])
    })

    it('offers only unmute and delete on a muted problem', () => {
        expect(offered(header(problem(ProblemState.MUTED)))).toEqual(['unmute', 'delete'])
    })

    it('says what an action asks for with the problem it is on', async () => {
        const wrapper = header(problem(ProblemState.MUTED))

        await wrapper.find('[data-testid="problem-unmute-7"]').trigger('click')

        expect(wrapper.emitted('act')).toEqual([['unmute', 7]])
    })

    it('never offers to send a refusal to the beacon', () => {
        expect(header(problem(ProblemState.OPEN, ProblemLevel.REFUSAL)).find('[data-testid="beacon-send-7"]').exists()).toBe(false)
        expect(header(problem(ProblemState.OPEN, ProblemLevel.ERROR)).find('[data-testid="beacon-send-7"]').exists()).toBe(true)
    })

    it('marks a problem that came back and one that is not open', () => {
        const wrapper = header(problem(ProblemState.RESOLVED, ProblemLevel.WARN, 2))

        expect(wrapper.text()).toContain('2-mal wieder aufgetreten')
        expect(wrapper.find('[data-testid="problem-state-7"]').text()).toBe('Erledigt')
    })
})
