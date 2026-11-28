/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {defineComponent, h} from 'vue'
import {mount, flushPromises} from '@vue/test-utils'
import {createMemoryHistory, createRouter} from 'vue-router'
import {POLL_MS, WRITE_SETTLE_MS, useLiveCounts} from './useLiveCounts'
import {countsMayHaveChanged} from '@/util/countRefreshState'

const Blank = defineComponent({render: () => h('div')})

/** Mounts a shell that keeps its counters live, with whether it may read them. */
async function shellWith(active: () => boolean = () => true) {
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [{path: '/station/:page*', component: Blank}],
    })
    await router.push('/station/overview')
    const refresh = vi.fn()
    const Shell = defineComponent({
        setup() {
            useLiveCounts(refresh, active)
            return () => h('div')
        },
    })
    const view = mount(Shell, {global: {plugins: [router]}})
    return {view, router, refresh}
}

function showTab(state: 'visible' | 'hidden') {
    Object.defineProperty(document, 'visibilityState', {value: state, configurable: true})
    document.dispatchEvent(new Event('visibilitychange'))
}

/**
 * The sidebar counters of an area follow the lists behind them without a reload: after the reader's
 * own writes, on every move inside the area, when the tab comes back, and on a timer while it is in
 * view.
 *
 * @vitest-environment happy-dom
 */
describe('useLiveCounts', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        showTab('visible')
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it('reads once for a run of writes, after they settled', async () => {
        const {view, refresh} = await shellWith()

        countsMayHaveChanged()
        countsMayHaveChanged()
        await flushPromises()
        expect(refresh).not.toHaveBeenCalled()

        vi.advanceTimersByTime(WRITE_SETTLE_MS)
        expect(refresh).toHaveBeenCalledTimes(1)
        view.unmount()
    })

    it('reads on every move to another page', async () => {
        const {view, router, refresh} = await shellWith()

        await router.push('/station/members')
        await flushPromises()

        expect(refresh).toHaveBeenCalledTimes(1)
        view.unmount()
    })

    it('reads on a timer while the tab is in view, and not while it is hidden', async () => {
        const {view, refresh} = await shellWith()

        vi.advanceTimersByTime(POLL_MS)
        expect(refresh).toHaveBeenCalledTimes(1)

        showTab('hidden')
        vi.advanceTimersByTime(POLL_MS)
        expect(refresh).toHaveBeenCalledTimes(1)
        view.unmount()
    })

    it('catches up the moment a hidden tab is shown again', async () => {
        const {view, refresh} = await shellWith()
        showTab('hidden')

        showTab('visible')

        expect(refresh).toHaveBeenCalledTimes(1)
        view.unmount()
    })

    it('reads nothing while it may not', async () => {
        const {view, router, refresh} = await shellWith(() => false)

        countsMayHaveChanged()
        await router.push('/station/members')
        await flushPromises()
        vi.advanceTimersByTime(POLL_MS)

        expect(refresh).not.toHaveBeenCalled()
        view.unmount()
    })

    it('stops once the shell is gone', async () => {
        const {view, refresh} = await shellWith()
        view.unmount()

        countsMayHaveChanged()
        await flushPromises()
        vi.advanceTimersByTime(POLL_MS)
        showTab('visible')

        expect(refresh).not.toHaveBeenCalled()
    })
})
