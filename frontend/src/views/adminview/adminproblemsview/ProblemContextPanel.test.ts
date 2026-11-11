/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {ProblemContextState, type ProblemContext} from '@/api/generated/schema'
import ProblemContextPanel from './ProblemContextPanel.vue'

const context = vi.hoisted(() => vi.fn())

vi.mock('@/api', () => ({problems: {context}}))

const AT = '2026-11-01T10:00:00Z'

function answer(state: ProblemContextState, over: Partial<ProblemContext> = {}): ProblemContext {
    return {
        state, databaseLogging: state !== ProblemContextState.LOGGING_OFF,
        occurrences: [{occurredAt: AT, thread: 'worker-1'}, {occurredAt: '2026-11-01T09:00:00Z', thread: 'worker-2'}],
        occurrence: 0, allThreads: false, lines: [], ...over,
    }
}

/**
 * The log around a problem: read for the newest occurrence first, again for another occurrence or for
 * every thread, the occurrence's own line marked, and an empty window explained.
 */
describe('ProblemContextPanel', () => {
    beforeEach(() => context.mockReset())

    async function panel() {
        const wrapper = mount(ProblemContextPanel, {props: {problemId: 4}})
        await flushPromises()
        return wrapper
    }

    it('shows the lines around the newest occurrence with the occurrence marked', async () => {
        context.mockResolvedValue(answer(ProblemContextState.LINES, {lines: [
            {loggedAt: AT, level: 'INFO', logger: 'dev.chojo.ember.A', thread: 'worker-1', message: 'before', problem: false},
            {loggedAt: AT, level: 'ERROR', logger: 'dev.chojo.ember.B', thread: 'worker-1', message: 'broken', problem: true},
        ]}))

        const wrapper = await panel()

        expect(context).toHaveBeenCalledWith(4, null, false)
        expect(wrapper.text()).toContain('before')
        const marked = wrapper.find('[data-testid="problem-context-occurrence"]')
        expect(marked.text()).toContain('broken')
        expect(marked.text()).toContain('Dieser Eintrag')
        expect(marked.attributes('aria-current')).toBe('true')
    })

    it('reads again for an earlier occurrence and for every thread', async () => {
        context.mockResolvedValue(answer(ProblemContextState.LINES))
        const wrapper = await panel()

        await wrapper.find('[data-testid="problem-occurrence-1"]').trigger('click')
        await flushPromises()
        await wrapper.find('[data-testid="problem-all-threads"]').trigger('click')
        await flushPromises()

        expect(context).toHaveBeenNthCalledWith(2, 4, '2026-11-01T09:00:00Z', false)
        expect(context).toHaveBeenNthCalledWith(3, 4, '2026-11-01T09:00:00Z', true)
    })

    it('says the log is not kept rather than showing nothing', async () => {
        context.mockResolvedValue(answer(ProblemContextState.LOGGING_OFF))

        const wrapper = await panel()

        expect(wrapper.find('[data-testid="problem-context-empty"]').text())
            .toContain('nicht in der Datenbank gespeichert')
    })

    it('says nothing was kept for the window', async () => {
        context.mockResolvedValue(answer(ProblemContextState.NOTHING_KEPT))

        const wrapper = await panel()

        expect(wrapper.find('[data-testid="problem-context-empty"]').text()).toContain('nichts gespeichert')
    })

    it('shows only the answer to the latest choice', async () => {
        let older: (value: ProblemContext) => void = () => {}
        context.mockResolvedValueOnce(answer(ProblemContextState.LINES))
        const wrapper = await panel()
        context.mockImplementationOnce(() => new Promise(resolve => {
            older = resolve
        }))
        context.mockResolvedValueOnce(answer(ProblemContextState.NOTHING_KEPT))

        await wrapper.find('[data-testid="problem-occurrence-1"]').trigger('click')
        await wrapper.find('[data-testid="problem-all-threads"]').trigger('click')
        await flushPromises()
        older(answer(ProblemContextState.LINES, {lines: [
            {loggedAt: AT, level: 'INFO', logger: 'dev.chojo.ember.A', thread: 'worker-1', message: 'stale', problem: false},
        ]}))
        await flushPromises()

        expect(wrapper.text()).not.toContain('stale')
        expect(wrapper.find('[data-testid="problem-context-empty"]').text()).toContain('nichts gespeichert')
    })

    it('shows no lines of an earlier choice once a choice fails', async () => {
        context.mockResolvedValueOnce(answer(ProblemContextState.LINES, {lines: [
            {loggedAt: AT, level: 'INFO', logger: 'dev.chojo.ember.A', thread: 'worker-1', message: 'earlier', problem: false},
        ]}))
        const wrapper = await panel()
        context.mockRejectedValueOnce(new Error('gone'))

        await wrapper.find('[data-testid="problem-all-threads"]').trigger('click')
        await flushPromises()

        expect(wrapper.text()).not.toContain('earlier')
    })

    it('links to the settings only where the log is not kept', async () => {
        context.mockResolvedValue(answer(ProblemContextState.NOTHING_KEPT))
        const kept = await panel()
        expect(kept.find('[data-testid="problem-context-empty"]').find('a, nuxt-link-stub').exists()).toBe(false)

        context.mockResolvedValue(answer(ProblemContextState.LOGGING_OFF))
        const off = await panel()
        expect(off.find('[data-testid="problem-context-empty"]').text()).toContain('Zu den Einstellungen')
    })

    it('says a problem without a kept occurrence has nothing around it', async () => {
        context.mockResolvedValue(answer(ProblemContextState.NO_OCCURRENCE, {occurrences: []}))

        const wrapper = await panel()

        expect(wrapper.find('[data-testid="problem-context-empty"]').text()).toContain('kein Zeitpunkt')
    })

    it('says when the problem\'s own line is not in the log', async () => {
        context.mockResolvedValue(answer(ProblemContextState.LINES, {lines: [
            {loggedAt: AT, level: 'INFO', logger: 'dev.chojo.ember.A', thread: 'worker-1', message: 'around', problem: false},
        ]}))

        const wrapper = await panel()

        expect(wrapper.find('[data-testid="problem-context-own-missing"]').exists()).toBe(true)
    })
})
