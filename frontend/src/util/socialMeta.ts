/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** What a page says about itself to a search engine and to whatever draws a preview of its link. */
export interface SocialMeta {
    /** The page's own headline. The site name is added behind it here. */
    title: string
    description: string
    /** The page's own picture, or the station's logo where the page has none. */
    imageUrl?: string | null
    /** The OpenGraph type, `website` unless the page is something more specific such as an article. */
    type?: string
}

/**
 * One tag, named the standard way or the OpenGraph way.
 *
 * <p>Written as one shape or the other rather than as both optional, because the head accepts a
 * choice between several kinds of tag and cannot tell which one is meant by a shape where every key
 * might be missing: it settles on the first kind, which declares the document's character set, and
 * refuses everything here for not being one.
 */
type HeadMetaTag =
    | {name: string, content: string}
    | {property: string, content: string}

const SITE_NAME = 'Ember'

/**
 * The description, OpenGraph and Twitter tags of one page.
 *
 * <p>Written once so every public page states them the same way: the site name behind the title, a
 * large card where there is a picture and a plain one where there is none, and no image tags at all
 * rather than empty ones. A page that needs more than this (a robots rule, a referrer policy) puts
 * its own entries in front of these.
 */
export function socialMeta({title, description, imageUrl, type = 'website'}: SocialMeta): HeadMetaTag[] {
    const titled = `${title} - ${SITE_NAME}`
    return [
        {name: 'description', content: description},
        {property: 'og:title', content: titled},
        {property: 'og:description', content: description},
        {property: 'og:type', content: type},
        ...(imageUrl ? [
            {property: 'og:image', content: imageUrl},
            {name: 'twitter:image', content: imageUrl},
        ] : []),
        {name: 'twitter:card', content: imageUrl ? 'summary_large_image' : 'summary'},
        {name: 'twitter:title', content: titled},
        {name: 'twitter:description', content: description},
    ]
}

/** The station's logo at preview size, which is what a page with no picture of its own falls back to. */
export function stationLogoImage(station: {stationUid: string, hasLogo: boolean} | null | undefined): string | undefined {
    if (!station?.hasLogo) return undefined
    return `/api/v1/public/stations/${station.stationUid}/logo?size=512`
}

/**
 * The origin this installation is being read at.
 *
 * <p>The request carries it, which is the only source that is right on an installation whose
 * operator has configured nothing and whose address nobody wrote down. Where one is configured it
 * wins over the browser's own idea, and the browser answers when there is no request to ask, which
 * is every navigation after the first.
 */
function siteOrigin(): string {
    const requested = requestedOrigin()
    if (requested) return requested
    const configured = (import.meta.env.NUXT_PUBLIC_SITE_URL as string) || ''
    if (configured) return configured.replace(/\/+$/, '')
    return typeof window === 'undefined' ? '' : window.location.origin
}

/**
 * The origin of the request being answered, where there is one to ask.
 *
 * <p>Asking needs the Nuxt instance, which is there while a component sets up and gone by the time
 * a head computed from it is resolved, so it answers with nothing rather than throwing there.
 */
function requestedOrigin(): string {
    try {
        return useRequestURL().origin
    } catch {
        return ''
    }
}

/**
 * Turns a path on this installation into the whole address, resolved while the component sets up.
 *
 * <p>A preview card and a canonical both have to name an address that can be fetched from outside,
 * and `/api/v1/...` names nothing to a chat client unfurling a link somewhere else entirely. The
 * origin is looked up once, here, because the head this feeds is computed later and lazily, where
 * the request behind it can no longer be reached.
 */
export function useAbsoluteUrl(): (path: string | null | undefined) => string | undefined {
    const origin = siteOrigin()
    return path => (path ? `${origin}${path}` : undefined)
}

/** A page's title as a tab reads it: what the page is, then whose it is. */
export function titleWithStation(title: string, stationName: string | null | undefined): string {
    return stationName ? `${title} - ${stationName}` : title
}

/**
 * The opening of a rendered body as plain text, for a description or a teaser.
 *
 * @param html      the body as it is rendered
 * @param maxLength how much of it to keep before the ellipsis
 */
export function plainTextExcerpt(html: string, maxLength = 200): string {
    const text = html.replace(/<[^>]*>/g, '').replace(/\s+/g, ' ').trim()
    return text.length > maxLength ? `${text.substring(0, maxLength)}…` : text
}
