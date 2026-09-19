/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
// @vitest-environment happy-dom
import {afterEach, beforeEach, describe, expect, it, vi} from 'vitest'
import {SAVED_BLOB_LIFETIME_MS, saveBlob} from './downloadAuthed'

vi.mock('@/api/client', () => ({default: {}}))

describe('saveBlob', () => {
    beforeEach(() => {
        vi.useFakeTimers()
        URL.createObjectURL = vi.fn(() => 'blob:saved')
        URL.revokeObjectURL = vi.fn()
    })

    afterEach(() => {
        vi.restoreAllMocks()
        vi.useRealTimers()
    })

    /**
     * Safari on iOS reads the URL only after the click has returned, so revoking it in the same
     * turn left the download button doing nothing on an iPhone.
     */
    it('keeps the URL alive after the click so a late reader still finds it', () => {
        const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})

        saveBlob(new Blob(['pdf']), 'handout.pdf')

        expect(click).toHaveBeenCalledOnce()
        expect(URL.revokeObjectURL).not.toHaveBeenCalled()
        vi.advanceTimersByTime(SAVED_BLOB_LIFETIME_MS)
        expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:saved')
    })

    it('names the file and leaves no link behind', () => {
        let clicked: HTMLAnchorElement | null = null
        vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
            clicked = this
        })

        saveBlob(new Blob(['pdf']), 'handout.pdf')

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

        it('uses the download link where the file cannot be shared at all', () => {
            Object.defineProperty(navigator, 'canShare', {value: vi.fn(() => false), configurable: true})

            saveBlob(new Blob(['pdf']), 'handout.pdf')

            expect(share).not.toHaveBeenCalled()
            expect(click).toHaveBeenCalledOnce()
        })
    })
})
