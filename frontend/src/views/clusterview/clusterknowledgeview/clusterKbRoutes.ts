/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {KbRoutes} from '@/views/stationview/knowledge/knowledgebaseview/useKbNavigation'

/** Where the association's knowledge base lives, which is the one thing that differs from a station's. */
export const CLUSTER_KB_ROUTES: KbRoutes = {
    browse: 'cluster-knowledge',
    file: 'cluster-kb-file',
    versions: 'cluster-kb-versions',
}
