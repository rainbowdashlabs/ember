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
 * Where the picture of sample text in a family is drawn: the family, or null for the default font, the
 * style, and the version of the sample the list named, which keeps a browser from showing an old one.
 */
export type FontSampleAddress = (family: string | null, style: FontStyle, version: string) => string

/**
 * The fonts of one owner: a station, an association or the instance. Each lists its own fonts with
 * every family its templates reach, uploads one style of a family and deletes a font. The files
 * themselves never come back; there is nothing to download, only a picture of sample text the server
 * draws in a family the owner reaches.
 */
export interface FontSource {
    list(): Promise<DocumentFontsResponse>
    upload(upload: FontUpload): Promise<DocumentFontsResponse>
    remove(id: number): Promise<DocumentFontsResponse>
    sample: FontSampleAddress
}

function sourceAt(path: string): FontSource {
    return {
        sample(family, style, version) {
            const query = new URLSearchParams({style, v: version})
            if (family) query.set('family', family)
            return `${path}/sample?${query.toString()}`
        },
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
