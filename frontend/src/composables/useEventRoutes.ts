/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, type InjectionKey} from 'vue'

/**
 * The pages the events screens are mounted on.
 *
 * <p>An association's calendar is a station's calendar, kept on the station the association owns and shown
 * through the station's own screens. The only thing that differs is where a click lands, because the two
 * sets of pages live at different addresses. Everything else, down to the last modal, is the same code.
 */
export interface EventRoutes {
    index: string
    create: string
    edit: string
    detail: string
    detailOnDate: string
    categories: string
    batch: string
}

export const STATION_EVENT_ROUTES: EventRoutes = {
    index: 'events',
    create: 'event-new',
    edit: 'event-edit',
    detail: 'event-detail',
    detailOnDate: 'event-detail-date',
    categories: 'event-categories',
    batch: 'event-batch',
}

const EVENT_ROUTES: InjectionKey<EventRoutes> = Symbol('eventRoutes')

/** Mounts the events screens below this one on another set of pages. Called by the page, not the view. */
export function provideEventRoutes(routes: EventRoutes): void {
    provide(EVENT_ROUTES, routes)
}

/** Where a click in the events screens should land, which is a station's pages unless told otherwise. */
export function useEventRoutes(): EventRoutes {
    return inject(EVENT_ROUTES, STATION_EVENT_ROUTES)
}
