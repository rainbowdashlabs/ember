/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, readonly} from 'vue'
import {session} from '@/api'
import client from '@/api/client'
import {getItem, removeItem, setItem} from '@/api/storage'
import type {StationMembership} from '@/api/generated/schema'

/**
 * The stations the reader belongs to, which one they work at, and the logos the browser fetched for them,
 * held per request.
 *
 * <p>The current station starts out unknown, the same on the server and in the browser; the one this
 * browser stored is put in by {@link restoreActiveStation}, never read here.
 */
function stationsState() {
    return {
        stationList: useState<StationMembership[]>('useStations.list', () => []),
        loaded: useState('useStations.loaded', () => false),
        currentStationId: useState<string | null>('useStations.current', () => null),
        activeLogoUrl: useState<string | null>('useStations.activeLogo', () => null),
        stationLogos: useState<Map<string, string>>('useStations.logos', () => new Map()),
    }
}

/** Puts the station this browser last worked at into the state. Called by the client plugin. */
export function restoreActiveStation() {
    stationsState().currentStationId.value = getItem('station_id')
}

async function fetchLogo(stationId: string): Promise<string | null> {
    const res = await client.get(`/stations/${stationId}/logo?size=256`, {
        responseType: 'blob',
        validateStatus: (status) => status === 200 || status === 404,
    })
    if (res.status === 404) return null
    return URL.createObjectURL(res.data)
}

/**
 * The stations the reader belongs to and which one they work at, exposed read-only with the operations
 * that change them. Take it at the top of a setup or a composable, before anything is awaited.
 */
export function useStations() {
    const {stationList, loaded, currentStationId, activeLogoUrl, stationLogos} = stationsState()

    async function load() {
        loaded.value = false
        try {
            stationList.value = await session.getStations()
        } catch {
            stationList.value = []
        }
        currentStationId.value = getItem('station_id')
        loaded.value = true
        await loadAllLogos()
    }

    function clear() {
        stationList.value = []
        loaded.value = false
        currentStationId.value = null
        if (activeLogoUrl.value) {
            URL.revokeObjectURL(activeLogoUrl.value)
            activeLogoUrl.value = null
        }
        stationLogos.value.forEach(url => URL.revokeObjectURL(url))
        stationLogos.value = new Map()
    }

    function setActiveStation(stationId: string) {
        setItem('station_id', stationId)
        currentStationId.value = stationId
        loadActiveLogo()
    }

    /**
     * The one station the account belongs to, where it belongs to exactly one. Somebody with a single
     * station has nothing to choose between, so the overview of all stations is never where they are
     * sent on their own.
     */
    async function soleStationId(): Promise<string | null> {
        if (!loaded.value) await load()
        const [only] = stationList.value
        return stationList.value.length === 1 && only ? only.stationId : null
    }

    function clearActiveStation() {
        removeItem('station_id')
        currentStationId.value = null
        activeLogoUrl.value = null
    }

    /** Fetches the logos not fetched yet; a logo that fails to arrive is left out. */
    async function loadAllLogos() {
        const newMap = new Map(stationLogos.value)
        for (const station of stationList.value) {
            if (newMap.has(station.stationId)) continue
            const url = await fetchLogo(station.stationId).catch(() => null)
            if (!url) continue
            newMap.set(station.stationId, url)
            if (station.stationId === currentStationId.value) activeLogoUrl.value = url
        }
        stationLogos.value = newMap
    }

    async function loadActiveLogo() {
        const stationId = currentStationId.value
        if (stationId === null) {
            activeLogoUrl.value = null
            return
        }
        const existing = stationLogos.value.get(stationId)
        if (existing) {
            activeLogoUrl.value = existing
            return
        }
        const url = await fetchLogo(stationId).catch(() => null)
        activeLogoUrl.value = url
        if (url) stationLogos.value = new Map(stationLogos.value).set(stationId, url)
    }

    function getStationLogoUrl(stationId: string): string | null {
        return stationLogos.value.get(stationId) ?? null
    }

    const hasMultipleStations = computed(() => stationList.value.length > 1)

    const activeStation = computed(() => {
        if (currentStationId.value === null) return null
        return stationList.value.find(s => s.stationId === currentStationId.value) ?? null
    })

    return {
        stationList: readonly(stationList),
        loaded: readonly(loaded),
        currentStationId: readonly(currentStationId),
        activeLogoUrl: readonly(activeLogoUrl),
        load,
        clear,
        setActiveStation,
        clearActiveStation,
        soleStationId,
        loadActiveLogo,
        getStationLogoUrl,
        hasMultipleStations,
        activeStation,
    }
}
