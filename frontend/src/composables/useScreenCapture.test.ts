/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {afterEach, describe, expect, it, vi} from 'vitest'
import {captureScreen} from './useScreenCapture'

/**
 * Asking for a picture of the screen, and the two ways that went wrong.
 *
 * <p>Both were invisible to a test that only checked a happy path against a browser doing
 * everything right: one needed the page to be hidden, which is what the sharing prompt does on a
 * desktop where that prompt is a window of its own, and the other needed a video that answers
 * without having read its own metadata.
 */
describe('captureScreen', () => {

    afterEach(() => {
        vi.restoreAllMocks()
        vi.unstubAllGlobals()
    })

    /** A video element that behaves the way the one in the capture is used. */
    function fakeVideo(options: {width: number; height: number; playRejects?: boolean; framesArrive: boolean}) {
        const listeners: Record<string, () => void> = {}
        return {
            srcObject: null as MediaStream | null,
            muted: false,
            videoWidth: options.width,
            videoHeight: options.height,
            readyState: options.framesArrive ? 2 : 0,
            setAttribute: () => {},
            play: () => (options.playRejects
                ? Promise.reject(Object.assign(new Error('The fetching process for the media resource was aborted'), {
                    name: 'AbortError',
                }))
                : Promise.resolve()),
            pause: () => {},
            addEventListener: (name: string, handler: () => void) => {
                listeners[name] = handler
            },
        }
    }

    function stubDocument(video: unknown, drawn: {called: boolean}) {
        const canvas = {
            width: 0,
            height: 0,
            getContext: () => ({
                drawImage: () => {
                    drawn.called = true
                },
            }),
        }
        vi.stubGlobal('document', {
            createElement: (tag: string) => (tag === 'video' ? video : canvas),
        })
        return canvas
    }

    function stubSharing(track: Partial<MediaStreamTrack>) {
        const stopped: string[] = []
        const full = {getSettings: () => ({}), stop: () => stopped.push('stopped'), ...track}
        vi.stubGlobal('navigator', {
            mediaDevices: {
                getDisplayMedia: () => Promise.resolve({
                    getVideoTracks: () => [full],
                    getTracks: () => [full],
                }),
            },
        })
        return stopped
    }

    /**
     * The one that made the picture never arrive.
     *
     * <p>The wait used to be a single animation frame. A browser stops running those while the page
     * is hidden, and the sharing prompt on Linux hides it for exactly as long as somebody spends
     * choosing what to share, so the wait outlived the prompt and the capture simply never
     * finished. Here no frame callback ever fires and no loaded event is sent, and a picture still
     * has to come back.
     */
    it('gives back a picture even where no frame ever announces itself', async () => {
        const drawn = {called: false}
        const video = fakeVideo({width: 1280, height: 720, framesArrive: false})
        stubDocument(video, drawn)
        const stopped = stubSharing({})

        const {picture, refused} = await captureScreen()

        expect(picture, 'the picture arrives rather than the wait hanging').not.toBeNull()
        expect(picture?.width).toBe(1280)
        expect(picture?.height).toBe(720)
        expect(drawn.called).toBe(true)
        expect(refused).toBe(false)
        expect(stopped.length, 'and sharing is stopped again').toBeGreaterThan(0)
    })

    /**
     * A video that has not read its metadata reports no size, and a canvas of no size is the empty
     * picture this used to hand back as if nothing had been chosen. What is being shared has a size
     * whether or not the video has caught up.
     */
    it('takes the size from what is being shared where the video does not know it', async () => {
        const drawn = {called: false}
        const video = fakeVideo({width: 0, height: 0, framesArrive: true})
        stubDocument(video, drawn)
        stubSharing({getSettings: () => ({width: 1920, height: 1080}) as MediaTrackSettings})

        const {picture} = await captureScreen()

        expect(picture?.width).toBe(1920)
        expect(picture?.height).toBe(1080)
    })

    /**
     * A play that is interrupted rejects with a media abort. It says nothing anybody can act on and
     * must not escape: left alone it reaches the global handler and is filed as a fault in the
     * product, which is how a cancelled screenshot turned into an error report.
     */
    it('swallows the media abort a broken-off play rejects with', async () => {
        const drawn = {called: false}
        const video = fakeVideo({width: 800, height: 600, framesArrive: true, playRejects: true})
        stubDocument(video, drawn)
        stubSharing({})

        const {picture} = await captureScreen()

        expect(picture, 'a frame can still be there after the play was cut short').not.toBeNull()
    })

    /** Turning the prompt down is an answer, and is told apart from something going wrong. */
    it('says a refusal is a refusal', async () => {
        vi.stubGlobal('navigator', {
            mediaDevices: {
                getDisplayMedia: () => Promise.reject(Object.assign(new Error('denied'), {name: 'NotAllowedError'})),
            },
        })

        const {picture, refused} = await captureScreen()

        expect(picture).toBeNull()
        expect(refused).toBe(true)
    })

    /** Anything else is a failure, which is a different thing to say to somebody. */
    it('tells a failure apart from a refusal', async () => {
        vi.stubGlobal('navigator', {
            mediaDevices: {
                getDisplayMedia: () => Promise.reject(new Error('no screen here')),
            },
        })

        const {picture, refused} = await captureScreen()

        expect(picture).toBeNull()
        expect(refused).toBe(false)
    })
})
