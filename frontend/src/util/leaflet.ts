/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'

let defaultIconPatched = false

/**
 * Loads Leaflet on demand and rewires the default marker icon to the bundled image
 * assets. Leaflet resolves its default icon URLs relative to the stylesheet location,
 * which does not exist in production builds and leaves markers invisible.
 */
export async function loadLeaflet() {
    const L = (await import('leaflet')).default
    if (!defaultIconPatched) {
        defaultIconPatched = true
        delete (L.Icon.Default.prototype as {_getIconUrl?: unknown})._getIconUrl
        L.Icon.Default.mergeOptions({
            iconRetinaUrl: markerIcon2x,
            iconUrl: markerIcon,
            shadowUrl: markerShadow,
        })
    }
    return L
}
