/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ClusterPermission} from '@/api/clusters'
import type {NavEntryDef} from '@/data/sidebar/navTypes'

/** What the association sidebar's conditions read. */
export interface ClusterNavContext {
    hasClusterPermission: (permission: string) => boolean
    unreadNotifications: number
}

type Condition = (context: ClusterNavContext) => boolean

function may(...permissions: string[]): Condition {
    return ({hasClusterPermission}) => permissions.some(permission => hasClusterPermission(permission))
}

/** The association's sidebar, in order. Each entry answers to what the association granted. */
export const CLUSTER_NAV: NavEntryDef<ClusterNavContext>[] = [
    {
        kind: 'group', key: 'cluster', label: 'clusterSidebar.cluster', icon: 'sitemap',
        children: [
            {kind: 'page', key: 'cluster-overview', label: 'clusterSidebar.overview', icon: 'house', to: '/cluster'},
            {kind: 'page', key: 'cluster-notifications', label: 'clusterSidebar.notifications', icon: 'bell', to: '/cluster/notifications', badge: ({unreadNotifications}) => unreadNotifications, visible: may(ClusterPermission.USER)},
            {kind: 'page', key: 'cluster-settings', label: 'clusterSidebar.settings', icon: 'sliders', to: '/cluster/settings', visible: may(ClusterPermission.CLUSTER_GENERAL)},
        ],
    },
    {
        kind: 'group', key: 'cluster-stations-group', label: 'clusterSidebar.stations', icon: 'building', visible: may(ClusterPermission.USER),
        children: [
            {kind: 'page', key: 'cluster-stations', label: 'clusterSidebar.stationList', icon: 'building', to: '/cluster/stations'},
            {kind: 'page', key: 'cluster-station-groups', label: 'clusterSidebar.stationGroups', icon: 'layer-group', to: '/cluster/stations/groups', visible: may(ClusterPermission.CLUSTER_STATIONS)},
            {kind: 'page', key: 'cluster-applications', label: 'clusterSidebar.applications', icon: 'clipboard-list', to: '/cluster/applications', visible: may(ClusterPermission.CLUSTER_STATIONS)},
        ],
    },
    {
        kind: 'group', key: 'cluster-team-group', label: 'clusterSidebar.team', icon: 'user-group', visible: may(ClusterPermission.CLUSTER_MEMBER_READ),
        children: [
            {kind: 'page', key: 'cluster-team', label: 'clusterSidebar.teamList', icon: 'users', to: '/cluster/team'},
            {kind: 'page', key: 'cluster-team-groups', label: 'clusterSidebar.memberGroups', icon: 'user-group', to: '/cluster/team/groups'},
        ],
    },
    {
        kind: 'group', key: 'cluster-members-group', label: 'clusterSidebar.members', icon: 'users', visible: may(ClusterPermission.CLUSTER_MEMBER_MANAGER),
        children: [
            {kind: 'page', key: 'cluster-members', label: 'clusterSidebar.memberList', icon: 'users-gear', to: '/cluster/members'},
            {kind: 'page', key: 'cluster-fields', label: 'clusterSidebar.fieldList', icon: 'id-card', to: '/cluster/members/fields'},
        ],
    },
    {kind: 'page', key: 'cluster-knowledge', label: 'clusterSidebar.knowledge', icon: 'book', to: '/cluster/knowledge', visible: may(ClusterPermission.USER)},
    {kind: 'page', key: 'cluster-news', label: 'clusterSidebar.news', icon: 'newspaper', to: '/cluster/news', visible: may(ClusterPermission.USER)},
    {kind: 'page', key: 'cluster-events', label: 'clusterSidebar.events', icon: 'calendar', to: '/cluster/events', visible: may(ClusterPermission.USER)},
    {
        kind: 'group', key: 'cluster-documents', label: 'clusterSidebar.documents', icon: 'file-signature', visible: may(ClusterPermission.CLUSTER_DOCUMENT_TEMPLATE_EDIT),
        children: [
            {kind: 'page', key: 'cluster-document-templates', label: 'clusterSidebar.documentTemplates', icon: 'file-signature', to: '/cluster/templates'},
            {kind: 'page', key: 'cluster-document-fonts', label: 'clusterSidebar.documentFonts', icon: 'font', to: '/cluster/fonts'},
            {kind: 'page', key: 'cluster-media', label: 'clusterSidebar.media', icon: 'image', to: '/cluster/media'},
        ],
    },
    {
        kind: 'group', key: 'cluster-inventory-group', label: 'clusterSidebar.inventory', icon: 'boxes-stacked', visible: may(ClusterPermission.CLUSTER_INVENTORY_READ),
        children: [
            {kind: 'page', key: 'cluster-inventory', label: 'clusterSidebar.stock', icon: 'boxes-stacked', to: '/cluster/inventory'},
            {kind: 'page', key: 'cluster-movements', label: 'clusterSidebar.movements', icon: 'right-left', to: '/cluster/inventory/movements'},
        ],
    },
    {
        kind: 'group', key: 'cluster-governance', label: 'clusterSidebar.governance', icon: 'gear',
        children: [
            {kind: 'page', key: 'cluster-modules', label: 'clusterSidebar.modules', icon: 'puzzle-piece', to: '/cluster/modules', visible: may(ClusterPermission.CLUSTER_MODULES)},
            {kind: 'page', key: 'cluster-look-and-feel', label: 'clusterSidebar.lookAndFeel', icon: 'palette', to: '/cluster/look-and-feel', visible: may(ClusterPermission.CLUSTER_LOOK_AND_FEEL)},
            {kind: 'page', key: 'cluster-storage', label: 'clusterSidebar.storage', icon: 'hard-drive', to: '/cluster/storage', visible: may(ClusterPermission.CLUSTER_STORAGE)},
        ],
    },
]
