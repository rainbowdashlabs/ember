/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {isEmbeddedBrowser, isGeckoOnAndroid, isHandheld} from '@/util/handheld'

/**
 * How long a saved blob's object URL outlives the click that saves it.
 *
 * Safari on iOS reads the URL only after the click has returned, so a URL revoked at once leaves it
 * nothing to save and the button silently does nothing.
 */
export const SAVED_BLOB_LIFETIME_MS = 40_000

/**
 * The type a file is handed to the download link as, whatever it actually is.
 *
 * <p>A browser that recognises the type may open the file rather than save it, and one with no
 * viewer for it opens nothing and leaves the window it navigated on an empty address. In an
 * installed app that window is the whole app, so a reader saving a document lost the page they were
 * on and got no document. Bytes nobody claims to be able to draw are saved by everybody.
 */
export const SAVED_BLOB_TYPE = 'application/octet-stream'

/** What a document with pages is, which is the one kind a browser here insists on opening itself. */
const PDF_TYPE = 'application/pdf'

/** Where the service worker answers a saved file, matching the path in {@code public/sw.js}. */
const WORKER_SAVE_PATH = '/save-file/'

/** How long the worker has to say it holds the bytes before the blob address is used instead. */
const WORKER_REPLY_TIMEOUT_MS = 2_000

/** How far a save got, which is the only way a caller can know the reader was left with nothing. */
export const SaveResult = {
    /** Handed to the system, or the browser's own saving began. */
    DONE: 'DONE',
    /** The reader closed the share sheet without choosing, which is an answer and not a failure. */
    DISMISSED: 'DISMISSED',
    /** Neither route works in this browser, so the reader has nothing and has not been told. */
    UNAVAILABLE: 'UNAVAILABLE',
} as const

export type SaveResultName = (typeof SaveResult)[keyof typeof SaveResult]

/**
 * Hands an already-materialised blob to the reader.
 *
 * <p>On a phone or a tablet the file goes to the system share sheet, from where it can be saved,
 * opened in another app or sent on. A download link there does nothing inside the browsers apps
 * embed, such as the Google app's, and on Android it can leave a blank tab instead of a file,
 * because a browser that will not take bytes from the page navigates to them and finds nothing it
 * can draw.
 *
 * <p>A reader who closes the sheet has chosen, and nothing more happens. A sheet that refuses the
 * file has not been chosen against, most often because the press that would have opened it has since
 * expired, so the download link is tried instead: the answer at a desk, in a phone's own browser and
 * in an installed app, and no answer only where a browser is embedded in another app. Saying which
 * of the three happened is this function's job, because the alternative is the button that does
 * nothing and explains nothing.
 */
export async function saveBlob(blob: Blob, filename: string): Promise<SaveResultName> {
    const file = new File([blob], filename, {type: blob.type})
    if (opensItselfAnyway(blob, filename)) return openInBrowsersViewer(blob, filename)
    if (!sharesFilesInstead(file)) return downloadInstead(blob, filename)
    try {
        await navigator.share({files: [file]})
        return SaveResult.DONE
    } catch (error) {
        return isDismissal(error) ? SaveResult.DISMISSED : downloadInstead(blob, filename)
    }
}

/**
 * The download, which answers everywhere a share sheet is not the way files are handled.
 *
 * <p>Through the service worker where there is one, because bytes fetched from an address of their
 * own are saved by every browser, and bytes handed over as a blob address are saved only by the ones
 * that guess kindly. The blob address remains for a page no worker has taken charge of yet.
 */
async function downloadInstead(blob: Blob, filename: string): Promise<SaveResultName> {
    const address = await addressOf(blob, filename, SAVED_BLOB_TYPE, 'attachment')
    if (address) clickLink(address, filename)
    else clickDownloadLink(blob, filename)
    return isEmbeddedBrowser() ? SaveResult.UNAVAILABLE : SaveResult.DONE
}

/**
 * Opens the document in the browser's own viewer, for the browser that was going to do that anyway.
 *
 * <p>Firefox on Android renders a PDF itself, and decides that a file is one by the ending of its
 * name whatever it has been told the bytes are. Asked to save one it therefore opens it, in a window
 * beside the one that asked, and an installed app has nowhere to put such a window: the reader loses
 * the page they were on and never sees the document. Opened on purpose, the same viewer arrives with
 * its own saving in it, which is the journey the browser's own window has always taken.
 *
 * <p>A window the browser refuses to open leaves the download, which is no worse than before.
 */
async function openInBrowsersViewer(blob: Blob, filename: string): Promise<SaveResultName> {
    const address = await addressOf(blob, filename, blob.type, 'inline')
    if (address && window.open(address, '_blank')) return SaveResult.DONE
    return downloadInstead(blob, filename)
}

/**
 * An address of its own for the file, answered by the service worker, or nothing where there is no
 * worker to answer it.
 *
 * <p>Bytes fetched from an address are handled by every browser the same way; bytes handed over as a
 * blob address are handled only by the ones that guess kindly. Waits to be told the worker is
 * holding them, because a worker is started on being spoken to and a fetch sent before it has heard
 * finds nothing there.
 */
async function addressOf(
    blob: Blob,
    filename: string,
    type: string,
    disposition: 'attachment' | 'inline',
): Promise<string | null> {
    const worker = await controllingWorker()
    if (!worker) return null
    const id = crypto.randomUUID()
    const channel = new MessageChannel()
    const held = new Promise<boolean>(resolve => {
        channel.port1.onmessage = () => resolve(true)
        setTimeout(() => resolve(false), WORKER_REPLY_TIMEOUT_MS)
    })
    worker.postMessage({type: 'save-file', id, blob, filename, contentType: type, disposition}, [channel.port2])
    return await held ? `${WORKER_SAVE_PATH}${id}` : null
}

/**
 * Whether this browser is going to open the document itself rather than save it.
 *
 * <p>Read off the name as well as the type, because that is how Firefox reads it: a name ending in
 * {@code .pdf} is a PDF to it even where the bytes were handed over as something nobody can draw.
 */
function opensItselfAnyway(blob: Blob, filename: string): boolean {
    if (!isGeckoOnAndroid()) return false
    return blob.type === PDF_TYPE || filename.toLowerCase().endsWith('.pdf')
}

/**
 * The worker in charge of this page, waited for where one is registered but has not taken over yet.
 *
 * <p>A worker is registered once the page has loaded and takes charge a moment later, and a page
 * that has just been opened is therefore briefly answered by nobody. Saving during that moment is
 * the first thing a reader does after updating, which is precisely when the blob address is still
 * the one that loses their page.
 */
async function controllingWorker(): Promise<ServiceWorker | null> {
    const workers = navigator.serviceWorker
    if (!workers) return null
    if (workers.controller) return workers.controller
    await Promise.race([workers.ready, new Promise(resolve => setTimeout(resolve, WORKER_REPLY_TIMEOUT_MS))])
    return workers.controller
}

/** Whether this device wants the share sheet and its browser will put this file on it. */
function sharesFilesInstead(file: File): boolean {
    return isHandheld() && typeof navigator.canShare === 'function' && navigator.canShare({files: [file]})
}

/**
 * Whether the reader closed the share sheet rather than the sheet failing to open.
 *
 * <p>Read off the name alone. A rejection does not always arrive as a {@code DOMException} this realm
 * recognises, and one that did not was taken for a failure: the file was then saved a second time
 * behind a reader who had just declined to save it once.
 */
function isDismissal(error: unknown): boolean {
    return nameOf(error) === 'AbortError'
}

function nameOf(error: unknown): string {
    if (typeof error !== 'object' || error === null || !('name' in error)) return ''
    return String((error as {name: unknown}).name)
}

/**
 * Saves a blob through a temporary object URL, revoked once the browser has had
 * {@link SAVED_BLOB_LIFETIME_MS} to read it, and handed over as {@link SAVED_BLOB_TYPE} so that it
 * is saved rather than opened.
 */
function clickDownloadLink(blob: Blob, filename: string): void {
    const blobUrl = URL.createObjectURL(new Blob([blob], {type: SAVED_BLOB_TYPE}))
    try {
        clickLink(blobUrl, filename)
    } finally {
        setTimeout(() => URL.revokeObjectURL(blobUrl), SAVED_BLOB_LIFETIME_MS)
    }
}

/** Presses a link to an address under the name the file is to be saved as. */
function clickLink(href: string, filename: string): void {
    const anchor = document.createElement('a')
    anchor.href = href
    anchor.download = filename
    anchor.rel = 'noopener'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
}
