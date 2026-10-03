/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {isRemoteEntry} from '@/api/discovery'
import type {DiscoveryEntry} from '@/api/generated/schema'
import type {DiscoveryViewer} from '@/composables/useDiscoveryViewer'

/** One public part of a station a tile links to. */
export interface OfferLink {
    key: 'waitingList' | 'wiki' | 'calendar' | 'blog'
    icon: string[]
    href: string
}

/**
 * Whether the tile shows the viewer's own station. The list a public page loads without a session cannot
 * say so itself, so the viewer's station is compared as well; a station of another instance is never it.
 */
export function isViewersStation(entry: DiscoveryEntry, viewer: DiscoveryViewer): boolean {
    return entry.isOwnStation || (!isRemoteEntry(entry) && entry.stationUid === viewer.stationUid)
}

function openToFederation(entry: DiscoveryEntry, viewer: DiscoveryViewer): boolean {
    return entry.acceptsFederation && !entry.alreadyFederated && !isViewersStation(entry, viewer)
}

/**
 * The request to federate: for a viewer allowed to ask, never on their own station, on a station of this
 * instance and of another instance alike.
 */
export function showsConnect(entry: DiscoveryEntry, viewer: DiscoveryViewer): boolean {
    return viewer.mayRequestFederation && openToFederation(entry, viewer)
}

/**
 * The invite code, for a station of this instance and of another instance alike. The code of a station of
 * another instance names that instance's address, and entering it at a station sends that instance a
 * request to federate.
 */
export function showsInvite(entry: DiscoveryEntry, viewer: DiscoveryViewer): boolean {
    return viewer.offersInvite && openToFederation(entry, viewer)
}

/**
 * The public parts the station offers, each as the address of that part of its public page, the open
 * waiting list first. Without a public page there is nothing to link to.
 */
export function offerLinks(entry: DiscoveryEntry): OfferLink[] {
    const page = entry.publicPageUrl
    if (!page) return []
    const offers: [boolean, OfferLink['key'], string[], string][] = [
        [entry.waitingListOpen, 'waitingList', ['fas', 'user-plus'], 'waitlist'],
        [entry.hasPublicWiki, 'wiki', ['fas', 'book-open'], 'knowledge'],
        [entry.hasPublicCalendar, 'calendar', ['fas', 'calendar-days'], 'calendar'],
        [entry.hasPublicBlog, 'blog', ['fas', 'newspaper'], 'blog'],
    ]
    return offers
        .filter(([offered]) => offered)
        .map(([, key, icon, part]) => ({key, icon, href: `${page}/${part}`}))
}

/**
 * A key that stays unique across instances: two instances may well publish a station under the same
 * identifier, and neither of them is this instance's own.
 */
export function discoveryKey(entry: DiscoveryEntry): string {
    return `${entry.instanceHost ?? ''}/${entry.stationUid}`
}

/** Whether the station has a place on the map. */
export function isOnTheMap(entry: DiscoveryEntry): boolean {
    return typeof entry.latitude === 'number' && typeof entry.longitude === 'number'
}

/** The station's address as one line, from the parts it published. */
export function placeLine(entry: DiscoveryEntry): string {
    return [entry.addressLine, entry.city, entry.country].filter(Boolean).join(', ')
}
