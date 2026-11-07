/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it} from 'vitest'
import {defineComponent, h, nextTick, ref} from 'vue'
import {flushPromises, mount, type VueWrapper} from '@vue/test-utils'
import {createMemoryHistory, createRouter} from 'vue-router'
import TabBar from './TabBar.vue'

const TABS = [
    {key: 'general', label: 'Allgemein'},
    {key: 'members', label: 'Mitglieder'},
    {key: 'history', label: 'Verlauf'},
]

/**
 * A row of tabs a screen reader announces as one and a keyboard walks with the arrows.
 *
 * @vitest-environment happy-dom
 */
describe('TabBar', () => {
    let wrapper: VueWrapper | null = null

    afterEach(() => {
        wrapper?.unmount()
        wrapper = null
    })

    function mountBar(initial = 'general') {
        const selected = ref(initial)
        wrapper = mount(defineComponent({
            setup: () => () => h(TabBar, {
                tabs: TABS,
                modelValue: selected.value,
                'onUpdate:modelValue': (key: string) => {
                    selected.value = key
                },
            }),
        }), {attachTo: document.body})
        return selected
    }

    function tabs() {
        return wrapper!.findAll('[role="tab"]')
    }

    function tab(index: number) {
        return tabs()[index]!
    }

    it('is a tab list of tabs', () => {
        mountBar()

        expect(wrapper!.find('[role="tablist"]').exists()).toBe(true)
        expect(tabs().map(each => each.text())).toEqual(['Allgemein', 'Mitglieder', 'Verlauf'])
    })

    it('marks the selected tab and keeps only it in the tab order', () => {
        mountBar('members')

        expect(tabs().map(each => each.attributes('aria-selected'))).toEqual(['false', 'true', 'false'])
        expect(tabs().map(each => each.attributes('tabindex'))).toEqual(['-1', '0', '-1'])
    })

    it('keeps the first tab reachable while none is selected', () => {
        mountBar('unknown')

        expect(tabs().map(each => each.attributes('tabindex'))).toEqual(['0', '-1', '-1'])
    })

    it('selects a tab that is clicked', async () => {
        const selected = mountBar()

        await tab(2).trigger('click')

        expect(selected.value).toBe('history')
        expect(tab(2).attributes('aria-selected')).toBe('true')
    })

    it('moves and selects with the arrow keys, wrapping at either end', async () => {
        const selected = mountBar()

        await tab(0).trigger('keydown', {key: 'ArrowRight'})
        expect(selected.value).toBe('members')
        expect(document.activeElement).toBe(tab(1).element)

        await tab(1).trigger('keydown', {key: 'ArrowLeft'})
        await tab(0).trigger('keydown', {key: 'ArrowLeft'})
        expect(selected.value).toBe('history')
        expect(document.activeElement).toBe(tab(2).element)
    })

    it('jumps to either end with Home and End', async () => {
        const selected = mountBar('members')

        await tab(1).trigger('keydown', {key: 'End'})
        expect(selected.value).toBe('history')

        await tab(2).trigger('keydown', {key: 'Home'})
        expect(selected.value).toBe('general')
        expect(document.activeElement).toBe(tab(0).element)
    })

    it('leaves other keys alone', async () => {
        const selected = mountBar()

        await tab(0).trigger('keydown', {key: 'ArrowDown'})

        expect(selected.value).toBe('general')
    })

    describe('linked to the address', () => {
        async function mountLinked(address: string, offered = ref(TABS), initial = 'general') {
            const router = createRouter({
                history: createMemoryHistory(),
                routes: [{path: '/:rest(.*)*', component: {template: '<div/>'}}],
            })
            await router.push(address)
            await router.isReady()
            const selected = ref(initial)
            wrapper = mount(defineComponent({
                setup: () => () => h(TabBar, {
                    tabs: offered.value,
                    linked: true,
                    modelValue: selected.value,
                    'onUpdate:modelValue': (key: string) => {
                        selected.value = key
                    },
                }),
            }), {attachTo: document.body, global: {plugins: [router]}})
            await nextTick()
            return {router, selected}
        }

        it('opens on the tab the hash names, whatever its case', async () => {
            const {selected} = await mountLinked('/page#Members')

            expect(selected.value).toBe('members')
        })

        it('keeps the view\'s own choice when the hash names no tab', async () => {
            const {selected} = await mountLinked('/page#nothing', ref(TABS), 'history')

            expect(selected.value).toBe('history')
        })

        it('opens a tab the hash names once it arrives', async () => {
            const offered = ref(TABS.slice(0, 2))
            const {selected} = await mountLinked('/page#history', offered)
            expect(selected.value).toBe('general')

            offered.value = TABS
            await nextTick()
            await nextTick()

            expect(selected.value).toBe('history')
        })

        it('follows the hash when it changes', async () => {
            const {router, selected} = await mountLinked('/page')

            await router.push('/page#history')
            await nextTick()

            expect(selected.value).toBe('history')
        })

        it('writes the picked tab into the hash, keeping path and query, without a new history entry', async () => {
            const {router} = await mountLinked('/page?x=1')
            const before = window.history.length

            await tab(1).trigger('click')
            await flushPromises()

            expect(router.currentRoute.value.fullPath).toBe('/page?x=1#members')
            expect(window.history.length).toBe(before)
        })
    })

    it('leaves the address alone when not linked', async () => {
        const router = createRouter({
            history: createMemoryHistory(),
            routes: [{path: '/:rest(.*)*', component: {template: '<div/>'}}],
        })
        await router.push('/page#history')
        await router.isReady()
        const selected = ref('general')
        wrapper = mount(defineComponent({
            setup: () => () => h(TabBar, {
                tabs: TABS,
                modelValue: selected.value,
                'onUpdate:modelValue': (key: string) => {
                    selected.value = key
                },
            }),
        }), {attachTo: document.body, global: {plugins: [router]}})
        await tab(1).trigger('click')
        await flushPromises()

        expect(selected.value).toBe('members')
        expect(router.currentRoute.value.fullPath).toBe('/page#history')
    })
})
