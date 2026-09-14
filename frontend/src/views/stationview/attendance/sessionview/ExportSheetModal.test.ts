/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {describe, expect, it} from 'vitest'
import {mountSuspended} from '@nuxt/test-utils/runtime'
import ExportSheetModal from './ExportSheetModal.vue'
import type {SheetOptions} from '@/api/attendance'

const SESSION_TITLE = 'Dienstabend'

async function open(showsInstanceUrl = true) {
    // The dialog teleports its content to the body, which a mounted wrapper cannot reach into.
    const wrapper = await mountSuspended(ExportSheetModal, {
        props: {modelValue: true, sessionTitle: SESSION_TITLE, showsInstanceUrl, exporting: false},
        global: {stubs: {teleport: true}},
    })
    // The dialog fills itself in when it opens, which is what the story is about.
    await wrapper.setProps({modelValue: false})
    await wrapper.setProps({modelValue: true})
    return wrapper
}

function asked(wrapper: Awaited<ReturnType<typeof open>>): SheetOptions {
    const events = wrapper.emitted('export')
    expect(events, 'the dialog asked for an export').toBeTruthy()
    return events![0]![0] as SheetOptions
}

async function submit(wrapper: Awaited<ReturnType<typeof open>>) {
    await wrapper.get('[data-testid="export-submit"]').trigger('click')
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

        await submit(wrapper)

        const options = asked(wrapper)
        expect(options.signature).toBe(false)
        expect(options.blankRows).toBe(0)
        expect(options.title, 'the session keeps its own heading').toBeUndefined()
    })

    it('starts from what the station settled on for the address', async () => {
        const hidden = await open(false)

        await submit(hidden)

        expect(asked(hidden).instanceUrl).toBe(false)
    })

    it('carries a sheet to sign, a heading of its own and room for people nobody expected', async () => {
        const wrapper = await open()
        await wrapper.get('[data-testid="export-signature-toggle"] [role="switch"]').trigger('click')
        await wrapper.get('input[type="text"]').setValue('Jahreshauptversammlung')
        await wrapper.get('input[type="number"]').setValue(5)

        await submit(wrapper)

        const options = asked(wrapper)
        expect(options.signature).toBe(true)
        expect(options.title).toBe('Jahreshauptversammlung')
        expect(options.blankRows).toBe(5)
    })

    it('sends an empty heading as one, so the sheet is headed by hand', async () => {
        const wrapper = await open()
        await wrapper.get('input[type="text"]').setValue('')

        await submit(wrapper)

        expect(asked(wrapper).title).toBe('')
    })
})
