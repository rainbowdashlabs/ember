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

/** What happened in the order it happened, which is the whole point of the two ordering tests. */
const events: string[] = []

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
        events.length = 0
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

    /**
     * The page pdf.js already holds answers at once and the one it has to fetch does not, so two
     * turns in quick succession can finish in the wrong order. Whoever was asked for last has to
     * win, or the canvas ends on a page the pager is no longer showing.
     */
    it('leaves the last page asked for on the canvas when an earlier one answers late', async () => {
        const slowPage = deferred<void>()
        getDocument.mockReturnValue({
            promise: Promise.resolve(documentDelaying(2, 2, slowPage.promise)),
            destroy: vi.fn(),
        })

        const canvas = mount(PdfCanvas, {props: {source: new Blob(['pdf']), page: 1}})
        await flushPromises()

        await canvas.setProps({page: 2})
        await canvas.setProps({page: 1})
        await flushPromises()
        slowPage.resolve()
        await flushPromises()

        expect(events.filter(entry => entry.startsWith('render')).at(-1)).toBe('render:1')
    })

    /**
     * Cancelling a render only asks it to stop; until its promise has settled the canvas is still
     * pdf.js's, and resizing it or starting a second render there fails the new render outright.
     */
    it('waits for a cancelled render to let go before drawing over it', async () => {
        getDocument.mockReturnValue({promise: Promise.resolve(documentHolding(3)), destroy: vi.fn()})

        const canvas = mount(PdfCanvas, {props: {source: new Blob(['pdf']), page: 1}})
        await flushPromises()

        await canvas.setProps({page: 2})
        await flushPromises()

        expect(events).toEqual(['render:1', 'cancel', 'settled', 'render:2'])
    })
})

function deferred<T>() {
    let resolve: (value: T | PromiseLike<T>) => void = () => undefined
    const promise = new Promise<T>(done => {
        resolve = done
    })
    return {promise, resolve}
}

function pageDrawing(num: number, task: {promise: Promise<void>; cancel: () => void}) {
    return {
        getViewport: ({scale}: {scale: number}) => ({width: 100 * scale, height: 200 * scale}),
        render: (options: unknown) => {
            render(options)
            events.push(`render:${num}`)
            return task
        },
    }
}

/** A document where one page is fetched only once the given promise settles, and the rest at once. */
function documentDelaying(pageCount: number, slow: number, gate: Promise<void>) {
    return {
        numPages: pageCount,
        getPage: async (num: number) => {
            getPage(num)
            if (num === slow) await gate
            return pageDrawing(num, {promise: Promise.resolve(), cancel: vi.fn()})
        },
    }
}

/**
 * A document whose renders never finish on their own, so that what a cancel does is observable:
 * the task lets go a tick after it is asked to, the way pdf.js unwinds its own render loop.
 */
function documentHolding(pageCount: number) {
    return {
        numPages: pageCount,
        getPage: (num: number) => {
            getPage(num)
            let settle: () => void = () => undefined
            const promise = new Promise<void>(done => {
                settle = done
            })
            const cancel = () => {
                events.push('cancel')
                queueMicrotask(() => {
                    events.push('settled')
                    settle()
                })
            }
            return Promise.resolve(pageDrawing(num, {promise, cancel}))
        },
    }
}
