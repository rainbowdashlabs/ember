/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {closeDocument, getViewedDocument, presentDocument} from './documentView'

const saveBlob = vi.fn()

vi.mock('@/util/downloadAuthed', () => ({saveBlob: (blob: Blob, name: string) => saveBlob(blob, name)}))

function pointer(kind: 'fine' | 'coarse') {
    window.matchMedia = vi.fn((query: string) => ({matches: kind === 'fine' && query.includes('fine')})) as never
}

/**
 * Which way a finished document reaches the reader.
 *
 * <p>At a desk it is saved, because that is where saving works and where a reader expects to find it
 * afterwards. In the hand it is opened instead, because a handheld browser will not take bytes from
 * the page and the reader is left with nothing at all.
 */

describe('presentDocument', () => {
    beforeEach(() => {
        saveBlob.mockReset()
        closeDocument()
        pointer('fine')
    })

    it('saves the document at a desk', () => {
        const blob = new Blob(['%PDF'], {type: 'application/pdf'})

        presentDocument({blob, filename: 'Anwesenheit - Januar 2026.pdf'})

        expect(saveBlob).toHaveBeenCalledWith(blob, 'Anwesenheit - Januar 2026.pdf')
        expect(getViewedDocument().value).toBeNull()
    })

    it('opens the document in the hand instead of saving it', () => {
        pointer('coarse')
        const blob = new Blob(['%PDF'], {type: 'application/pdf'})

        presentDocument({blob, filename: 'Anwesenheit - Januar 2026.pdf'})

        expect(saveBlob).not.toHaveBeenCalled()
        expect(getViewedDocument().value)
            .toMatchObject({filename: 'Anwesenheit - Januar 2026.pdf', mimeType: 'application/pdf'})
    })

    /** Showing an archive is not on offer, so opening one would only cost the reader a press. */
    it('saves what nothing can draw, even in the hand', () => {
        pointer('coarse')
        const blob = new Blob(['zip'], {type: 'application/zip'})

        presentDocument({blob, filename: 'Datenauskunft.zip'})

        expect(saveBlob).toHaveBeenCalledWith(blob, 'Datenauskunft.zip')
        expect(getViewedDocument().value).toBeNull()
    })

    it('takes the kind from the bytes it was handed', () => {
        pointer('coarse')

        presentDocument({blob: new Blob(['text'], {type: 'text/csv'}), filename: 'Mitglieder.csv'})

        expect(getViewedDocument().value).toMatchObject({mimeType: 'text/csv'})
    })

    it('lets go of the document when the reader closes it', () => {
        pointer('coarse')
        presentDocument({blob: new Blob(['%PDF'], {type: 'application/pdf'}), filename: 'Anwesenheit.pdf'})

        closeDocument()

        expect(getViewedDocument().value).toBeNull()
    })
})
