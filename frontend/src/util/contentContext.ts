/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {mediaFileUrl, mediaImageUrlAt} from '@/api/media'

/**
 * How wide a page of content is, which is as wide as anything drawn in it can be.
 *
 * <p>The reading column both a public page and a station page sit in, in CSS pixels. Whoever
 * changes that column changes this.
 */
export const PAGE_WIDTH = 1024

/**
 * How wide a picture is asked for when a reader opens it large: the largest copy kept of any
 * picture, so a full screen shows it sharp without fetching the photograph as it was taken.
 */
export const ENLARGED_WIDTH = 2048

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
    /**
     * The widest an image gets on this surface, which is what a variant is chosen for.
     *
     * <p>The width a reader actually sees, not the width a screen could hold. A hint of twice the
     * surface is not a sharper picture, it is the same picture at four times the bytes, and on a
     * phone the bytes are the whole cost. The browser doubles it again for a dense screen, which is
     * where the sharpness comes from.
     */
    widthHint: number
    /** Used as the accessible name of an embedded player. */
    title: string
    /**
     * The clock a date in a block is written on, or nothing to write it on the reader's own.
     *
     * <p>A page rendered on a server and then again in a browser is written by two machines, neither
     * of which stands where the reader does, so a date put on whichever clock wrote it comes out
     * differently in the two copies. A surface with no reader to ask names the station's clock here;
     * a surface inside the station leaves it, because there the reader's clock is the right one.
     */
    timezone?: string | null
}

/**
 * A public page or a public blog entry: no session, so files are addressed by hash on the public
 * route and a reader anywhere can load them.
 *
 * @param timezone the station's clock, which every date on such a page is written on
 */
export function publicContentContext(
    stationUid: string,
    title = '',
    timezone: string | null = null,
): ContentRenderContext {
    return {
        isPublic: true,
        stationUid,
        fileUrl: hash => mediaFileUrl(stationUid, hash),
        imageUrl: (hash, width) => mediaImageUrlAt(stationUid, hash, width),
        widthHint: PAGE_WIDTH,
        title,
        timezone,
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
        widthHint: PAGE_WIDTH,
        title,
    }
}
