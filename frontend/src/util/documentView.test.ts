/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {closeDocument, getViewedDocument, presentDocument} from './documentView'

const saveBlob = vi.fn()

vi.mock('@/util/saveBlob', () => ({saveBlob: (blob: Blob, name: string) => saveBlob(blob, name)}))

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

        presentDocument(blob, 'report.pdf')

        expect(saveBlob).toHaveBeenCalledWith(blob, 'report.pdf')
        expect(getViewedDocument().value).toBeNull()
    })

    it('opens the document in the hand instead of saving it', () => {
        pointer('coarse')
        const blob = new Blob(['%PDF'], {type: 'application/pdf'})

        presentDocument(blob, 'report.pdf')

        expect(saveBlob).not.toHaveBeenCalled()
        expect(getViewedDocument().value).toMatchObject({filename: 'report.pdf', mimeType: 'application/pdf'})
    })

    /** Showing an archive is not on offer, so opening one would only cost the reader a press. */
    it('saves what nothing can draw, even in the hand', () => {
        pointer('coarse')
        const blob = new Blob(['zip'], {type: 'application/zip'})

        presentDocument(blob, 'export.zip')

        expect(saveBlob).toHaveBeenCalledWith(blob, 'export.zip')
        expect(getViewedDocument().value).toBeNull()
    })

    /**
     * A list of values is not a document to read: shown in the page it is its own bytes as words,
     * and a reader who exported it wanted it in something that opens lists.
     */
    it('saves a list of values rather than showing it', () => {
        pointer('coarse')
        const blob = new Blob(['a,b'], {type: 'text/csv'})

        presentDocument(blob, 'list.csv')

        expect(saveBlob).toHaveBeenCalledWith(blob, 'list.csv')
        expect(getViewedDocument().value).toBeNull()
    })

    /** The type a browser offers is often nothing at all, so the name has to answer for it. */
    it('takes the kind from the name where the type says nothing', () => {
        pointer('coarse')

        presentDocument(new Blob(['%PDF'], {type: 'application/octet-stream'}), 'report.pdf')

        expect(saveBlob).not.toHaveBeenCalled()
        expect(getViewedDocument().value).toMatchObject({filename: 'report.pdf'})
    })

    it('lets go of the document when the reader closes it', () => {
        pointer('coarse')
        presentDocument(new Blob(['%PDF'], {type: 'application/pdf'}), 'report.pdf')

        closeDocument()

        expect(getViewedDocument().value).toBeNull()
    })
})
