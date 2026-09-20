/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {beforeEach, describe, expect, it, vi} from 'vitest'
import {flushPromises, mount} from '@vue/test-utils'
import PdfCanvas from './PdfCanvas.vue'

const getDocument = vi.fn()
const render = vi.fn()
const getPage = vi.fn()

vi.mock('pdfjs-dist', () => ({
    GlobalWorkerOptions: {workerSrc: ''},
    getDocument: (options: {data: Uint8Array}) => getDocument(options),
}))

/**
 * The one thing this component is for: a page of a PDF drawn where a phone can read it.
 *
 * <p>The document itself is pdf.js's business and is stood in for here. What is worth holding is the
 * wiring around it: that a page is drawn only once bytes exist, that asking for another page draws
 * that one, that a refusal is reported rather than swallowed, and that the caller's own memory is not
 * handed away, because pdf.js takes what it is given and leaves the caller with an emptied buffer.
 */

function documentOf(pageCount: number) {
    return {
        numPages: pageCount,
        getPage: (num: number) => {
            getPage(num)
            return Promise.resolve({
                getViewport: ({scale}: {scale: number}) => ({width: 100 * scale, height: 200 * scale}),
                render: (options: unknown) => {
                    render(options)
                    return {promise: Promise.resolve(), cancel: vi.fn()}
                },
            })
        },
    }
}

function opens(pageCount: number) {
    getDocument.mockReturnValue({promise: Promise.resolve(documentOf(pageCount)), destroy: vi.fn()})
}

describe('PdfCanvas', () => {
    beforeEach(() => {
        getDocument.mockReset()
        render.mockReset()
        getPage.mockReset()
        HTMLCanvasElement.prototype.getContext = vi.fn(() => ({clearRect: vi.fn()})) as never
    })

    it('draws nothing at all while there are no bytes', async () => {
        mount(PdfCanvas, {props: {source: null}})
        await flushPromises()

        expect(getDocument).not.toHaveBeenCalled()
    })

    it('reports how many pages the document has', async () => {
        opens(7)

        const canvas = mount(PdfCanvas, {props: {source: new Blob(['pdf'])}})
        await flushPromises()

        expect(canvas.emitted('loaded')).toEqual([[7]])
        expect(getPage).toHaveBeenCalledWith(1)
    })

    it('draws the page it is asked for, and redraws when that changes', async () => {
        opens(7)

        const canvas = mount(PdfCanvas, {props: {source: new Blob(['pdf']), page: 3}})
        await flushPromises()
        expect(getPage).toHaveBeenCalledWith(3)

        await canvas.setProps({page: 4})
        await flushPromises()
        expect(getPage).toHaveBeenCalledWith(4)
    })

    it('stays inside the document when asked for a page beyond it', async () => {
        opens(2)

        mount(PdfCanvas, {props: {source: new Blob(['pdf']), page: 99}})
        await flushPromises()

        expect(getPage).toHaveBeenCalledWith(2)
    })

    it('reports a document that will not open instead of failing silently', async () => {
        getDocument.mockReturnValue({promise: Promise.reject(new Error('broken')), destroy: vi.fn()})

        const canvas = mount(PdfCanvas, {props: {source: new Blob(['pdf'])}})
        await flushPromises()

        expect(canvas.emitted('failed')).toHaveLength(1)
    })

    /**
     * pdf.js transfers the memory it is handed, which would leave a caller holding an emptied buffer
     * and nothing to retry or save with.
     */
    it('keeps the bytes it was given out of the reader', async () => {
        opens(1)
        const source = new ArrayBuffer(8)

        mount(PdfCanvas, {props: {source}})
        await flushPromises()

        expect(getDocument.mock.calls[0]?.[0].data.buffer).not.toBe(source)
    })
})
