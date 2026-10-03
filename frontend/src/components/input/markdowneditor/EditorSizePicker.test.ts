/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {DOMWrapper, enableAutoUnmount, mount} from '@vue/test-utils'
import EditorSizePicker from './EditorSizePicker.vue'

enableAutoUnmount(afterEach)

/**
 * The size entry of the editor's menu: a size in pixels typed into a panel rendered at the end of the
 * page, refused when it is no whole number within bounds, and taken off again by an empty field or the
 * reset entry.
 */
describe('EditorSizePicker', () => {
    async function opened(current: number | null = null, shown = 15) {
        const wrapper = mount(EditorSizePicker, {attachTo: document.body, props: {current, shown}})
        await wrapper.find('[data-testid="editor-size"]').trigger('click')
        return wrapper
    }

    function panel(): HTMLElement | null {
        return document.querySelector<HTMLElement>('[role="dialog"][aria-label="Schriftgröße"]')
    }

    function inPanel(selector: string): DOMWrapper<HTMLInputElement> {
        return new DOMWrapper(panel()!.querySelector<HTMLInputElement>(selector)!)
    }

    function field(): DOMWrapper<HTMLInputElement> {
        return inPanel('[data-testid="editor-size-field"]')
    }

    it('opens its panel at the end of the page', async () => {
        const wrapper = await opened()

        expect(panel()?.parentElement).toBe(document.body)
        expect(wrapper.element.contains(panel())).toBe(false)
    })

    it('applies a size confirmed with enter and closes', async () => {
        const wrapper = await opened()
        await field().setValue('24')
        await field().trigger('keydown', {key: 'Enter'})

        expect(wrapper.emitted('pick')).toEqual([[24]])
        expect(panel()).toBeNull()
    })

    it('applies a size when the field is left and stays open', async () => {
        const wrapper = await opened()
        await field().setValue('9')
        await field().trigger('blur')

        expect(wrapper.emitted('pick')).toEqual([[9]])
        expect(panel()).not.toBeNull()
    })

    it.each(['5', '97', '12.5'])('refuses %s and says so', async typed => {
        const wrapper = await opened()
        await field().setValue(typed)
        await field().trigger('keydown', {key: 'Enter'})

        expect(wrapper.emitted('pick')).toBeUndefined()
        expect(field().attributes('aria-invalid')).toBe('true')
        expect(panel()?.textContent).toContain('ganze Zahl von 6 bis 96')
    })

    it('shows the size at the cursor and on its button', async () => {
        const wrapper = await opened(18)

        expect(field().element.value).toBe('18')
        expect(wrapper.find('[data-testid="editor-size"]').text()).toBe('18 px')
    })

    it('takes the size off for an emptied field', async () => {
        const wrapper = await opened(18)
        await field().setValue('')
        await field().trigger('keydown', {key: 'Enter'})

        expect(wrapper.emitted('pick')).toEqual([[null]])
    })

    it('takes the size off with the reset entry and closes', async () => {
        const wrapper = await opened(18)
        await inPanel('[data-testid="editor-size-reset"]').trigger('click')

        expect(wrapper.emitted('pick')).toEqual([[null]])
        expect(panel()).toBeNull()
    })

    it('opens on the size the words are shown in where they have none of their own', async () => {
        await opened(null, 28)

        expect(field().element.value).toBe('28')
    })

    it('counts on from the size the words are shown in', async () => {
        const wrapper = await opened(null, 15)
        field().element.stepUp()
        await field().trigger('input')
        await field().trigger('keydown', {key: 'Enter'})

        expect(wrapper.emitted('pick')).toEqual([[16]])
    })

    it('applies nothing for the shown size left as it is', async () => {
        const wrapper = await opened(null, 15)
        await field().trigger('blur')
        await field().trigger('keydown', {key: 'Enter'})

        expect(wrapper.emitted('pick')).toBeUndefined()
    })

    it('applies nothing when the size stays as it was', async () => {
        const wrapper = await opened(18)
        await field().trigger('keydown', {key: 'Enter'})

        expect(wrapper.emitted('pick')).toBeUndefined()
    })
})
