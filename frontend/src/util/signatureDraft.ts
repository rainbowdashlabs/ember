/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {SignatureImageSource} from '@/api/generated/schema'

/**
 * A signature picture made on the screen and not sent yet: a PNG as a data address, and how it was made.
 * The server cleans it before it keeps or draws it, so this is the picture as the browser drew it.
 */
export interface SignatureDraft {
    dataUrl: string
    source: SignatureImageSource
}

/**
 * @param draft the picture
 * @returns its PNG bytes in Base64, without the data address in front
 */
export function draftBase64(draft: SignatureDraft): string {
    return draft.dataUrl.slice(draft.dataUrl.indexOf(',') + 1)
}

/**
 * @param draft the picture
 * @returns its PNG bytes, for an upload
 */
export function draftBlob(draft: SignatureDraft): Blob {
    const binary = atob(draftBase64(draft))
    const bytes = new Uint8Array(binary.length)
    for (let index = 0; index < binary.length; index++) {
        bytes[index] = binary.charCodeAt(index)
    }
    return new Blob([bytes], {type: 'image/png'})
}
