/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {isHandheld} from '@/util/handheld'

/**
 * How long a saved blob's object URL outlives the click that saves it.
 *
 * Safari on iOS reads the URL only after the click has returned, so a URL revoked at once leaves it
 * nothing to save and the button silently does nothing.
 */
export const SAVED_BLOB_LIFETIME_MS = 40_000

/** How far a save got, which is the only way a caller can know the reader was left with nothing. */
export const SaveResult = {
    /** Handed to the system, or the browser's own saving began. */
    DONE: 'DONE',
    /** The reader closed the share sheet without choosing, which is an answer and not a failure. */
    DISMISSED: 'DISMISSED',
    /** Neither route works on this device, so the reader has nothing and has not been told. */
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
 * expired, so the download link is tried instead: the whole answer at a desk, and no answer at all in
 * the hand. Saying which of the three happened is this function's job, because the alternative is the
 * button that does nothing and explains nothing.
 */
export async function saveBlob(blob: Blob, filename: string): Promise<SaveResultName> {
    const file = new File([blob], filename, {type: blob.type})
    if (!sharesFilesInstead(file)) return downloadInstead(blob, filename)
    try {
        await navigator.share({files: [file]})
        return SaveResult.DONE
    } catch (error) {
        return isDismissal(error) ? SaveResult.DISMISSED : downloadInstead(blob, filename)
    }
}

/** The download link, which answers everywhere a share sheet is not the way files are handled. */
function downloadInstead(blob: Blob, filename: string): SaveResultName {
    clickDownloadLink(blob, filename)
    return isHandheld() ? SaveResult.UNAVAILABLE : SaveResult.DONE
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
 * {@link SAVED_BLOB_LIFETIME_MS} to read it.
 */
function clickDownloadLink(blob: Blob, filename: string): void {
    const blobUrl = URL.createObjectURL(blob)
    try {
        const anchor = document.createElement('a')
        anchor.href = blobUrl
        anchor.download = filename
        anchor.rel = 'noopener'
        document.body.appendChild(anchor)
        anchor.click()
        anchor.remove()
    } finally {
        setTimeout(() => URL.revokeObjectURL(blobUrl), SAVED_BLOB_LIFETIME_MS)
    }
}
