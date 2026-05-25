/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, readonly, ref} from 'vue'
import {session} from '@/api'
import client from '@/api/client'
import {getItem, setItem} from '@/api/storage'
import type {StationMembership} from '@/api/types'

const stationList = ref<StationMembership[]>([])
const loaded = ref(false)
const currentStationId = ref<string | null>(getItem('station_id') ?? null)
const activeLogoUrl = ref<string | null>(null)
const stationLogos = ref<Map<string, string>>(new Map())

export function useStations() {
    async function load() {
        loaded.value = false
        try {
            stationList.value = await session.getStations()
        } catch {
            stationList.value = []
        }
        const stored = getItem('station_id')
        currentStationId.value = stored ?? null
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

    async function fetchLogo(stationId: string): Promise<string | null> {
        const res = await client.get(`/stations/${stationId}/logo`, {
            responseType: 'blob',
            validateStatus: (status) => status === 200 || status === 404,
        })
        if (res.status === 404) return null
        return URL.createObjectURL(res.data)
    }

    async function loadAllLogos() {
        const newMap = new Map(stationLogos.value)
        for (const station of stationList.value) {
            if (newMap.has(station.stationId)) continue
            try {
                const url = await fetchLogo(station.stationId)
                if (url) {
                    newMap.set(station.stationId, url)
                    if (station.stationId === currentStationId.value) {
                        activeLogoUrl.value = url
                    }
                }
            } catch {
                // Network error
            }
        }
        stationLogos.value = newMap
    }

    async function loadActiveLogo() {
        if (currentStationId.value === null) {
            activeLogoUrl.value = null
            return
        }
        const existing = stationLogos.value.get(currentStationId.value)
        if (existing) {
            activeLogoUrl.value = existing
            return
        }
        try {
            const url = await fetchLogo(currentStationId.value)
            if (url) {
                const newMap = new Map(stationLogos.value)
                newMap.set(currentStationId.value, url)
                stationLogos.value = newMap
                activeLogoUrl.value = url
            } else {
                activeLogoUrl.value = null
            }
        } catch {
            activeLogoUrl.value = null
        }
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
        loadActiveLogo,
        getStationLogoUrl,
        hasMultipleStations,
        activeStation,
    }
}
