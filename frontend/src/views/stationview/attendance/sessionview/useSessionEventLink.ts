/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, type Ref} from 'vue'
import {useRouter} from 'vue-router'
import type {AttendanceSession} from '@/api/attendance'
import {useEventRoutes} from '@/composables/useEventRoutes'
import {useSession} from '@/composables/useSession'
import {stationDayOf} from '@/util/format'

/**
 * The way back from a sheet to the appointment it was taken for.
 *
 * <p>A repeating appointment has a sheet per date, so the date travels along: without it the page
 * would open on the first of the series, which is rarely the one being looked at. The day is read
 * where the station stands rather than where the reader does, so a sheet does not shift by a date
 * for somebody travelling.
 */
export function useSessionEventLink(session: Ref<AttendanceSession | null>) {
    const router = useRouter()
    const eventRoutes = useEventRoutes()
    const {stationTimezone} = useSession()

    const sheetDay = computed(() => {
        const start = session.value?.startTime
        return start ? stationDayOf(new Date(start), stationTimezone.value) : null
    })

    function openEvent() {
        const eventId = session.value?.eventId
        if (!eventId) return
        const date = sheetDay.value
        if (!date) {
            router.push({name: eventRoutes.detail, params: {id: eventId}})
            return
        }
        router.push({name: eventRoutes.detailOnDate, params: {id: eventId, date}})
    }

    return {openEvent}
}
