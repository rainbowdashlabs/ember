/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import {defineComponent, h, nextTick, ref} from 'vue'
import Modal from '@/components/feedback/Modal.vue'
import CellActionsMenu from './CellActionsMenu.vue'
import RowActionsMenu from './RowActionsMenu.vue'

enableAutoUnmount(afterEach)

const ROW_MENU = 'Zeilen-Menü'
const CELL_MENU = 'Aktionen'

/** Lets a dialog and a panel settle: both place their focus scope a tick after they mount. */
async function settle() {
    await flushPromises()
    await new Promise(resolve => setTimeout(resolve, 0))
    await flushPromises()
}

function menu(label: string): HTMLElement | null {
    return document.querySelector<HTMLElement>(`[role="menu"][aria-label="${label}"]`)
}

function entry(label: string, text: string): HTMLElement {
    return [...menu(label)!.querySelectorAll<HTMLElement>('button')].find(button => button.textContent?.includes(text))!
}

function pressOn(target: EventTarget) {
    target.dispatchEvent(new PointerEvent('pointerdown', {bubbles: true, button: 0}))
}

/**
 * The menus of a row and a cell of the page editor: rendered at the end of the page, so neither the
 * cell nor the dialog around the editor cuts them off.
 */
describe('RowActionsMenu', () => {
    async function opened() {
        const wrapper = mount(RowActionsMenu, {attachTo: document.body, props: {isFirst: true, isLast: true}})
        await wrapper.get('button').trigger('click')
        await settle()
        return wrapper
    }

    it('opens at the end of the page, outside the row', async () => {
        const wrapper = await opened()

        expect(menu(ROW_MENU)?.parentElement).toBe(document.body)
        expect(wrapper.element.contains(menu(ROW_MENU))).toBe(false)
        expect(menu(ROW_MENU)?.style.position).toBe('fixed')
    })

    it('closes once an entry was chosen', async () => {
        const wrapper = await opened()

        entry(ROW_MENU, 'Zeile kopieren').click()
        await nextTick()

        expect(wrapper.emitted('copy')).toHaveLength(1)
        expect(menu(ROW_MENU)).toBeNull()
    })

    it('stays open on a press inside and closes on a press outside', async () => {
        await opened()

        pressOn(entry(ROW_MENU, 'Zeile kopieren'))
        await nextTick()
        expect(menu(ROW_MENU)).not.toBeNull()

        pressOn(document.body)
        await nextTick()
        expect(menu(ROW_MENU)).toBeNull()
    })

    it('closes on escape', async () => {
        await opened()

        document.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape'}))
        await nextTick()

        expect(menu(ROW_MENU)).toBeNull()
    })
})

describe('CellActionsMenu inside a dialog', () => {
    it('lets its width field take the focus and keeps the dialog open', async () => {
        const dialogOpen = ref(true)
        mount(defineComponent({
            setup: () => () => h(Modal, {
                'modelValue': dialogOpen.value,
                'onUpdate:modelValue': (value: boolean) => dialogOpen.value = value,
            }, () => h('div', {class: 'relative overflow-hidden'}, [h(CellActionsMenu, {canResize: true, widthPercent: 50})])),
        }), {attachTo: document.body})
        await settle()
        document.querySelector<HTMLButtonElement>(`button[aria-label="${CELL_MENU}"]`)!.click()
        await settle()

        const width = menu(CELL_MENU)!.querySelector<HTMLInputElement>('input[type="number"]')!
        pressOn(width)
        width.focus()
        await settle()

        expect(menu(CELL_MENU)?.parentElement).toBe(document.body)
        expect(document.activeElement).toBe(width)
        expect(dialogOpen.value).toBe(true)
    })
})
