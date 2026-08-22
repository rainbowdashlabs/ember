/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {NewsRoutes} from '@/composables/useNewsRoutes'

/** Where the association's news list lives, which is the one thing that differs from a station's. */
export const CLUSTER_NEWS_ROUTES: NewsRoutes = {
    list: 'cluster-news',
    create: 'cluster-news-create',
    edit: 'cluster-news-edit',
    detail: 'cluster-news-detail',
}
