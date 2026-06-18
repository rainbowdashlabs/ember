/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from '@/api/client'

/**
 * Downloads a resource from an authenticated API endpoint and saves it to
 * disk under the given filename.
 *
 * The request is sent through the shared axios client so the
 * {@code Authorization} header and {@code X-Station-Id} header are applied
 * automatically. The response body is materialised as a {@link Blob} and
 * exposed to the browser via a temporary object URL that is revoked
 * immediately after the click is dispatched.
 *
 * @param url      relative API path (e.g. {@code /kb/files/42/original}).
 * @param filename name suggested to the browser's save dialog.
 */
export async function downloadAuthed(url: string, filename: string): Promise<void> {
    const res = await client.get(url, {responseType: 'blob'})
    const blobUrl = URL.createObjectURL(res.data as Blob)
    try {
        const anchor = document.createElement('a')
        anchor.href = blobUrl
        anchor.download = filename
        anchor.rel = 'noopener'
        document.body.appendChild(anchor)
        anchor.click()
        anchor.remove()
    } finally {
        URL.revokeObjectURL(blobUrl)
    }
}
