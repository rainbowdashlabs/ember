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

/**
 * Hands an already-materialised blob to the reader.
 *
 * On a phone or a tablet the file goes to the system share sheet, from where it can be saved, opened
 * in another app or sent on. A download link there does nothing inside the browsers apps embed, such
 * as the Google app's, and on Android it can leave a blank tab instead of a file, because a browser
 * that will not take bytes from the page navigates to them and finds nothing it can draw. Where the
 * share sheet refuses the file, the download link is the fallback; a reader closing the sheet has
 * chosen, and nothing more happens.
 */
export function saveBlob(blob: Blob, filename: string): void {
    const file = new File([blob], filename, {type: blob.type})
    if (!sharesFilesInstead(file)) {
        clickDownloadLink(blob, filename)
        return
    }
    navigator.share({files: [file]}).catch((error: unknown) => {
        if (!isDismissal(error)) clickDownloadLink(blob, filename)
    })
}

/** Whether this device wants the share sheet and its browser will put this file on it. */
function sharesFilesInstead(file: File): boolean {
    return isHandheld() && typeof navigator.canShare === 'function' && navigator.canShare({files: [file]})
}

function isDismissal(error: unknown): boolean {
    return error instanceof DOMException && error.name === 'AbortError'
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
