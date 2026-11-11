/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {flushPromises, mount} from '@vue/test-utils'
import {defineComponent} from 'vue'
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {ProblemLevel, ProblemState, type StoredProblem} from '@/api/generated/schema'
import {useProblemList} from './useProblemList'

const api = vi.hoisted(() => ({
    listProblems: vi.fn(),
    resolve: vi.fn(),
    mute: vi.fn(),
    unmute: vi.fn(),
    deleteProblem: vi.fn(),
    resolveAll: vi.fn(),
    sendProblems: vi.fn(),
    refreshCounts: vi.fn(),
}))

vi.mock('@/api', () => ({
    problems: {
        listProblems: api.listProblems,
        resolve: api.resolve,
        mute: api.mute,
        unmute: api.unmute,
        deleteProblem: api.deleteProblem,
        resolveAll: api.resolveAll,
    },
    beacon: {sendProblems: api.sendProblems},
}))

vi.mock('@/composables/useMonitoringCounts', () => ({
    useMonitoringCounts: () => ({refresh: api.refreshCounts}),
}))

function problem(id: number, level: ProblemLevel = ProblemLevel.ERROR, state: ProblemState = ProblemState.OPEN): StoredProblem {
    return {
        id, fingerprint: `fp-${id}`, level, logger: 'dev.chojo.ember.X', message: 'broken', exceptionClass: null,
        exceptionMessage: null, stacktrace: null, distinctMessages: ['broken'], count: 1,
        firstSeen: '2026-11-01T08:00:00Z', lastSeen: '2026-11-01T08:00:00Z', state, stateChangedAt: null,
        reopenedCount: 0,
    }
}

/** The composable reaches for the locale and mounts its first load, so it runs inside a component. */
async function mounted() {
    let list: ReturnType<typeof useProblemList> | null = null
    mount(defineComponent({
        setup() {
            list = useProblemList()
            return () => null
        },
    }))
    await flushPromises()
    return list as unknown as ReturnType<typeof useProblemList>
}

/**
 * Working through the problem log: the list follows its filters and only the latest answer counts, every
 * change is followed by a quiet reload and a fresh count, and whatever fails is said.
 */
describe('useProblemList', () => {
    beforeEach(() => {
        Object.values(api).forEach(fn => fn.mockReset())
        api.listProblems.mockResolvedValue([problem(1), problem(2, ProblemLevel.WARN)])
    })

    it('loads the open faults first and counts only what is open', async () => {
        api.listProblems.mockResolvedValue([problem(1), problem(2, ProblemLevel.WARN), problem(3, ProblemLevel.ERROR, ProblemState.MUTED)])

        const list = await mounted()

        expect(api.listProblems).toHaveBeenCalledWith(ProblemState.OPEN, false)
        expect(list.errorCount.value).toBe(1)
        expect(list.warnCount.value).toBe(1)
    })

    it('starts over when the kind changes: no level, no selection', async () => {
        const list = await mounted()
        list.toggleLevel(ProblemLevel.WARN)
        list.toggleSelected(1)

        list.showKind(true)
        await flushPromises()

        expect(api.listProblems).toHaveBeenLastCalledWith(ProblemState.OPEN, true)
        expect(list.level.value).toBeNull()
        expect(list.selected.value.size).toBe(0)
    })

    it('shows only the answer to the latest request', async () => {
        const list = await mounted()
        let answerRefusals: (value: StoredProblem[]) => void = () => {}
        api.listProblems.mockImplementationOnce(() => new Promise(resolve => {
            answerRefusals = resolve
        }))
        api.listProblems.mockResolvedValueOnce([problem(9)])

        list.showKind(true)
        list.showKind(false)
        await flushPromises()
        answerRefusals([problem(5, ProblemLevel.REFUSAL)])
        await flushPromises()

        expect(list.entries.value.map(e => e.id)).toEqual([9])
    })

    it('reloads quietly and refreshes the counts after a change', async () => {
        const list = await mounted()
        api.listProblems.mockResolvedValue([problem(2, ProblemLevel.WARN)])

        await list.change('mute', 1)

        expect(api.mute).toHaveBeenCalledWith(1)
        expect(list.loading.value).toBe(false)
        expect(list.entries.value.map(e => e.id)).toEqual([2])
        expect(api.refreshCounts).toHaveBeenCalled()
    })

    it('says what failed and still shows what the server holds', async () => {
        const list = await mounted()
        api.resolve.mockRejectedValue(new Error('gone'))

        await list.change('resolve', 1)

        expect(list.failure.value).not.toBeNull()
        expect(api.listProblems).toHaveBeenCalledTimes(2)
    })

    it('offers resolve-all only while every open problem of the kind is shown', async () => {
        const list = await mounted()
        expect(list.canResolveAll.value).toBe(true)

        list.toggleLevel(ProblemLevel.ERROR)

        expect(list.canResolveAll.value).toBe(false)
    })

    it('resolves all of the kind shown and says how many', async () => {
        const list = await mounted()
        list.showKind(true)
        await flushPromises()
        api.resolveAll.mockResolvedValue({resolved: 4})

        await list.resolveAll()

        expect(api.resolveAll).toHaveBeenCalledWith(true)
        expect(list.result.value).toBe('Als erledigt markiert: 4')
    })

    it('forgets a picked problem that is no longer listed', async () => {
        const list = await mounted()
        list.toggleSelected(1)
        list.toggleSelected(2)
        api.listProblems.mockResolvedValue([problem(2, ProblemLevel.WARN)])

        await list.change('resolve', 1)

        expect([...list.selected.value]).toEqual([2])
    })

    it('asks before deleting and deletes only once confirmed', async () => {
        const list = await mounted()

        list.requestDelete(1)
        expect(list.deletion.show.value).toBe(true)
        expect(api.deleteProblem).not.toHaveBeenCalled()

        await list.deletion.confirm()

        expect(api.deleteProblem).toHaveBeenCalledWith(1)
    })
})
