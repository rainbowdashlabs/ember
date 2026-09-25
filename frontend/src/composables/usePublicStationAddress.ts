/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, inject, type Ref} from 'vue'
import {useRoute} from 'vue-router'
import type {PublicStationInfo} from '@/api/discovery'

/**
 * How a station's public pages are addressed, which is not one answer but two.
 *
 * <p>A request names the station by its identifier, which never changes and which the backend
 * resolves either way. A link names it by the readable name the station was given, which is the
 * address a reader sees, copies and is sent back to. The shell swaps the identifier for the name as
 * soon as the page is up, so a link built from the identifier hands the reader straight back to the
 * address they have just been taken off, and a canonical built from it claims a second address for
 * a page that already has one.
 *
 * <p>A station that was given no readable name is addressed by its identifier in both, because that
 * is the only address it has.
 *
 * @param provided the station where the caller holds it already rather than taking it from the
 *                 shell above it, which the shell itself has to do: nothing provides to itself
 */
export function usePublicStationAddress(provided?: Ref<PublicStationInfo | null>) {
    const route = useRoute()
    const injected = inject<Ref<PublicStationInfo | null> | null>('publicStation', null)

    const station = computed(() => provided?.value ?? injected?.value ?? null)
    const routeParam = computed(() => String(route.params.stationUid ?? ''))

    const stationUid = computed(() => station.value?.stationUid ?? routeParam.value)
    const stationAddress = computed(() => station.value?.publicSlug || stationUid.value)
    const basePath = computed(() => `/public/station/${stationAddress.value}`)

    const canonicalPath = computed(() => (routeParam.value
        ? route.path.replace(`/public/station/${routeParam.value}`, basePath.value)
        : route.path))

    return {station, stationUid, stationAddress, basePath, canonicalPath}
}
