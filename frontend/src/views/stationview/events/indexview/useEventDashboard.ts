/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {attendance, events} from '@/api'
import type {AttendanceTemplate} from '@/api/attendance'
import {
    EventKinds,
    type DatedEvent,
    type EventBreak,
    type EventCategory,
    type EventField,
    type EventKindName,
    type EventPageParams,
    type StationEvent,
} from '@/api/events'
import {StationPermission} from '@/api/types'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {usePagedList, PAGE_SIZE} from '@/composables/usePagedList'
import {useSession} from '@/composables/useSession'
import {useEventListFilters} from '@/composables/useEventListFilters'
import {describeFailure, saying} from '@/util/failure'

/**
 * A station's series are counted in dozens, so one request holds them all and the button offering
 * more never appears in practice. It is still a page and not an unbounded list, because a station
 * that does have more of them should see them rather than silently lose the tail.
 */
const SERIES_PAGE_SIZE = 50

/**
 * Everything the events dashboard shows, and the filters that decide which part of it.
 *
 * <p>Two lists, not one. One-time appointments are points in time and read as a run of dates,
 * soonest first while they are still coming and newest first once they have passed. A series is not
 * a point in time, so it sits in a block of its own rather than being wedged between two one-off
 * appointments by the date it happens to fall on next. Each is asked for separately, so a page of
 * ten dates is ten dates rather than whatever is left after the series in it were taken out.
 *
 * <p>Today's appointments, the breaks and the attendance templates belong to the current tab and are
 * loaded once. Attendance templates are asked for on their own and only by whoever may record
 * attendance: asking for them alongside the appointments sank the whole page for everybody else,
 * because one refused call beside the others left a member with an error instead of the list.
 */
export function useEventDashboard() {
    const {t} = useI18n()
    const {hasPermission} = useSession()

    const todayEvents = ref<StationEvent[]>([])
    const breaks = ref<EventBreak[]>([])
    const categories = ref<EventCategory[]>([])
    const templates = ref<AttendanceTemplate[]>([])
    const overviewFields = ref<Record<number, EventField[]>>({})

    const {tab, state, isPast, searchInput, categoryId, from, to} = useEventListFilters(() => refilter())

    function pageParams(offset: number, kind: EventKindName, limit: number): EventPageParams {
        return {
            state: state.value,
            kind,
            categoryId: categoryId.value ? Number(categoryId.value) : undefined,
            search: searchInput.value.trim() || undefined,
            from: from.value || undefined,
            to: to.value || undefined,
            limit,
            offset,
        }
    }

    const dates = usePagedList<DatedEvent>(
        offset => events.listPagedEvents(pageParams(offset, EventKinds.ONE_TIME, PAGE_SIZE)))
    const series = usePagedList<DatedEvent>(
        offset => events.listPagedEvents(pageParams(offset, EventKinds.REPEATING, SERIES_PAGE_SIZE)),
        SERIES_PAGE_SIZE)

    const isEmpty = computed(() => dates.items.value.length === 0 && series.items.value.length === 0)

    const {loading, failure, reload} = useAsyncLoader(async () => {
        const [today, brs, cats, ovFields] = await Promise.all([
            events.listTodayEvents(),
            events.listBreaks(),
            events.listCategories(),
            events.getOverviewFields(),
            dates.load(),
            series.load(),
        ])
        todayEvents.value = today
        breaks.value = brs
        categories.value = cats
        overviewFields.value = ovFields

        templates.value = hasPermission(StationPermission.ATTENDANCE_EDIT)
            ? await attendance.listTemplates().catch(() => [])
            : []
    })

    /**
     * Fetches both lists again after a filter, a search or the tab changed.
     *
     * <p>It says what could not be read and that the rows on screen are now out of date, because
     * they are still drawn: a reader who is not told reads them as the answer to the filter they
     * just set.
     */
    async function refilter() {
        if (loading.value) return
        try {
            await Promise.all([dates.load(), series.load()])
        } catch (e) {
            failure.value = saying(describeFailure(e, t), t('events.listNotLoaded'))
        }
    }

    /** Asks for the next page of one of the two lists, named by the kind of appointment it holds. */
    async function loadMore(kind: EventKindName) {
        try {
            await (kind === EventKinds.REPEATING ? series : dates).loadMore()
        } catch (e) {
            failure.value = saying(describeFailure(e, t), t('events.moreNotLoaded'))
        }
    }

    return {
        tab,
        isPast,
        searchInput,
        categoryId,
        from,
        to,
        todayEvents,
        breaks,
        categories,
        templates,
        overviewFields,
        dates: dates.view,
        series: series.view,
        isEmpty,
        loading,
        failure,
        reload,
        loadMore,
    }
}
