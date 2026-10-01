/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
import type {Map as LeafletMap} from 'leaflet'

import type {PublicMapsConfig} from '@/api/generated/schema'
import {browserShallowRef} from '@/util/browserState'

const defaultIcon = browserShallowRef({patched: false})

/**
 * Loads Leaflet on demand and rewires the default marker icon to the bundled image
 * assets. Leaflet resolves its default icon URLs relative to the stylesheet location,
 * which does not exist in production builds and leaves markers invisible.
 */
export async function loadLeaflet() {
    const L = (await import('leaflet')).default
    if (!defaultIcon.value.patched) {
        defaultIcon.value.patched = true
        delete (L.Icon.Default.prototype as {_getIconUrl?: unknown})._getIconUrl
        L.Icon.Default.mergeOptions({
            iconRetinaUrl: markerIcon2x,
            iconUrl: markerIcon,
            shadowUrl: markerShadow,
        })
    }
    return L
}

/**
 * Lays the configured tiles under a map. A provider without a template has no tiles to offer, so
 * the map stays blank rather than asking an address that does not exist.
 */
export function addTileLayer(
    L: Awaited<ReturnType<typeof loadLeaflet>>,
    map: LeafletMap,
    config: PublicMapsConfig,
): void {
    if (!config.urlTemplate) return
    L.tileLayer(config.urlTemplate, {
        minZoom: config.minZoom,
        maxZoom: config.maxZoom,
        attribution: config.attribution ?? undefined,
    }).addTo(map)
}
