/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mediaFileUrl, mediaImageUrlAt} from '@/api/media'

/**
 * Where a piece of block content is being read, which is everything the renderer needs to know
 * about its surroundings.
 *
 * <p>The same blocks appear on a public page, inside the station, and in the editor's preview. What
 * differs between those is only how a file is addressed and how wide the surface is, so the
 * renderer takes those as a value rather than growing a branch per caller. That is what keeps one
 * renderer instead of one per surface, and what makes the editor preview show what the reader sees
 * by construction rather than because two people remembered to change two files.
 */
export interface ContentRenderContext {
    /** Whether the content is read without a session, which decides how a file is addressed. */
    isPublic: boolean
    stationUid: string
    /** The address of a media file from here, given its content hash. */
    fileUrl: (contentHash: string) => string
    /** The address of a media image at a given width, for picking a pre-generated variant. */
    imageUrl: (contentHash: string, width: number) => string
    /** The widest an image gets on this surface, which is what a variant is chosen for. */
    widthHint: number
    /** Used as the accessible name of an embedded player. */
    title: string
}

/**
 * A public page or a public blog entry: no session, so files are addressed by hash on the public
 * route and a reader anywhere can load them.
 */
export function publicContentContext(stationUid: string, title = ''): ContentRenderContext {
    return {
        isPublic: true,
        stationUid,
        fileUrl: hash => mediaFileUrl(stationUid, hash),
        imageUrl: (hash, width) => mediaImageUrlAt(stationUid, hash, width),
        widthHint: 2048,
        title,
    }
}

/**
 * Content read inside the station. Files still come from the public route here, because an image
 * is only as private as its hash either way and the authenticated route exists for the content
 * that needs more than that.
 */
export function internalContentContext(stationUid: string, title = ''): ContentRenderContext {
    return {
        isPublic: false,
        stationUid,
        fileUrl: hash => mediaFileUrl(stationUid, hash),
        imageUrl: (hash, width) => mediaImageUrlAt(stationUid, hash, width),
        widthHint: 1024,
        title,
    }
}
