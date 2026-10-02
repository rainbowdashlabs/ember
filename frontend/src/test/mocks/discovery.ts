/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {DiscoveryEntry} from '@/api/generated/schema'
import type {DiscoveryViewer} from '@/composables/useDiscoveryViewer'

/** A station of this instance on the discovery page that offers nothing, for a test to fill in. */
export function createDiscoveryEntry(overrides: Partial<DiscoveryEntry> = {}): DiscoveryEntry {
    return {
        stationUid: '00000000-0000-0000-0000-000000000001',
        name: 'Wache Hier',
        description: null,
        hasLogo: false,
        logoUrl: null,
        hasPublicWiki: false,
        hasPublicCalendar: false,
        hasPublicBlog: false,
        waitingListOpen: false,
        acceptsFederation: true,
        alreadyFederated: false,
        isOwnStation: false,
        publicSlug: 'wache-hier',
        publicPageUrl: null,
        addressLine: null,
        city: null,
        country: null,
        latitude: null,
        longitude: null,
        clusterUid: null,
        clusterName: null,
        instanceHost: null,
        instanceUrl: null,
        ...overrides,
    }
}

/** A station of another instance on the discovery page, with its page there. */
export function createRemoteDiscoveryEntry(overrides: Partial<DiscoveryEntry> = {}): DiscoveryEntry {
    return createDiscoveryEntry({
        stationUid: '00000000-0000-0000-0000-000000000002',
        name: 'Wache Dort',
        publicSlug: 'wache-dort',
        publicPageUrl: 'https://feuer.example:8443/public/station/wache-dort',
        instanceHost: 'feuer.example',
        instanceUrl: 'https://feuer.example:8443',
        ...overrides,
    })
}

/** Somebody signed in at the given station who may ask other stations to federate. */
export function federationManager(stationUid: string | null = null): DiscoveryViewer {
    return {mayRequestFederation: true, stationUid, offersInvite: true}
}
