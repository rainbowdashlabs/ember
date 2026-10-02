/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {enableAutoUnmount, flushPromises, mount} from '@vue/test-utils'
import OneTimePasswordDialog from './OneTimePasswordDialog.vue'
import type {IssuedOneTimePassword} from '@/api/generated/schema'
import type {Sheet} from '@/util/printSheet'

const printSheet = vi.hoisted(() => vi.fn())
vi.mock('@/util/printSheet', () => ({printSheet}))

enableAutoUnmount(afterEach)

const ISSUED: IssuedOneTimePassword = {
    accountId: 42,
    name: 'Lena Weber',
    loginName: 'lena.weber@example.org',
    password: 'k7mq-x2pd-9wtr-hb4z',
    expiresAt: '2026-10-09T12:00:00Z',
}

async function shown() {
    const wrapper = mount(OneTimePasswordDialog, {
        props: {issued: ISSUED, modelValue: true},
        attachTo: document.body,
    })
    await flushPromises()
    return wrapper
}

function dialog(): HTMLElement {
    const found = document.querySelector<HTMLElement>('[data-testid="one-time-password-dialog"]')
    if (!found) throw new Error('the dialog is not on the page')
    return found
}

function button(label: string): HTMLButtonElement {
    const found = [...dialog().querySelectorAll('button')].find(candidate => candidate.textContent?.includes(label))
    if (!found) throw new Error(`no button says ${label}`)
    return found
}

afterEach(() => {
    printSheet.mockReset()
})

describe('OneTimePasswordDialog', () => {
    it('shows who it is for, the name to sign in with and the password, once', async () => {
        await shown()

        expect(dialog().textContent).toContain('Zugangsdaten für Lena Weber')
        expect(dialog().textContent).toContain('lena.weber@example.org')
        expect(dialog().textContent).toContain('k7mq-x2pd-9wtr-hb4z')
        expect(dialog().textContent).toContain('nur jetzt angezeigt')
    })

    it('names the address of the instance to sign in at', async () => {
        await shown()

        expect(dialog().textContent).toContain(window.location.origin)
    })

    it('copies the whole sheet and says so', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined)
        Object.defineProperty(navigator, 'clipboard', {value: {writeText}, configurable: true})
        await shown()

        button('Kopieren').click()
        await flushPromises()

        expect(writeText).toHaveBeenCalledOnce()
        const copied = String(writeText.mock.calls.at(0)?.at(0))
        expect(copied).toContain('Einmalpasswort: k7mq-x2pd-9wtr-hb4z')
        expect(copied).toContain('Benutzername / Adresse: lena.weber@example.org')
        expect(button('Kopiert')).toBeTruthy()
    })

    it('prints the sheet on its own', async () => {
        await shown()

        button('Drucken').click()

        expect(printSheet).toHaveBeenCalledOnce()
        const sheet: Sheet = printSheet.mock.calls.at(0)?.at(0)
        expect(sheet.title).toBe('Zugangsdaten für Lena Weber')
        expect(sheet.lines.map(line => line.value)).toContain('k7mq-x2pd-9wtr-hb4z')
    })

    it('closes when the reader is done', async () => {
        const wrapper = await shown()

        button('Schließen').click()
        await flushPromises()

        expect(wrapper.emitted('update:modelValue')?.at(-1)).toEqual([false])
    })
})
