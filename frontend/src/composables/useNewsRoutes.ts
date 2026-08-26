/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, type InjectionKey} from 'vue'

/**
 * The pages the news screens are mounted on.
 *
 * <p>An association's news list is a station's, kept on the station the association owns and shown through
 * the station's own screens. The only thing that differs is where a click lands, because the two sets of
 * pages live at different addresses.
 */
export interface NewsRoutes {
    list: string
    create: string
    edit: string
    detail: string
}

export const STATION_NEWS_ROUTES: NewsRoutes = {
    list: 'news-list',
    create: 'news-create',
    edit: 'news-edit',
    detail: 'news-detail',
}

const NEWS_ROUTES: InjectionKey<NewsRoutes> = Symbol('newsRoutes')

/** Mounts the news screens below this one on another set of pages. Called by the page, not the view. */
export function provideNewsRoutes(routes: NewsRoutes): void {
    provide(NEWS_ROUTES, routes)
}

/** Where a click in the news screens should land, which is a station's pages unless told otherwise. */
export function useNewsRoutes(): NewsRoutes {
    return inject(NEWS_ROUTES, STATION_NEWS_ROUTES)
}
