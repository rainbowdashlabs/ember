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

/**
 * Takes one picture of whatever the person chooses to share, and stops sharing at once.
 *
 * <p>The browser asks them which window or tab, and that prompt is what makes this honest: the
 * browser says what is being shared, not this code. The track is stopped the moment a frame is
 * held, so nothing goes on watching after the one picture.
 *
 * @return the picture, or null where they refused the prompt or the browser cannot do it
 */
export async function captureScreen(): Promise<HTMLCanvasElement | null> {
    if (!canCaptureScreen()) return null
    let stream: MediaStream | null = null
    try {
        stream = await navigator.mediaDevices.getDisplayMedia({video: true, audio: false})
        const track = stream.getVideoTracks()[0]
        if (!track) return null
        const video = document.createElement('video')
        video.srcObject = stream
        video.muted = true
        await video.play()
        await new Promise(resolve => requestAnimationFrame(resolve))
        const canvas = document.createElement('canvas')
        canvas.width = video.videoWidth
        canvas.height = video.videoHeight
        canvas.getContext('2d')?.drawImage(video, 0, 0)
        video.pause()
        video.srcObject = null
        return canvas.width > 0 && canvas.height > 0 ? canvas : null
    } catch {
        return null
    } finally {
        stream?.getTracks().forEach(track => track.stop())
    }
}

/** Reads a picture somebody attached themselves, which is the way where sharing is refused. */
export async function readPictureFile(file: File): Promise<HTMLCanvasElement | null> {
    if (!file.type.startsWith('image/')) return null
    const url = URL.createObjectURL(file)
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
