/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Thrown when the browser cannot read the picked file as a picture at all.
 *
 * A phone camera hands over whatever format the phone keeps its photos in, and on some devices
 * that is a format only the phone itself understands. Saying so before anything is sent is the
 * whole point: the alternative is a long upload that the server refuses at the end of it.
 */
export class UnreadableImageError extends Error {
    constructor() {
        super('The picked file could not be read as an image')
        this.name = 'UnreadableImageError'
    }
}

/** How small a picture has to become before it is sent. */
export interface ImageBudget {
    /** The longest edge the picture may keep, in pixels. */
    maxEdge: number
    /** The size the encoded picture has to fit into, in bytes. */
    maxBytes: number
}

/**
 * What the endpoints that take a picture allow. The server refuses anything above five megabytes
 * and keeps only PNG, JPEG and WebP, so nothing larger and nothing else ever leaves the browser.
 * The edge is generous enough that a photo stays readable on a desktop screen and small enough
 * that it fits the budget on the first try in almost every case.
 */
export const DEFAULT_IMAGE_BUDGET: ImageBudget = {maxEdge: 2048, maxBytes: 4 * 1024 * 1024}

/** The qualities tried in turn, from the best that might fit down to the last one worth sending. */
const LOWEST_QUALITY = 0.4
const QUALITIES = [0.9, 0.8, 0.7, 0.55, LOWEST_QUALITY]

/**
 * The size a picture is drawn at so its longest edge fits the budget, keeping its proportions.
 * A picture already inside the budget keeps the size it has.
 */
export function scaledSize(width: number, height: number, maxEdge: number): {width: number; height: number} {
    const longest = Math.max(width, height)
    if (longest <= maxEdge || longest === 0) return {width, height}
    const factor = maxEdge / longest
    return {
        width: Math.max(1, Math.round(width * factor)),
        height: Math.max(1, Math.round(height * factor)),
    }
}

/**
 * Redraws a picked picture as a JPEG small enough to send.
 *
 * Every endpoint that takes a picture takes it in one of three formats and under one size, and a
 * phone offers neither: today's cameras write ten megabytes in a format the browser is the only
 * thing on the device that can read. Drawing the picture onto a canvas and encoding it again
 * settles both questions at once, whatever came in, and it happens on the device rather than
 * costing an upload that ends in a refusal.
 *
 * @param file   the file the person picked
 * @param budget how small the result has to be, defaulting to what the endpoints allow
 * @throws UnreadableImageError when this browser cannot decode the file
 */
export async function prepareImageUpload(file: File, budget: ImageBudget = DEFAULT_IMAGE_BUDGET): Promise<File> {
    await painted()
    const source = await decode(file)
    try {
        const {width, height} = scaledSize(source.width, source.height, budget.maxEdge)
        const canvas = surfaceOf(width, height)
        const context = canvas.getContext('2d') as CanvasRenderingContext2D | null
        if (!context) throw new UnreadableImageError()
        context.drawImage(source.image as CanvasImageSource, 0, 0, width, height)

        for (const quality of QUALITIES) {
            const blob = await encode(canvas, quality)
            if (blob.size <= budget.maxBytes) return named(blob, file.name)
        }
        const half = await halved(canvas, budget)
        if (half) return named(half, file.name)
        throw new UnreadableImageError()
    } finally {
        source.release()
    }
}

/**
 * Lets the screen catch up before the work starts.
 *
 * <p>Whatever said it was busy said so in the same breath as calling this, and a frame has to be
 * drawn for anybody to see it. Decoding and encoding a photograph from a modern camera holds the
 * thread long enough that the spinner would otherwise appear only once the work was already over,
 * which is what left people pressing the button again and wondering whether anything had happened.
 */
function painted(): Promise<void> {
    if (typeof requestAnimationFrame !== 'function') return Promise.resolve()
    return new Promise(resolve => requestAnimationFrame(() => setTimeout(resolve, 0)))
}

/** What a picture is drawn on: off the screen thread where the browser has such a thing. */
type Surface = HTMLCanvasElement | OffscreenCanvas

/**
 * A surface to redraw the picture on.
 *
 * <p>An {@link OffscreenCanvas} is asked for first, because the encoding that follows is where the
 * time goes and that one can hand it to another thread. Everything else keeps the canvas element,
 * which does the same work in front of the person waiting.
 */
function surfaceOf(width: number, height: number): Surface {
    if (typeof OffscreenCanvas === 'function') return new OffscreenCanvas(width, height)
    const canvas = document.createElement('canvas')
    canvas.width = width
    canvas.height = height
    return canvas
}

/** A decoded picture together with whatever has to be let go of afterwards. */
interface DecodedImage {
    image: ImageBitmap | HTMLImageElement
    width: number
    height: number
    release: () => void
}

/**
 * Decodes the file, preferring the bitmap decoder and falling back to an image element for the
 * browsers that do not have it. Both are asked, because an image element reads a few formats the
 * bitmap decoder refuses. Either way a format the device cannot read at all ends as a refusal.
 */
async function decode(file: File): Promise<DecodedImage> {
    if (typeof createImageBitmap === 'function') {
        try {
            const bitmap = await createImageBitmap(file, {imageOrientation: 'from-image'})
            return {image: bitmap, width: bitmap.width, height: bitmap.height, release: () => bitmap.close()}
        } catch {
            void 0
        }
    }
    const url = URL.createObjectURL(file)
    try {
        const element = await loadElement(url)
        return {
            image: element,
            width: element.naturalWidth,
            height: element.naturalHeight,
            release: () => URL.revokeObjectURL(url),
        }
    } catch (e) {
        URL.revokeObjectURL(url)
        throw e
    }
}

function loadElement(url: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
        const element = new Image()
        element.onload = () => resolve(element)
        element.onerror = () => reject(new UnreadableImageError())
        element.src = url
    })
}

/**
 * Writes the surface out as a JPEG.
 *
 * <p>The offscreen way of asking is a promise the browser may answer from another thread, which is
 * the whole reason for preferring that surface: encoding a picture of this size is several hundred
 * milliseconds of arithmetic, and on the screen's own thread that is a page that does not respond.
 */
function encode(canvas: Surface, quality: number): Promise<Blob> {
    if ('convertToBlob' in canvas) {
        return canvas.convertToBlob({type: 'image/jpeg', quality}).catch(() => {
            throw new UnreadableImageError()
        })
    }
    return new Promise((resolve, reject) => {
        canvas.toBlob(blob => (blob ? resolve(blob) : reject(new UnreadableImageError())), 'image/jpeg', quality)
    })
}

/**
 * The last resort for a picture that will not fit even at the lowest quality: draw it again at
 * half the edge and try once more.
 */
async function halved(canvas: Surface, budget: ImageBudget): Promise<Blob | null> {
    const smaller = surfaceOf(Math.max(1, Math.round(canvas.width / 2)), Math.max(1, Math.round(canvas.height / 2)))
    const context = smaller.getContext('2d') as CanvasRenderingContext2D | null
    if (!context) return null
    context.drawImage(canvas as CanvasImageSource, 0, 0, smaller.width, smaller.height)
    const blob = await encode(smaller, LOWEST_QUALITY)
    return blob.size <= budget.maxBytes ? blob : null
}

function named(blob: Blob, originalName: string): File {
    const base = originalName.replace(/\.[^.]+$/, '') || 'image'
    return new File([blob], `${base}.jpg`, {type: 'image/jpeg'})
}
