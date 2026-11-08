/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MonitoringCounts} from '@/api/generated/schema'
import type {NavEntryDef} from '@/data/sidebar/navTypes'

/** What the administration sidebar's conditions read. */
export interface AdminNavContext {
    counts: MonitoringCounts
    /** A development build, which lists the developer tools. */
    dev: boolean
}

const beaconActive = ({counts}: AdminNavContext) => counts.beaconActive

/** The instance administration's sidebar, in order. */
export const ADMIN_NAV: NavEntryDef<AdminNavContext>[] = [
    {
        kind: 'group', key: 'dashboard', label: 'sidebar.dashboard', icon: 'gauge',
        children: [
            {kind: 'page', key: 'admin-overview', label: 'sidebar.overview', icon: 'house', to: '/admin/dashboard/overview'},
            {kind: 'page', key: 'admin-statistics', label: 'sidebar.statistics', icon: 'chart-line', to: '/admin/dashboard/statistics'},
        ],
    },
    {
        kind: 'group', key: 'stations', label: 'sidebar.stations', icon: 'building',
        children: [
            {kind: 'page', key: 'admin-stations', label: 'sidebar.manageStations', icon: 'building', to: '/admin/stations', onboarding: 'nav.admin.stations'},
            {kind: 'page', key: 'admin-station-applications', label: 'sidebar.applications', icon: 'clipboard-list', to: '/admin/stations/applications'},
            {kind: 'page', key: 'admin-news', label: 'sidebar.systemNews', icon: 'bullhorn', to: '/admin/news'},
        ],
    },
    {
        kind: 'group', key: 'clusters', label: 'sidebar.clusters', icon: 'sitemap',
        children: [
            {kind: 'page', key: 'admin-clusters', label: 'sidebar.manageClusters', icon: 'sitemap', to: '/admin/clusters'},
        ],
    },
    {
        kind: 'group', key: 'settings', label: 'sidebar.settings', icon: 'gear',
        children: [
            {kind: 'page', key: 'admin-settings', label: 'sidebar.general', icon: 'sliders', to: '/admin/settings', onboarding: 'nav.admin.settings'},
            {kind: 'page', key: 'admin-mailing', label: 'sidebar.mailing', icon: 'envelope', to: '/admin/settings/mailing', onboarding: 'nav.admin.mailing'},
            {
                kind: 'group', key: 'admin-security-group', label: 'sidebar.security', icon: 'shield', onboarding: 'nav.admin.security',
                children: [
                    {kind: 'page', key: 'admin-security', label: 'sidebar.general', icon: 'shield', to: '/admin/settings/security', onboarding: 'nav.admin.security'},
                    {kind: 'page', key: 'admin-security-tokens', label: 'sidebar.securityTokens', icon: 'key', to: '/admin/settings/security/tokens'},
                    {kind: 'page', key: 'admin-security-hibp', label: 'sidebar.securityHibp', icon: 'user-shield', to: '/admin/settings/security/hibp'},
                    {kind: 'page', key: 'admin-security-two-factor', label: 'sidebar.securityTwoFactor', icon: 'mobile-screen', to: '/admin/settings/security/two-factor'},
                    {kind: 'page', key: 'admin-security-signing-keys', label: 'sidebar.securitySigningKeys', icon: 'file-signature', to: '/admin/settings/security/signing-keys'},
                ],
            },
            {kind: 'page', key: 'admin-legal', label: 'sidebar.legal', icon: 'scale-balanced', to: '/admin/settings/legal', onboarding: 'nav.admin.legal'},
            {kind: 'page', key: 'admin-document-fonts', label: 'sidebar.documentFonts', icon: 'font', to: '/admin/settings/fonts'},
        ],
    },
    {kind: 'page', key: 'admin-accounts', label: 'sidebar.accounts', icon: 'users', to: '/admin/accounts'},
    {kind: 'page', key: 'admin-two-factor', label: 'sidebar.twoFactor', icon: 'mobile-screen', to: '/admin/2fa'},
    {
        kind: 'group', key: 'monitoring', label: 'sidebar.monitoring', icon: 'triangle-exclamation',
        children: [
            {kind: 'page', key: 'admin-storage', label: 'sidebar.storageDashboard', icon: 'hard-drive', to: '/admin/monitoring/storage', onboarding: 'nav.admin.storage'},
            {kind: 'page', key: 'admin-storage-backend', label: 'sidebar.storageBackend', icon: 'hard-drive', to: '/admin/monitoring/storage/backend', paletteOnly: true},
            {kind: 'page', key: 'admin-storage-audit', label: 'sidebar.storageAudit', icon: 'list-check', to: '/admin/monitoring/storage/audit', paletteOnly: true},
            {kind: 'page', key: 'admin-mail-log', label: 'sidebar.mailLog', icon: 'envelope', to: '/admin/monitoring/mail-log'},
            {kind: 'page', key: 'admin-log', label: 'sidebar.applicationLog', icon: 'file-lines', to: '/admin/monitoring/log'},
            {kind: 'page', key: 'admin-problems', label: 'sidebar.problemLog', icon: 'bug', to: '/admin/monitoring/problems', badge: ({counts}) => counts.problemLog},
            {kind: 'page', key: 'admin-problem-reports', label: 'sidebar.problemReports', icon: 'flag', to: '/admin/monitoring/problem-reports', badge: ({counts}) => counts.problemReports},
            {kind: 'page', key: 'admin-api-status', label: 'sidebar.apiStatus', icon: 'chart-line', to: '/admin/monitoring/api-status'},
            {kind: 'page', key: 'admin-feed-metrics', label: 'sidebar.feedMetrics', icon: 'rss', to: '/admin/monitoring/feed-metrics'},
            {kind: 'page', key: 'admin-tasks', label: 'sidebar.backgroundTasks', icon: 'list-check', to: '/admin/monitoring/tasks'},
            {kind: 'page', key: 'admin-traffic', label: 'sidebar.adminTraffic', icon: 'tower-broadcast', to: '/admin/monitoring/traffic'},
            {kind: 'page', key: 'admin-discovery', label: 'sidebar.adminDiscovery', icon: 'compass', to: '/admin/monitoring/discovery'},
            {kind: 'page', key: 'admin-maps', label: 'sidebar.maps', icon: 'map-location-dot', to: '/admin/monitoring/maps', onboarding: 'nav.admin.maps'},
        ],
    },
    {
        kind: 'group', key: 'beacon', label: 'sidebar.beacon', icon: 'tower-broadcast',
        badge: ({counts}) => counts.beaconFaults + counts.beaconReports,
        children: [
            {kind: 'page', key: 'admin-beacon', label: 'sidebar.beaconSettings', icon: 'sliders', to: '/admin/beacon'},
            {kind: 'page', key: 'admin-beacon-faults', label: 'sidebar.beaconFaults', icon: 'bug', to: '/admin/beacon/faults', badge: ({counts}) => counts.beaconFaults, visible: beaconActive},
            {kind: 'page', key: 'admin-beacon-reports', label: 'sidebar.beaconReports', icon: 'flag', to: '/admin/beacon/reports', badge: ({counts}) => counts.beaconReports, visible: beaconActive},
            {kind: 'page', key: 'admin-beacon-figures', label: 'sidebar.beaconMetrics', icon: 'chart-line', to: '/admin/beacon/figures', visible: beaconActive},
        ],
    },
    {
        kind: 'group', key: 'dev', label: 'sidebar.devTools', icon: 'code', visible: ({dev}) => dev,
        children: [
            {kind: 'page', key: 'admin-data-tracking', label: 'sidebar.dataTracking', icon: 'database', to: '/admin/dev/data-tracking'},
        ],
    },
]
