/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it} from 'vitest'
import {DOMWrapper, flushPromises} from '@vue/test-utils'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import ExportSheetModal from './ExportSheetModal.vue'
import type {SheetOptions} from '@/api/attendance'

const SESSION_TITLE = 'Dienstabend'

const mounted: { unmount(): void }[] = []

afterEach(() => mounted.splice(0).forEach(wrapper => wrapper.unmount()))

/**
 * Opens the dialog the way a reader does, closed first and then open, since it fills itself in
 * as it opens.
 */
async function open(showsInstanceUrl = true) {
    const wrapper = await mountSuspended(ExportSheetModal, {
        props: {modelValue: false, sessionTitle: SESSION_TITLE, showsInstanceUrl, exporting: false},
        attachTo: document.body,
    })
    mounted.push(wrapper)
    await wrapper.setProps({modelValue: true})
    await flushPromises()
    return wrapper
}

/** The dialog's content lands at the end of the body, outside what the mounted wrapper holds. */
function page() {
    return new DOMWrapper(document.body)
}

function asked(wrapper: Awaited<ReturnType<typeof open>>): SheetOptions {
    const events = wrapper.emitted('export')
    expect(events, 'the dialog asked for an export').toBeTruthy()
    return events![0]![0] as SheetOptions
}

async function submit() {
    await page().get('[data-testid="export-submit"]').trigger('click')
}

/**
 * What the dialog asks the server for.
 *
 * <p>The plain export is one press away: opening it and exporting is the sheet the product has
 * always produced, which is why what it sends for an untouched dialog matters as much as what it
 * sends for a filled-in one.
 */
describe('ExportSheetModal', () => {
    it('asks for the sheet as it always was when nothing is touched', async () => {
        const wrapper = await open()

        await submit()

        const options = asked(wrapper)
        expect(options.signature).toBe(false)
        expect(options.blankRows).toBe(0)
        expect(options.title, 'the session keeps its own heading').toBeUndefined()
    })

    it('starts from what the station settled on for the address', async () => {
        const hidden = await open(false)

        await submit()

        expect(asked(hidden).instanceUrl).toBe(false)
    })

    it('carries a sheet to sign, a heading of its own and room for people nobody expected', async () => {
        const wrapper = await open()
        await page().get('[data-testid="export-signature-toggle"] [role="switch"]').trigger('click')
        await page().get('input[type="text"]').setValue('Jahreshauptversammlung')
        await page().get('input[type="number"]').setValue(5)

        await submit()

        const options = asked(wrapper)
        expect(options.signature).toBe(true)
        expect(options.title).toBe('Jahreshauptversammlung')
        expect(options.blankRows).toBe(5)
    })

    it('sends an empty heading as one, so the sheet is headed by hand', async () => {
        const wrapper = await open()
        await page().get('input[type="text"]').setValue('')

        await submit()

        expect(asked(wrapper).title).toBe('')
    })
})
