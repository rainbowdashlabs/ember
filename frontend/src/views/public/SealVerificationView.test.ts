/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** @vitest-environment happy-dom */
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import FileUploadField from '@/components/input/FileUploadField.vue'
import {createSealCheck, createSealVerification} from '@/test/mocks/sealVerification'
import SealVerificationView from './SealVerificationView.vue'

const verifySeals = vi.hoisted(() => vi.fn())

vi.mock('@/api', () => ({
    signing: {verifySeals, SEAL_CHECK_MAX_BYTES: 25 * 1024 * 1024},
}))

/**
 * The public check of a PDF: a file picked or dropped goes to the server once, and what comes back
 * is the verdict, or the reason the file was refused in words that lead to another attempt.
 */
function pdf(name = 'urkunde.pdf'): File {
    return new File(['%PDF-1.7'], name, {type: 'application/pdf'})
}

describe('SealVerificationView', () => {
    beforeEach(() => {
        verifySeals.mockReset()
    })

    it('says that nothing is kept and how large a file may be', () => {
        const view = mount(SealVerificationView)

        expect(view.text()).toContain('nicht gespeichert')
        expect(view.text()).toContain('Max. 25 MB')
    })

    it('checks a picked file and shows the verdict', async () => {
        verifySeals.mockResolvedValue(createSealVerification({signatures: [createSealCheck()]}))
        const view = mount(SealVerificationView)

        view.getComponent(FileUploadField).vm.$emit('select', pdf())
        await flushPromises()

        expect(verifySeals).toHaveBeenCalledTimes(1)
        expect(verifySeals.mock.calls[0]?.[0].name).toBe('urkunde.pdf')
        expect(view.text()).toContain('Ergebnis für urkunde.pdf')
        expect(view.text()).toContain('Von dieser Installation versiegelt und unverändert')
    })

    it('checks a dropped file the same way', async () => {
        verifySeals.mockResolvedValue(createSealVerification())
        const view = mount(SealVerificationView)

        await view.getComponent(FileUploadField).trigger('drop', {dataTransfer: {files: [pdf('aushang.pdf')]}})
        await flushPromises()

        expect(verifySeals).toHaveBeenCalledTimes(1)
        expect(view.get('[data-testid="seal-none"]').text()).toContain('Dieses PDF trägt kein Siegel.')
    })

    it('refuses a file over the limit before sending it', async () => {
        const view = mount(SealVerificationView)
        const large = pdf('gross.pdf')
        Object.defineProperty(large, 'size', {value: 26 * 1024 * 1024})

        await view.getComponent(FileUploadField).trigger('drop', {dataTransfer: {files: [large]}})
        await flushPromises()

        expect(verifySeals).not.toHaveBeenCalled()
        expect(view.text()).toContain('Datei zu groß')
    })

    it('says why the server refused a file, with this page\'s guidance', async () => {
        verifySeals.mockRejectedValue(Object.assign(new Error('Not a PDF'), {
            response: {status: 415, data: {code: 'D-132', message: 'Not a PDF'}},
        }))
        const view = mount(SealVerificationView)

        view.getComponent(FileUploadField).vm.$emit('select', pdf('notiz.pdf'))
        await flushPromises()

        expect(view.text()).toContain('Die Datei ist kein lesbares PDF')
        expect(view.text()).toContain('Wähle eine PDF-Datei mit höchstens 25 MB')
        expect(view.find('[data-testid="seal-verification-result"]').exists()).toBe(false)
    })
})
