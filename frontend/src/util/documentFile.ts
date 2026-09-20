/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {AxiosResponse} from 'axios'
import {parseContentDispositionFilename} from '@/util/downloadAuthed'

/** A finished document and the name it should reach the reader under. */
export interface DocumentFile {
    blob: Blob
    filename: string
}

/**
 * Reads a document out of the response that carries it.
 *
 * <p>The name comes from the server, because the server is what built the document and what knows
 * which language the station reads. A name invented here would be the interface's language rather
 * than the document's, and those are not always the same.
 *
 * @param response the response, fetched as a blob
 * @param fallback the name to use while an endpoint still sends none
 */
export function documentFrom(response: AxiosResponse, fallback: string): DocumentFile {
    const header = response.headers['content-disposition'] as string | undefined
    return {
        blob: response.data as Blob,
        filename: parseContentDispositionFilename(header) ?? fallback,
    }
}

/** A document built here rather than fetched, which names itself. */
export function documentOf(blob: Blob, filename: string): DocumentFile {
    return {blob, filename}
}
