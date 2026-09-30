/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it, vi} from 'vitest'
import {defineComponent, h, ref} from 'vue'
import {mount} from '@vue/test-utils'
import {useAsyncLoader} from './useAsyncLoader'

vi.mock('vue-i18n', () => ({useI18n: () => ({t: (key: string) => key})}))

/** A promise plus the handle that settles it, so a test can decide which answer arrives first. */
function deferred<T>() {
    let resolve!: (value: T) => void
    let reject!: (reason: unknown) => void
    const promise = new Promise<T>((res, rej) => {
        resolve = res
        reject = rej
    })
    return {promise, resolve, reject}
}

/** A loader that asks for one answer per run and keeps what the latest run was told. */
function filteredLoader() {
    const gates: ReturnType<typeof deferred<string>>[] = []
    const shown = ref('')
    const loader = useAsyncLoader(async (isCurrent) => {
        const gate = deferred<string>()
        gates.push(gate)
        const answer = await gate.promise
        if (!isCurrent()) return
        shown.value = answer
    }, {autoLoad: false})
    return {gates, shown, ...loader}
}

/**
 * A loader re-run by a filter or a page before its previous answer came back.
 *
 * @vitest-environment happy-dom
 */
describe('useAsyncLoader', () => {
    it('keeps the later answer when the earlier one arrives last', async () => {
        const {gates, shown, loading, reload} = filteredLoader()

        const first = reload()
        const second = reload()
        gates[1]!.resolve('second')
        await second
        gates[0]!.resolve('first')
        await first

        expect(shown.value).toBe('second')
        expect(loading.value).toBe(false)
    })

    it('stays loading while the latest run is still out', async () => {
        const {gates, loading, reload} = filteredLoader()

        const first = reload()
        void reload()
        gates[0]!.resolve('first')
        await first

        expect(loading.value).toBe(true)
    })

    it('ignores the failure of a run that was overtaken', async () => {
        const {gates, shown, error, failure, reload} = filteredLoader()

        const first = reload()
        const second = reload()
        gates[1]!.resolve('second')
        await second
        gates[0]!.reject(new Error('late'))
        await first

        expect(shown.value).toBe('second')
        expect(error.value).toBe('')
        expect(failure.value).toBeNull()
    })

    it('reports the failure of the latest run', async () => {
        const {gates, error, loading, reload} = filteredLoader()

        const run = reload()
        gates[0]!.reject(new Error('down'))
        await run

        expect(error.value).not.toBe('')
        expect(loading.value).toBe(false)
    })

    it('names the failure with its own key when it has one', async () => {
        const {error, reload} = useAsyncLoader(() => Promise.reject(new Error('down')), {
            autoLoad: false,
            errorMessageKey: 'errors.loadFailed',
        })

        await reload()

        expect(error.value).toBe('errors.loadFailed')
    })

    it('shows the wait before the first render when it loads on mount', () => {
        let loadingBeforeMount: boolean | undefined
        mount(defineComponent({
            setup() {
                const {loading} = useAsyncLoader(() => new Promise<void>(() => {}))
                loadingBeforeMount = loading.value
                return () => h('div')
            },
        }))

        expect(loadingBeforeMount).toBe(true)
    })

    it('starts idle when it waits to be asked', () => {
        const {loading} = useAsyncLoader(() => Promise.resolve(), {autoLoad: false})

        expect(loading.value).toBe(false)
    })
})
