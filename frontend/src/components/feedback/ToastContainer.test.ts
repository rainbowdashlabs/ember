/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {nextTick} from 'vue'
import {mount, type VueWrapper} from '@vue/test-utils'
import ToastContainer from './ToastContainer.vue'
import de from '@/i18n/de-DE'
import {dismissToast, getToasts, showToast} from '@/util/toast'

/**
 * The toasts as a screen reader and a keyboard meet them: announced from live regions, closed with a
 * real button, and kept on screen while somebody is at them.
 *
 * @vitest-environment happy-dom
 */
describe('ToastContainer', () => {
    let wrapper: VueWrapper | null = null

    beforeEach(() => {
        vi.useFakeTimers()
        wrapper = mount(ToastContainer, {attachTo: document.body})
    })

    afterEach(() => {
        getToasts().value.forEach(toast => dismissToast(toast.id))
        wrapper?.unmount()
        wrapper = null
        vi.useRealTimers()
    })

    function region(testId: string) {
        return document.querySelector<HTMLElement>(`[data-testid="${testId}"]`)!
    }

    function toastElement() {
        return document.querySelector<HTMLElement>('[data-testid="toast"]')
    }

    it('stands a polite status region and an alert region in the page before any toast arrives', () => {
        expect(region('toast-notices').getAttribute('role')).toBe('status')
        expect(region('toast-notices').getAttribute('aria-live')).toBe('polite')
        expect(region('toast-alerts').getAttribute('role')).toBe('alert')
    })

    it('announces an error as an alert and anything else politely', async () => {
        showToast('Gespeichert', 'success')
        showToast('Das hat nicht funktioniert', 'error')
        await nextTick()

        expect(region('toast-notices').textContent).toContain('Gespeichert')
        expect(region('toast-notices').textContent).not.toContain('Das hat nicht funktioniert')
        expect(region('toast-alerts').textContent).toContain('Das hat nicht funktioniert')
    })

    it('closes with a labelled button', async () => {
        showToast('Gespeichert', 'success')
        await nextTick()

        const close = document.querySelector<HTMLButtonElement>('[data-testid="toast-dismiss"]')!
        expect(close.tagName).toBe('BUTTON')
        expect(close.getAttribute('aria-label')).toBe(de.toast.dismiss)

        close.click()
        await nextTick()
        expect(getToasts().value).toHaveLength(0)
    })

    it('goes on its own once its time is up', async () => {
        showToast('Gespeichert', 'success', 5000)
        await nextTick()

        vi.advanceTimersByTime(5000)

        expect(getToasts().value).toHaveLength(0)
    })

    it('waits while the pointer rests on it, and takes up the time it had left', async () => {
        showToast('Abgemeldet', 'info', 5000, {label: 'Rückgängig', run: () => {}})
        await nextTick()
        vi.advanceTimersByTime(2000)

        toastElement()!.dispatchEvent(new MouseEvent('mouseenter'))
        vi.advanceTimersByTime(60_000)
        expect(getToasts().value).toHaveLength(1)

        toastElement()!.dispatchEvent(new MouseEvent('mouseleave'))
        vi.advanceTimersByTime(2999)
        expect(getToasts().value).toHaveLength(1)
        vi.advanceTimersByTime(1)
        expect(getToasts().value).toHaveLength(0)
    })

    it('waits while the focus is inside it, even when the pointer leaves', async () => {
        showToast('Abgemeldet', 'info', 5000, {label: 'Rückgängig', run: () => {}})
        await nextTick()

        toastElement()!.dispatchEvent(new MouseEvent('mouseenter'))
        document.querySelector<HTMLButtonElement>('[data-testid="toast-action"]')!.focus()
        toastElement()!.dispatchEvent(new MouseEvent('mouseleave'))
        vi.advanceTimersByTime(60_000)
        expect(getToasts().value).toHaveLength(1)

        document.querySelector<HTMLButtonElement>('[data-testid="toast-action"]')!.blur()
        vi.advanceTimersByTime(5000)
        expect(getToasts().value).toHaveLength(0)
    })
})
