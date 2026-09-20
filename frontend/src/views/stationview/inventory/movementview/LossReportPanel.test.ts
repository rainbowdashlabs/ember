/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {mount} from '@vue/test-utils'
import {createI18n} from 'vue-i18n'
import LossReportPanel from './LossReportPanel.vue'

const downloadDocument = vi.fn()
const presentDocument = vi.fn()

vi.mock('@/api', () => ({movements: {downloadDocument: (id: number) => downloadDocument(id)}}))
vi.mock('@/util/documentView', () => ({presentDocument: (file: unknown) => presentDocument(file)}))

/**
 * Where the evidence of a loss report reaches the reader.
 *
 * <p>The panel used to write out its own download link and revoke the object URL in the same turn,
 * which left the button doing nothing on a phone. The shared hand-off is the only path that knows
 * whether to save the document or open it, so what matters is that the panel calls it at all.
 */

const i18n = createI18n({
    legacy: false,
    locale: 'de-DE',
    missingWarn: false,
    fallbackWarn: false,
    messages: {'de-DE': {}},
})

function mountPanel(documentName: string | null) {
    return mount(LossReportPanel, {
        props: {movementId: 4, report: {documentName}},
        global: {plugins: [i18n]},
    })
}

describe('LossReportPanel', () => {
    beforeEach(() => {
        downloadDocument.mockReset()
        presentDocument.mockReset()
    })

    /** The name travels with the document from the server, so the panel adds nothing of its own. */
    it('hands the fetched document to the shared hand-off', async () => {
        const file = {blob: new Blob(['evidence']), filename: 'verlust.pdf'}
        downloadDocument.mockResolvedValue(file)

        await mountPanel('verlust.pdf').get('[data-testid="loss-report-download"]').trigger('click')
        await vi.waitFor(() => expect(presentDocument).toHaveBeenCalledOnce())

        expect(downloadDocument).toHaveBeenCalledWith(4)
        expect(presentDocument).toHaveBeenCalledWith(file)
    })

    it('reports a refused download instead of handing anything over', async () => {
        downloadDocument.mockRejectedValue(new Error('denied'))

        const panel = mountPanel('verlust.pdf')
        await panel.get('[data-testid="loss-report-download"]').trigger('click')
        await vi.waitFor(() => expect(panel.text()).toContain('common.error'))

        expect(presentDocument).not.toHaveBeenCalled()
    })
})
