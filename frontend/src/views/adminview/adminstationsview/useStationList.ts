/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {stations} from '@/api'
import type {AdminStationSummary} from '@/api/generated/schema'
import {getItem, setItem} from '@/api/storage'
import {useConfigPanel} from '@/composables/useConfigPanel'
import {useDataTable} from '@/composables/useDataTable'
import {stationColumns} from './stationColumns'
import {StationFilters, filterFromQuery, matchesFilter, viewFromStorage, type StationFilter, type StationView} from './stationList'

const VIEW_STORAGE_KEY = 'adminStations.viewMode'

/**
 * The station list of the instance administration: every station with its state, narrowed by the filter
 * the address carries in `?state=`, drawn as tiles or as a table as this browser last chose.
 */
export function useStationList() {
    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()

    const {config: list, loading, failure, reload} = useConfigPanel<AdminStationSummary[]>({
        initial: [],
        fetch: () => stations.listAdminStations(),
    })

    const filter = computed<StationFilter>({
        get: () => filterFromQuery(route.query.state),
        set: value => {
            const query = {...route.query}
            if (value === StationFilters.ALL) delete query.state
            else query.state = value
            void router.replace({query})
        },
    })

    const view = ref<StationView>(viewFromStorage(getItem(VIEW_STORAGE_KEY)))
    watch(view, value => setItem(VIEW_STORAGE_KEY, value))

    const shown = computed(() => list.value.filter(station => matchesFilter(station, filter.value)))

    const table = useDataTable<AdminStationSummary>({
        id: 'admin-stations',
        perStation: false,
        rows: shown,
        columns: computed(() => stationColumns(t)),
        rowKey: station => station.id,
        searchText: station => station.managerEmail ?? '',
    })

    /** Puts the station as the server answered it after a reminder in place of the one listed. */
    function replace(updated: AdminStationSummary) {
        list.value = list.value.map(station => station.id === updated.id ? updated : station)
    }

    return {list, shown, loading, failure, reload, filter, view, table, replace}
}
