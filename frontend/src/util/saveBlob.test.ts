/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {SAVED_BLOB_LIFETIME_MS, SAVED_BLOB_TYPE, SaveResult, saveBlob} from './saveBlob'

vi.mock('@/api/client', () => ({default: {}}))

function pointer(kind: 'fine' | 'coarse') {
    window.matchMedia = vi.fn((query: string) => ({matches: kind === 'fine' && query.includes('fine')})) as never
}

describe('saveBlob', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        URL.createObjectURL = vi.fn(() => 'blob:saved')
        URL.revokeObjectURL = vi.fn()
        pointer('fine')
    })

    afterEach(() => {
        vi.restoreAllMocks()
        vi.useRealTimers()
    })

    /**
     * Safari on iOS reads the URL only after the click has returned, so revoking it in the same
     * turn left the download button doing nothing on an iPhone.
     */
    it('keeps the URL alive after the click so a late reader still finds it', async () => {
        const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

        await saveBlob(new Blob(['pdf']), 'handout.pdf')

        expect(click).toHaveBeenCalledOnce()
        expect(URL.revokeObjectURL).not.toHaveBeenCalled()
        vi.advanceTimersByTime(SAVED_BLOB_LIFETIME_MS)
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:saved')
    })

    it('counts the download link as done at a desk, where it works', async () => {
        vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

        expect(await saveBlob(new Blob(['pdf']), 'handout.pdf')).toBe(SaveResult.DONE)
    })

    /**
     * A PDF handed over as a PDF is opened rather than saved by a browser with no viewer for one,
     * which left an installed app sitting on an empty address instead of saving the document.
     */
    it('hands the bytes over as something no browser tries to open', async () => {
        vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

        await saveBlob(new Blob(['pdf'], {type: 'application/pdf'}), 'handout.pdf')

        expect((vi.mocked(URL.createObjectURL).mock.calls[0]?.[0] as Blob).type).toBe(SAVED_BLOB_TYPE)
    })

    it('names the file and leaves no link behind', async () => {
        let clicked: HTMLAnchorElement | null = null
        vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
            clicked = this
        })

        await saveBlob(new Blob(['pdf']), 'handout.pdf')

        expect(clicked!.download).toBe('handout.pdf')
        expect(document.querySelector('a[download]')).toBeNull()
    })

    describe('on an iPhone', () => {
        const IPHONE = 'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 GSA/380.0'
        let share: ReturnType<typeof vi.fn>
        let click: ReturnType<typeof vi.spyOn>

        beforeEach(() => {
            vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(IPHONE)
            share = vi.fn(() => Promise.resolve())
            Object.defineProperty(navigator, 'share', {value: share, configurable: true})
            Object.defineProperty(navigator, 'canShare', {value: vi.fn(() => true), configurable: true})
            click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
        })

        afterEach(() => {
            Reflect.deleteProperty(navigator, 'share')
            Reflect.deleteProperty(navigator, 'canShare')
        })

        /**
         * A browser embedded in another app, the Google app's among them, ignores a download link to
         * a blob, so a guardian pressing the download button of an event file saw nothing happen.
         */
        it('hands the file to the share sheet instead of a download link', async () => {
            saveBlob(new Blob(['pdf'], {type: 'application/pdf'}), 'handout.pdf')
            await vi.runAllTimersAsync()

            const shared = share.mock.calls[0]?.[0].files[0] as File
            expect(shared.name).toBe('handout.pdf')
            expect(shared.type).toBe('application/pdf')
            expect(click).not.toHaveBeenCalled()
        })

        it('does nothing more when the reader closes the share sheet', async () => {
            share.mockRejectedValue(new DOMException('dismissed', 'AbortError'))

            saveBlob(new Blob(['pdf']), 'handout.pdf')
            await vi.runAllTimersAsync()

            expect(click).not.toHaveBeenCalled()
        })

        it('falls back to the download link where the share sheet refuses', async () => {
            share.mockRejectedValue(new DOMException('no gesture', 'NotAllowedError'))

            saveBlob(new Blob(['pdf']), 'handout.pdf')
            await vi.runAllTimersAsync()

            expect(click).toHaveBeenCalledOnce()
        })

        /**
         * A rejection does not always arrive as a DOMException this realm recognises. Read by type
         * rather than by name, a reader who had just declined to save the file had it saved anyway.
         */
        it('reads a dismissal by its name, whatever the rejection was made of', async () => {
            share.mockRejectedValue({name: 'AbortError', message: 'dismissed'})

            const result = await saveBlob(new Blob(['pdf']), 'handout.pdf')

            expect(result).toBe(SaveResult.DISMISSED)
            expect(click).not.toHaveBeenCalled()
        })

        /**
         * The download link is no answer inside another app's browser, so a caller has to be able
         * to tell the reader rather than leave them pressing a button that does nothing and says
         * nothing.
         */
        it('says the file could not be saved when neither route works', async () => {
            share.mockRejectedValue(new DOMException('no gesture', 'NotAllowedError'))

            const result = await saveBlob(new Blob(['pdf']), 'handout.pdf')

            expect(result).toBe(SaveResult.UNAVAILABLE)
        })

        it('uses the download link where the file cannot be shared at all', async () => {
            Object.defineProperty(navigator, 'canShare', {value: vi.fn(() => false), configurable: true})

            await saveBlob(new Blob(['pdf']), 'handout.pdf')

            expect(share).not.toHaveBeenCalled()
            expect(click).toHaveBeenCalledOnce()
        })
    })

    describe('on a device without a mouse', () => {
        let share: ReturnType<typeof vi.fn>

        beforeEach(() => {
            pointer('coarse')
            share = vi.fn(() => Promise.resolve())
            Object.defineProperty(navigator, 'share', {value: share, configurable: true})
            Object.defineProperty(navigator, 'canShare', {value: vi.fn(() => true), configurable: true})
        })

        afterEach(() => {
            Reflect.deleteProperty(navigator, 'share')
            Reflect.deleteProperty(navigator, 'canShare')
        })

        /**
         * An Android browser that will not take bytes from the page navigates to the blob instead and
         * draws nothing, which reached a reader as a blank tab where a report should have been.
         */
        it('hands the file to the share sheet although it is not an Apple device', async () => {
            const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

            saveBlob(new Blob(['pdf'], {type: 'application/pdf'}), 'report.pdf')
            await vi.runAllTimersAsync()

            expect((share.mock.calls[0]?.[0].files[0] as File).name).toBe('report.pdf')
            expect(click).not.toHaveBeenCalled()
        })
    })

    describe('where a service worker is in charge', () => {
        let clicked: HTMLAnchorElement | null

        beforeEach(() => {
            clicked = null
            vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
                clicked = this
            })
            const controller = {
                postMessage: (_message: unknown, transfer: Transferable[]) =>
                    (transfer[0] as MessagePort).postMessage({held: true}),
            }
            Object.defineProperty(navigator, 'serviceWorker', {value: {controller}, configurable: true})
        })

        afterEach(() => Reflect.deleteProperty(navigator, 'serviceWorker'))

        /**
         * Firefox reads a blob address's name, finds a document it cannot draw on a phone and
         * navigates to it instead of saving it, which in an installed app loses the page the reader
         * was on. Fetched from an address of its own the file is a download and nothing else.
         */
        it('fetches the file from the worker rather than from a blob address', async () => {
            await saveBlob(new Blob(['pdf'], {type: 'application/pdf'}), 'handout.pdf')

            expect(clicked!.getAttribute('href')).toMatch(/^\/save-file\/.+/)
            expect(clicked!.download).toBe('handout.pdf')
            expect(URL.createObjectURL).not.toHaveBeenCalled()
        })

        /**
         * A worker takes charge a moment after the page has loaded, and saving in that moment is
         * the first thing a reader does after an update.
         */
        it('waits for a worker that has only just been registered', async () => {
            const controller = {
                postMessage: (_message: unknown, transfer: Transferable[]) =>
                    (transfer[0] as MessagePort).postMessage({held: true}),
            }
            const workers: {controller: unknown; ready: Promise<unknown>} = {
                controller: null,
                ready: Promise.resolve().then(() => (workers.controller = controller)),
            }
            Object.defineProperty(navigator, 'serviceWorker', {value: workers, configurable: true})

            await saveBlob(new Blob(['pdf']), 'handout.pdf')

            expect(clicked!.getAttribute('href')).toMatch(/^\/save-file\/.+/)
        })

        it('falls back to the blob address when the worker does not answer', async () => {
            Object.defineProperty(navigator, 'serviceWorker', {
                value: {controller: {postMessage: () => undefined}},
                configurable: true,
            })

            const saving = saveBlob(new Blob(['pdf']), 'handout.pdf')
            await vi.runAllTimersAsync()

            expect(await saving).toBe(SaveResult.DONE)
            expect(clicked!.getAttribute('href')).toBe('blob:saved')
        })
    })

    describe('on Firefox for Android', () => {
        const ANDROID_GECKO = 'Mozilla/5.0 (Android 17; Mobile; rv:156.0) Gecko/156.0 Firefox/156.0'
        let opened: string | null
        let clicked: HTMLAnchorElement | null
        let handed: {contentType: string; disposition: string} | null = null

        beforeEach(() => {
            opened = null
            clicked = null
            vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(ANDROID_GECKO)
            vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
                clicked = this
            })
            window.open = vi.fn((address?: string | URL) => {
                opened = String(address)
                return {} as Window
            }) as never
            const controller = {
                postMessage: (message: {contentType: string; disposition: string}, transfer: Transferable[]) => {
                    handed = message
                    ;(transfer[0] as MessagePort).postMessage({held: true})
                },
            }
            Object.defineProperty(navigator, 'serviceWorker', {value: {controller}, configurable: true})
            pointer('coarse')
        })

        afterEach(() => Reflect.deleteProperty(navigator, 'serviceWorker'))

        /**
         * Firefox renders a PDF itself and opens it beside the window that asked, which in an
         * installed app is a window with nowhere to go: the reader lost their page and got no
         * document. Opened on purpose, the viewer arrives with its own saving in it.
         */
        it('opens a document with pages in the browser viewer instead of saving it', async () => {
            expect(await saveBlob(new Blob(['pdf'], {type: 'application/pdf'}), 'handout.pdf')).toBe(SaveResult.DONE)

            expect(opened).toMatch(/^\/save-file\/.+/)
            expect(handed).toMatchObject({contentType: 'application/pdf', disposition: 'inline'})
            expect(clicked).toBeNull()
        })

        /** The name is how Firefox decides, so a PDF served as something else is still a PDF to it. */
        it('reads the document off the name as well as the type', async () => {
            await saveBlob(new Blob(['pdf'], {type: 'application/octet-stream'}), 'handout.pdf')

            expect(opened).toMatch(/^\/save-file\/.+/)
        })

        it('saves everything else, which this browser saves perfectly well', async () => {
            await saveBlob(new Blob(['csv'], {type: 'text/csv'}), 'list.csv')

            expect(window.open).not.toHaveBeenCalled()
            expect(clicked!.getAttribute('href')).toMatch(/^\/save-file\/.+/)
            expect(handed).toMatchObject({disposition: 'attachment'})
        })

        it('saves the document after all where the window is refused', async () => {
            window.open = vi.fn(() => null) as never

            expect(await saveBlob(new Blob(['pdf'], {type: 'application/pdf'}), 'handout.pdf')).toBe(SaveResult.DONE)
            expect(clicked!.getAttribute('href')).toMatch(/^\/save-file\/.+/)
        })
    })

    /**
     * A phone browser of its own saves what the link hands it, in its window and in an installed
     * app alike. Reported as a failure regardless, it told a reader that a file they had just been
     * offered to save could not be saved.
     */
    it('counts the download link as done in a phone browser that will not share files', async () => {
        const firefox = 'Mozilla/5.0 (Android 15; Mobile; rv:143.0) Gecko/143.0 Firefox/143.0'
        vi.spyOn(navigator, 'userAgent', 'get').mockReturnValue(firefox)
        vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
        pointer('coarse')

        expect(await saveBlob(new Blob(['csv']), 'list.csv')).toBe(SaveResult.DONE)
    })

    it('keeps the download link on a mouse, where saving is what a reader expects', async () => {
        const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
        const share = vi.fn(() => Promise.resolve())
        Object.defineProperty(navigator, 'share', {value: share, configurable: true})
        Object.defineProperty(navigator, 'canShare', {value: vi.fn(() => true), configurable: true})

        await saveBlob(new Blob(['pdf']), 'report.pdf')

        expect(share).not.toHaveBeenCalled()
        expect(click).toHaveBeenCalledOnce()
        Reflect.deleteProperty(navigator, 'share')
        Reflect.deleteProperty(navigator, 'canShare')
    })
})
