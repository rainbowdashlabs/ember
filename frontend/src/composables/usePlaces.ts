/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import {getCrossStationDashboard} from '@/api/session'
import {useStations} from '@/composables/useStations'
import {useCluster} from '@/composables/useCluster'
import {useSession} from '@/composables/useSession'
import {useMonitoringCounts} from '@/composables/useMonitoringCounts'
import {useClusterNotificationCount} from '@/composables/useClusterNotificationCount'

/** One place the reader can work in: a station, an association, their own account, or the instance administration. */
export interface Place {
    key: string
    kind: 'station' | 'cluster' | 'account' | 'admin'
    label: string
    icon: string
    logoUrl: string | null
    badge: number
    current: boolean
}

/**
 * The places the reader can switch between, which one they are in, and the number waiting in each.
 *
 * <p>Switching to a station or an association loads the session anew for it, the way the station
 * switcher always did. The counts are read only when the list is opened, since only the menu shows them.
 */
export function usePlaces() {
    const {t} = useI18n()
    const route = useRoute()
    const {isAdmin} = useSession()
    const stations = useStations()
    const clusters = useCluster()
    const {counts: monitoring, refresh: refreshMonitoring} = useMonitoringCounts()
    const {count: clusterUnread, refresh: refreshClusterUnread} = useClusterNotificationCount()
    const stationCounts = ref<Record<string, number>>({})

    onMounted(() => {
        if (!stations.loaded.value) void stations.load()
        if (!clusters.loaded.value) void clusters.load()
    })

    const area = computed<Place['kind']>(() => {
        if (route.path.startsWith('/admin')) return 'admin'
        if (route.path.startsWith('/cluster')) return 'cluster'
        if (route.path.startsWith('/account')) return 'account'
        return 'station'
    })

    const places = computed<Place[]>(() => [
        ...stations.stationList.value.map((station): Place => ({
            key: `station:${station.stationId}`,
            kind: 'station',
            label: station.stationName,
            icon: 'building',
            logoUrl: stations.getStationLogoUrl(station.stationId),
            badge: stationCounts.value[station.stationId] ?? 0,
            current: area.value === 'station' && station.stationId === stations.currentStationId.value,
        })),
        ...clusters.clusterList.value.map((cluster): Place => ({
            key: `cluster:${cluster.uid}`,
            kind: 'cluster',
            label: cluster.name,
            icon: 'sitemap',
            logoUrl: null,
            badge: cluster.uid === clusters.currentClusterId.value ? clusterUnread.value : 0,
            current: area.value === 'cluster' && cluster.uid === clusters.currentClusterId.value,
        })),
        {
            key: 'account',
            kind: 'account',
            label: t('placeSwitcher.account'),
            icon: 'user',
            logoUrl: null,
            badge: 0,
            current: area.value === 'account',
        },
        ...(isAdmin() ? [{
            key: 'admin',
            kind: 'admin' as const,
            label: t('placeSwitcher.administration'),
            icon: 'shield',
            logoUrl: null,
            badge: monitoring.value.problemLog + monitoring.value.problemReports,
            current: area.value === 'admin',
        }] : []),
    ])

    /** Whether there is anywhere else to go: the own account and nothing more needs no switcher. */
    const switchable = computed(() => places.value.length > 1)

    const current = computed(() => places.value.find(place => place.current))

    /** The association the count of unread notices is for: the one chosen, while the reader belongs to it. */
    const countedCluster = computed(() => clusters.clusterList.value
        .some(cluster => cluster.uid === clusters.currentClusterId.value))

    /**
     * Reads the numbers each place shows in the menu, keeping what is there when one cannot be read.
     *
     * <p>The association's notices are counted only where an association is chosen and the reader belongs
     * to it. That count is read in the association, and anywhere else the server refuses it, which the
     * reader would see as a refusal for a menu they only opened.
     */
    async function refreshCounts() {
        const reads: Promise<unknown>[] = [stations.loadAllLogos()]
        if (countedCluster.value) reads.push(refreshClusterUnread())
        if (isAdmin()) reads.push(refreshMonitoring())
        reads.push(getCrossStationDashboard().then(dashboard => {
            stationCounts.value = Object.fromEntries(dashboard.stations
                .map(station => [station.stationId, station.notifications + station.requirements]))
        }).catch(() => {}))
        await Promise.all(reads)
    }

    function go(place: Place) {
        if (place.kind === 'admin') {
            window.location.href = '/admin/dashboard/overview'
            return
        }
        if (place.kind === 'account') {
            window.location.href = '/account'
            return
        }
        const id = place.key.slice(place.key.indexOf(':') + 1)
        if (place.kind === 'station') {
            stations.setActiveStation(id)
            window.location.href = '/station/dashboard/overview'
            return
        }
        clusters.setActiveCluster(id)
        window.location.href = '/cluster'
    }

    /** All of the account's stations at once, offered where there is more than one. */
    const crossStation = computed(() => stations.stationList.value.length > 1)

    return {places, current, switchable, crossStation, refreshCounts, go}
}
