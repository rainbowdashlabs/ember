/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref, type Ref} from 'vue'

/** A rectangle over the picture, in the picture's own pixels rather than the screen's. */
export interface Cover {
    x: number
    y: number
    width: number
    height: number
}

/** The long edge a picture is scaled to before it is sent. Enough to read a layout by. */
const MAX_EDGE = 1600

/** What the picture may weigh once written out, before quality is given up to meet it. */
const MAX_BYTES = 2 * 1024 * 1024

/**
 * Whether this browser can be asked for a picture of the screen.
 *
 * <p>Asked rather than assumed: the call exists only where the page is served over a secure
 * connection, and a button that does nothing is worse than a button that is not there.
 */
export function canCaptureScreen(): boolean {
    return typeof navigator !== 'undefined'
        && !!navigator.mediaDevices
        && typeof navigator.mediaDevices.getDisplayMedia === 'function'
}

/** What came of asking for a picture, which is not the same question as whether there is one. */
export interface Capture {
    picture: HTMLCanvasElement | null
    /** Whether the person turned the prompt down, as against it going wrong on its own. */
    refused: boolean
}

/** How long a frame is waited for before whatever the video already holds is drawn. */
const FRAME_WAIT = 2000

/**
 * How long to let the screen settle after sharing begins, before the frame is kept.
 *
 * <p>The first frames still have the browser's own sharing prompt in them: it is taken down after
 * the choice is made rather than before sharing starts, so a picture grabbed the instant a frame is
 * available is a picture of the prompt. The video goes on receiving frames throughout, so waiting
 * costs nothing but the wait and what is kept is the screen as it looks once the asking is over.
 */
const SETTLE = 700

/**
 * What is asked for, which is this tab before anything else.
 *
 * <p>Both of these are hints and a browser may ignore either, but where they are read they take the
 * desktop out of the question. Sharing a window or a screen is the operating system's business, and
 * on Linux that means its own dialog on top of the browser's, which is a great deal of asking for
 * one picture of one page. Sharing a tab is the browser's business alone, and the tab wanted is
 * almost always the one the report is being written on.
 *
 * <p>Nothing is narrowed by this. Whoever is asked can still pick any window or screen they like:
 * the prompt is the browser's and says what is being shared, which is what keeps this honest.
 */
const WHAT_TO_SHARE: DisplayMediaStreamOptions = {
    video: {displaySurface: 'browser'},
    audio: false,
    preferCurrentTab: true,
} as DisplayMediaStreamOptions

/**
 * Takes one picture of whatever the person chooses to share, and stops sharing at once.
 *
 * <p>The browser asks them which window or tab, and that prompt is what makes this honest: the
 * browser says what is being shared, not this code. The track is stopped the moment a frame is
 * held, so nothing goes on watching after the one picture.
 *
 * <p>Nothing here waits on an animation frame. A browser stops running those while the page is
 * hidden, and on Linux the sharing prompt is a window of the desktop rather than of the browser, so
 * the page is hidden for exactly as long as the person spends choosing. Waiting for a frame that
 * way is waiting for something that has been switched off, which is why the picture never arrived:
 * the wait never ended, the prompt was answered, and nothing happened. What is waited for instead
 * is the video saying it has data, and never for longer than {@link FRAME_WAIT}.
 */
export async function captureScreen(): Promise<Capture> {
    if (!canCaptureScreen()) return {picture: null, refused: false}
    let stream: MediaStream | null = null
    try {
        stream = await navigator.mediaDevices.getDisplayMedia(WHAT_TO_SHARE)
    } catch (e) {
        return {picture: null, refused: isRefusal(e)}
    }
    try {
        const track = stream.getVideoTracks()[0]
        if (!track) return {picture: null, refused: false}
        const video = document.createElement('video')
        video.srcObject = stream
        video.muted = true
        video.setAttribute('playsinline', 'true')
        await played(video)
        await firstFrame(video)
        await settled()
        const picture = drawn(video, track)
        video.pause()
        video.srcObject = null
        return {picture, refused: false}
    } catch {
        return {picture: null, refused: false}
    } finally {
        stream.getTracks().forEach(track => track.stop())
    }
}

/**
 * Whether the person said no, rather than something failing.
 *
 * <p>Turning a prompt down is an answer and not a fault, so it is told apart here and reported
 * nowhere: a report that somebody declined to share their screen is noise in the error log of every
 * instance that has this switched on.
 */
function isRefusal(error: unknown): boolean {
    const name = (error as {name?: string} | null)?.name
    return name === 'NotAllowedError' || name === 'AbortError'
}

/**
 * Starts the video and swallows whatever the attempt rejects with.
 *
 * <p>A play that is interrupted rejects with a media abort, which says nothing anybody can act on
 * and must not escape: left alone it reaches the global handler and is filed as a fault in the
 * product. Whether a frame actually arrived is answered by looking at the video, not by this.
 */
async function played(video: HTMLVideoElement): Promise<void> {
    try {
        await video.play()
    } catch {
        /* a frame may still arrive; the wait below is what decides */
    }
}

/**
 * Waits until the video holds something to draw, and gives up rather than hanging.
 *
 * <p>The frame callback where a browser has one, the loaded event where it does not, and a timeout
 * behind both, because a wait that can outlast the dialog it belongs to is how this failed before.
 */
function firstFrame(video: HTMLVideoElement): Promise<void> {
    const onFrame = (video as unknown as {requestVideoFrameCallback?: (cb: () => void) => number})
        .requestVideoFrameCallback
    return new Promise(resolve => {
        let done = false
        const finish = () => {
            if (done) return
            done = true
            resolve()
        }
        const timer = setTimeout(finish, FRAME_WAIT)
        const stop = () => {
            clearTimeout(timer)
            finish()
        }
        if (video.readyState >= 2) {
            stop()
            return
        }
        if (typeof onFrame === 'function') {
            onFrame.call(video, stop)
            return
        }
        video.addEventListener('loadeddata', stop, {once: true})
    })
}

/** Lets the sharing prompt finish leaving the screen before the frame that is kept is taken. */
function settled(): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, SETTLE))
}

/**
 * Draws what the video holds, sized by the video where it knows and by the track where it does not.
 *
 * <p>A video that has not read its metadata reports no size at all, and a canvas of no size is the
 * empty picture this used to hand back. The track carries the size of what is being shared whether
 * or not the video has caught up, so it answers where the video cannot.
 */
function drawn(video: HTMLVideoElement, track: MediaStreamTrack): HTMLCanvasElement | null {
    const settings = track.getSettings()
    const width = video.videoWidth || settings.width || 0
    const height = video.videoHeight || settings.height || 0
    if (width <= 0 || height <= 0) return null
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    canvas.getContext('2d')?.drawImage(video, 0, 0, width, height)
    return canvas
}

/** Reads a picture somebody attached themselves, which is the way where sharing is refused. */
export async function readPictureFile(file: File): Promise<HTMLCanvasElement | null> {
    if (!file.type.startsWith('image/')) return null
    return pictureFrom(file)
}

/**
 * Draws a picture that arrived as bytes onto a canvas, which is what covering it needs.
 *
 * <p>The same path a stored picture comes back through when somebody looks at it again before it is
 * passed on: covering works on pixels, and a blob is not pixels until something has drawn it.
 */
export async function pictureFrom(blob: Blob): Promise<HTMLCanvasElement | null> {
    const url = URL.createObjectURL(blob)
    try {
        const image = new Image()
        image.src = url
        await image.decode()
        const canvas = document.createElement('canvas')
        canvas.width = image.naturalWidth
        canvas.height = image.naturalHeight
        canvas.getContext('2d')?.drawImage(image, 0, 0)
        return canvas
    } catch {
        return null
    } finally {
        URL.revokeObjectURL(url)
    }
}

/** Scales a picture down to something worth sending, keeping its proportions. */
export function scaled(source: HTMLCanvasElement, maxEdge = MAX_EDGE): HTMLCanvasElement {
    const longest = Math.max(source.width, source.height)
    if (longest <= maxEdge) return source
    const factor = maxEdge / longest
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(source.width * factor)
    canvas.height = Math.round(source.height * factor)
    canvas.getContext('2d')?.drawImage(source, 0, 0, canvas.width, canvas.height)
    return canvas
}

/**
 * Draws the covers into the picture and writes it out, which is the moment a covered thing stops
 * existing.
 *
 * <p>Flattened rather than sent as rectangles beside the picture: a cover that travels as
 * coordinates is a cover somebody can take off again, and what leaves here has no layer to peel.
 */
export async function flatten(source: HTMLCanvasElement, covers: readonly Cover[]): Promise<string | null> {
    const canvas = document.createElement('canvas')
    canvas.width = source.width
    canvas.height = source.height
    const context = canvas.getContext('2d')
    if (!context) return null
    context.drawImage(source, 0, 0)
    context.fillStyle = '#111827'
    for (const cover of covers) {
        context.fillRect(cover.x, cover.y, cover.width, cover.height)
    }
    for (const quality of [0.85, 0.7, 0.5]) {
        const written = await write(canvas, quality)
        if (written && written.length <= MAX_BYTES) return written
    }
    return null
}

function write(canvas: HTMLCanvasElement, quality: number): Promise<string | null> {
    return new Promise(resolve => {
        canvas.toBlob(
            blob => {
                if (!blob) {
                    resolve(null)
                    return
                }
                const reader = new FileReader()
                reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : null)
                reader.onerror = () => resolve(null)
                reader.readAsDataURL(blob)
            },
            'image/webp',
            quality,
        )
    })
}

/**
 * The rectangles a person drags over a picture, and the ones drawn for them.
 *
 * <p>Kept here rather than in the dialog because the same covering is done twice: once by whoever
 * writes the report, and once by whoever decides whether it may leave the instance.
 */
export function useCovers(): {
    covers: Ref<Cover[]>
    add: (cover: Cover) => void
    removeAt: (index: number) => void
    clear: () => void
} {
    const covers = ref<Cover[]>([])

    /** Anything smaller than this is a misfired click rather than a rectangle somebody meant. */
    const SMALLEST = 4

    function add(cover: Cover) {
        if (cover.width < SMALLEST || cover.height < SMALLEST) return
        covers.value = [...covers.value, cover]
    }

    function removeAt(index: number) {
        covers.value = covers.value.filter((_, at) => at !== index)
    }

    function clear() {
        covers.value = []
    }

    return {covers, add, removeAt, clear}
}
