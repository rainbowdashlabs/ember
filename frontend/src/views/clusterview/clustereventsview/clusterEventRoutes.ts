/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EventRoutes} from '@/composables/useEventRoutes'

/** Where the association's calendar lives, which is the one thing that differs from a station's. */
export const CLUSTER_EVENT_ROUTES: EventRoutes = {
    index: 'cluster-events',
    create: 'cluster-event-new',
    edit: 'cluster-event-edit',
    detail: 'cluster-event-detail',
    detailOnDate: 'cluster-event-detail-date',
    categories: 'cluster-event-categories',
    batch: 'cluster-event-batch',
}
