/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import {uploadFile} from './upload'
import type {DocumentFontsResponse, FontStyle} from '@/api/generated/schema'

/** One style of a font family as the upload form sends it. */
export interface FontUpload {
    file: File
    family: string
    style: FontStyle
    /** The uploader's word that the owner may use the font for its documents. */
    confirmed: boolean
}

/**
 * The fonts of one owner: a station, an association or the instance. Each lists its own fonts with
 * every family its templates reach, uploads one style of a family and deletes a font. The files
 * themselves never come back; there is nothing to download.
 */
export interface FontSource {
    list(): Promise<DocumentFontsResponse>
    upload(upload: FontUpload): Promise<DocumentFontsResponse>
    remove(id: number): Promise<DocumentFontsResponse>
}

function sourceAt(path: string): FontSource {
    return {
        async list() {
            const res = await client.get<DocumentFontsResponse>(path)
            return res.data
        },
        upload(upload) {
            return uploadFile<DocumentFontsResponse>(path, {
                file: upload.file,
                family: upload.family,
                style: upload.style,
                confirmed: String(upload.confirmed),
            })
        },
        async remove(id) {
            const res = await client.delete<DocumentFontsResponse>(`${path}/${id}`)
            return res.data
        },
    }
}

/** The fonts of the station the reader works for. */
export const stationFontSource: FontSource = sourceAt('/document-fonts')

/** The fonts of the association the reader acts for, which its stations reach too. */
export const associationFontSource: FontSource = sourceAt('/cluster/document-fonts')

/** The fonts of the instance, which every station reaches. */
export const instanceFontSource: FontSource = sourceAt('/admin/document-fonts')
