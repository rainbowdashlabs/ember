/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch} from 'vue'
import deDE from '@/i18n/de-DE'

export interface HelpSearchEntry {
    route: string
    path: string
    title: string
    section: string
    text: string
}

export interface HelpSearchResult {
    entry: HelpSearchEntry
    snippet: string
    matchStart: number
    matchEnd: number
}

/**
 * Maps each help center route name to its i18n key prefix and human-readable section breadcrumb.
 */
const HELP_PAGE_MAP: { route: string; path: string; i18nPrefix: string; section: string }[] = [
    // Basics
    {route: 'help-welcome', path: '/helpcenter/station/basics', i18nPrefix: 'helpCenter.welcome', section: 'Grundlagen'},
    {route: 'help-basics-overview', path: '/helpcenter/station/basics/overview', i18nPrefix: 'helpCenter.basics.overview', section: 'Grundlagen > Was ist Ember?'},
    {route: 'help-basics-roles', path: '/helpcenter/station/basics/roles', i18nPrefix: 'helpCenter.basics.roles', section: 'Grundlagen > Rollen'},
    {route: 'help-basics-modules', path: '/helpcenter/station/basics/modules', i18nPrefix: 'helpCenter.basics.modules', section: 'Grundlagen > Module'},
    {route: 'help-basics-hosting', path: '/helpcenter/station/basics/hosting', i18nPrefix: 'helpCenter.basics.hosting', section: 'Grundlagen > Selbst betreiben'},
    {route: 'help-basics-federation', path: '/helpcenter/station/basics/federation', i18nPrefix: 'helpCenter.basics.federation', section: 'Grundlagen > Föderation'},
    // Dashboard
    {route: 'help-dashboard-module-overview', path: '/helpcenter/station/dashboard', i18nPrefix: 'helpCenter.dashboardOverview', section: 'Dashboard'},
    {route: 'help-dashboard-overview', path: '/helpcenter/station/dashboard/overview', i18nPrefix: 'helpCenter.dashboard', section: 'Dashboard > Übersicht'},
    {route: 'help-dashboard-statistics', path: '/helpcenter/station/dashboard/statistics', i18nPrefix: 'helpCenter.statistics', section: 'Dashboard > Statistiken'},
    // News
    {route: 'help-news-overview', path: '/helpcenter/station/news', i18nPrefix: 'helpCenter.newsOverview', section: 'Neuigkeiten'},
    {route: 'help-news-list', path: '/helpcenter/station/news/list', i18nPrefix: 'helpCenter.news', section: 'Neuigkeiten > Liste'},
    {route: 'help-news-create', path: '/helpcenter/station/news/create', i18nPrefix: 'helpCenter.newsEdit', section: 'Neuigkeiten > Erstellen'},
    {route: 'help-news-edit', path: '/helpcenter/station/news/edit', i18nPrefix: 'helpCenter.newsEdit', section: 'Neuigkeiten > Bearbeiten'},
    // Profile
    {route: 'help-profile-overview', path: '/helpcenter/station/profile', i18nPrefix: 'helpCenter.profileOverview', section: 'Profil'},
    {route: 'help-profile', path: '/helpcenter/station/profile/index', i18nPrefix: 'helpCenter.profile', section: 'Profil > Mein Profil'},
    {route: 'help-profile-absences', path: '/helpcenter/station/profile/absences', i18nPrefix: 'helpCenter.absences', section: 'Profil > Abwesenheit'},
    {route: 'help-profile-managed', path: '/helpcenter/station/profile/managed', i18nPrefix: 'helpCenter.managed', section: 'Profil > Verwaltete Profile'},
    {route: 'help-profile-inventory', path: '/helpcenter/station/profile/inventory', i18nPrefix: 'helpCenter.inventoryMy', section: 'Profil > Mein Inventar'},
    {route: 'help-profile-settings', path: '/helpcenter/station/profile/settings', i18nPrefix: 'helpCenter.settings', section: 'Profil > Einstellungen'},
    {route: 'help-profile-theme', path: '/helpcenter/station/profile/theme', i18nPrefix: 'helpCenter.themeUser', section: 'Profil > Erscheinungsbild'},
    // Station management
    {route: 'help-manage-overview', path: '/helpcenter/station/manage', i18nPrefix: 'helpCenter.manageOverview', section: 'Verwaltung'},
    {route: 'help-station-manage', path: '/helpcenter/station/manage/station', i18nPrefix: 'helpCenter.stationManage', section: 'Verwaltung > Wache'},
    {route: 'help-station-attendance-config', path: '/helpcenter/station/manage/attendance-config', i18nPrefix: 'helpCenter.attendanceConfig', section: 'Verwaltung > Anwesenheitsvorlagen'},
    {route: 'help-station-attendance-config-edit', path: '/helpcenter/station/manage/attendance-config/edit', i18nPrefix: 'helpCenter.attendanceConfigEdit', section: 'Verwaltung > Vorlage bearbeiten'},
    {route: 'help-station-members-config', path: '/helpcenter/station/manage/members-config', i18nPrefix: 'helpCenter.membersConfig', section: 'Verwaltung > Mitglieder-Konfiguration'},
    {route: 'help-station-mail-config', path: '/helpcenter/station/manage/mail-config', i18nPrefix: 'helpCenter.mailConfig', section: 'Verwaltung > E-Mail'},
    {route: 'help-station-theme-manage', path: '/helpcenter/station/manage/theme', i18nPrefix: 'helpCenter.themeManage', section: 'Verwaltung > Farbschema'},
    {route: 'help-station-federation', path: '/helpcenter/station/manage/federation', i18nPrefix: 'helpCenter.federation', section: 'Verwaltung > Föderation'},
    // Members
    {route: 'help-members-overview', path: '/helpcenter/station/members', i18nPrefix: 'helpCenter.membersOverview', section: 'Mitglieder'},
    {route: 'help-members-create', path: '/helpcenter/station/members/create', i18nPrefix: 'helpCenter.membersCreate', section: 'Mitglieder > Anlegen'},
    {route: 'help-members-list', path: '/helpcenter/station/members/list', i18nPrefix: 'helpCenter.membersList', section: 'Mitglieder > Liste'},
    {route: 'help-members-import', path: '/helpcenter/station/members/import', i18nPrefix: 'helpCenter.membersImport', section: 'Mitglieder > Import'},
    {route: 'help-members-import-team', path: '/helpcenter/station/members/import-team', i18nPrefix: 'helpCenter.membersImportTeam', section: 'Mitglieder > Team-Import'},
    {route: 'help-members-detail', path: '/helpcenter/station/members/detail', i18nPrefix: 'helpCenter.membersDetail', section: 'Mitglieder > Detail'},
    {route: 'help-members-edit', path: '/helpcenter/station/members/edit', i18nPrefix: 'helpCenter.membersEdit', section: 'Mitglieder > Bearbeiten'},
    {route: 'help-members-groups', path: '/helpcenter/station/members/groups', i18nPrefix: 'helpCenter.membersGroups', section: 'Mitglieder > Gruppen'},
    {route: 'help-members-tags', path: '/helpcenter/station/members/tags', i18nPrefix: 'helpCenter.membersTags', section: 'Mitglieder > Tags'},
    {route: 'help-members-changes', path: '/helpcenter/station/members/changes', i18nPrefix: 'helpCenter.membersChanges', section: 'Mitglieder > Änderungen'},
    {route: 'help-members-former', path: '/helpcenter/station/members/former', i18nPrefix: 'helpCenter.membersFormer', section: 'Mitglieder > Ehemalige'},
    {route: 'help-waiting-lists', path: '/helpcenter/station/members/waiting-lists', i18nPrefix: 'helpCenter.waitingList', section: 'Mitglieder > Wartelisten'},
    // Inventory
    {route: 'help-inventory-module-overview', path: '/helpcenter/station/inventory', i18nPrefix: 'helpCenter.inventoryModuleOverview', section: 'Inventar'},
    {route: 'help-inventory-overview', path: '/helpcenter/station/inventory/overview', i18nPrefix: 'helpCenter.inventoryOverview', section: 'Inventar > Übersicht'},
    {route: 'help-inventory-my', path: '/helpcenter/station/inventory/my', i18nPrefix: 'helpCenter.inventoryMy', section: 'Inventar > Mein Inventar'},
    {route: 'help-inventory-exchanges', path: '/helpcenter/station/inventory/exchanges', i18nPrefix: 'helpCenter.inventoryExchanges', section: 'Inventar > Tausch'},
    {route: 'help-inventory-members', path: '/helpcenter/station/inventory/members', i18nPrefix: 'helpCenter.inventoryMembers', section: 'Inventar > Mitglieder'},
    {route: 'help-inventory-detail', path: '/helpcenter/station/inventory/detail', i18nPrefix: 'helpCenter.inventoryDetail', section: 'Inventar > Detail'},
    {route: 'help-inventory-edit', path: '/helpcenter/station/inventory/edit', i18nPrefix: 'helpCenter.inventoryEdit', section: 'Inventar > Bearbeiten'},
    {route: 'help-inventory-member', path: '/helpcenter/station/inventory/member', i18nPrefix: 'helpCenter.inventoryMember', section: 'Inventar > Mitglied'},
    {route: 'help-inventory-manage', path: '/helpcenter/station/inventory/manage', i18nPrefix: 'helpCenter.inventoryManage', section: 'Inventar > Verwalten'},
    {route: 'help-inventory-requirements', path: '/helpcenter/station/inventory/requirements', i18nPrefix: 'helpCenter.inventoryRequirements', section: 'Inventar > Anforderungen'},
    {route: 'help-inventory-checks', path: '/helpcenter/station/inventory/checks', i18nPrefix: 'helpCenter.inventoryChecks', section: 'Inventar > Prüfung'},
    {route: 'help-inventory-check-member', path: '/helpcenter/station/inventory/checks/member', i18nPrefix: 'helpCenter.inventoryCheckMember', section: 'Inventar > Prüfung > Mitglied'},
    {route: 'help-inventory-check-result', path: '/helpcenter/station/inventory/checks/result', i18nPrefix: 'helpCenter.inventoryCheckResult', section: 'Inventar > Prüfung > Ergebnis'},
    {route: 'help-inventory-procurement', path: '/helpcenter/station/inventory/procurement', i18nPrefix: 'helpCenter.inventoryProcurement', section: 'Inventar > Beschaffung'},
    // Attendance
    {route: 'help-attendance-overview', path: '/helpcenter/station/attendance', i18nPrefix: 'helpCenter.attendanceOverview', section: 'Anwesenheit'},
    {route: 'help-attendance-new', path: '/helpcenter/station/attendance/new', i18nPrefix: 'helpCenter.attendanceNew', section: 'Anwesenheit > Neue Anwesenheit'},
    {route: 'help-attendance-session', path: '/helpcenter/station/attendance/session', i18nPrefix: 'helpCenter.attendanceSession', section: 'Anwesenheit > Sitzung'},
    {route: 'help-attendance-past', path: '/helpcenter/station/attendance/past', i18nPrefix: 'helpCenter.attendancePast', section: 'Anwesenheit > Vergangene'},
    {route: 'help-attendance-report', path: '/helpcenter/station/attendance/report', i18nPrefix: 'helpCenter.attendanceReport', section: 'Anwesenheit > Bericht'},
    // Events
    {route: 'help-events-overview', path: '/helpcenter/station/events/overview', i18nPrefix: 'helpCenter.eventsOverview', section: 'Termine'},
    {route: 'help-events-upcoming', path: '/helpcenter/station/events/upcoming', i18nPrefix: 'helpCenter.eventsUpcoming', section: 'Termine > Anstehend'},
    {route: 'help-events-registrations', path: '/helpcenter/station/events/registrations', i18nPrefix: 'helpCenter.eventsRegistrations', section: 'Termine > Anmeldungen'},
    {route: 'help-events', path: '/helpcenter/station/events', i18nPrefix: 'helpCenter.eventsManage', section: 'Termine > Verwalten'},
    {route: 'help-event-new', path: '/helpcenter/station/events/new', i18nPrefix: 'helpCenter.eventEdit', section: 'Termine > Erstellen'},
    {route: 'help-event-edit', path: '/helpcenter/station/events/edit', i18nPrefix: 'helpCenter.eventEdit', section: 'Termine > Bearbeiten'},
    {route: 'help-event-detail', path: '/helpcenter/station/events/detail', i18nPrefix: 'helpCenter.eventDetail', section: 'Termine > Detail'},
    // Forms
    {route: 'help-forms-overview', path: '/helpcenter/station/forms', i18nPrefix: 'helpCenter.formsOverview', section: 'Formulare'},
    {route: 'help-forms-list', path: '/helpcenter/station/forms/list', i18nPrefix: 'helpCenter.forms', section: 'Formulare > Liste'},
    {route: 'help-forms-create', path: '/helpcenter/station/forms/create', i18nPrefix: 'helpCenter.formsBuilder', section: 'Formulare > Erstellen'},
    {route: 'help-forms-fill', path: '/helpcenter/station/forms/fill', i18nPrefix: 'helpCenter.formsFill', section: 'Formulare > Ausfüllen'},
    {route: 'help-forms-analytics', path: '/helpcenter/station/forms/analytics', i18nPrefix: 'helpCenter.formsAnalytics', section: 'Formulare > Auswertung'},
    // Lost and Found
    {route: 'help-lost-and-found', path: '/helpcenter/station/lost-and-found', i18nPrefix: 'helpCenter.lostAndFound', section: 'Fundbüro'},
    // Quiz
    {route: 'help-quiz-overview', path: '/helpcenter/station/quiz', i18nPrefix: 'helpCenter.quiz', section: 'Quiz'},
    {route: 'help-quiz-catalogs', path: '/helpcenter/station/quiz/catalogs', i18nPrefix: 'helpCenter.quiz.catalogs', section: 'Quiz > Kataloge'},
    {route: 'help-quiz-catalog-detail', path: '/helpcenter/station/quiz/catalog', i18nPrefix: 'helpCenter.quiz.catalogDetail', section: 'Quiz > Katalog Detail'},
    {route: 'help-quiz-ai', path: '/helpcenter/station/quiz/ai', i18nPrefix: 'helpCenter.quiz.ai', section: 'Quiz > KI-Generierung'},
    {route: 'help-quiz-tests', path: '/helpcenter/station/quiz/tests', i18nPrefix: 'helpCenter.quiz.tests', section: 'Quiz > Tests'},
    {route: 'help-quiz-test-detail', path: '/helpcenter/station/quiz/tests/details', i18nPrefix: 'helpCenter.quiz.testDetail', section: 'Quiz > Test Detail'},
    {route: 'help-quiz-training', path: '/helpcenter/station/quiz/training', i18nPrefix: 'helpCenter.quiz.training', section: 'Quiz > Training'},
    // Protocol
    {route: 'help-protocol', path: '/helpcenter/station/protocols', i18nPrefix: 'helpCenter.protocol', section: 'Prüfungsprotokolle'},
    // Knowledge Base
    {route: 'help-knowledge-base', path: '/helpcenter/station/knowledge', i18nPrefix: 'helpCenter.kb', section: 'Lernsammlung'},
    {route: 'help-knowledge-editor', path: '/helpcenter/station/knowledge/editor', i18nPrefix: 'helpCenter.kb.editor', section: 'Lernsammlung > Editor'},
    {route: 'help-knowledge-federated', path: '/helpcenter/station/knowledge/federated', i18nPrefix: 'helpCenter.federatedKb', section: 'Lernsammlung > Geteilte Inhalte'},
    // Admin
    {route: 'help-admin-overview', path: '/helpcenter/admin', i18nPrefix: 'helpCenter.admin', section: 'Administration'},
    {route: 'help-admin-stations', path: '/helpcenter/admin/stations', i18nPrefix: 'helpCenter.admin.stations', section: 'Administration > Wachen'},
    {route: 'help-admin-station-edit', path: '/helpcenter/admin/station-edit', i18nPrefix: 'helpCenter.admin.stationEdit', section: 'Administration > Wache bearbeiten'},
    {route: 'help-admin-station-applications', path: '/helpcenter/admin/applications', i18nPrefix: 'helpCenter.admin.applications', section: 'Administration > Anträge'},
    {route: 'help-admin-statistics', path: '/helpcenter/admin/statistics', i18nPrefix: 'helpCenter.admin.statistics', section: 'Administration > Statistiken'},
    {route: 'help-admin-settings', path: '/helpcenter/admin/settings', i18nPrefix: 'helpCenter.admin.settings', section: 'Administration > Einstellungen'},
]

/**
 * Recursively extracts all string values from a nested object.
 */
function flattenStrings(obj: unknown): string[] {
    if (typeof obj === 'string') return [obj]
    if (typeof obj !== 'object' || obj === null) return []
    const result: string[] = []
    for (const value of Object.values(obj)) {
        result.push(...flattenStrings(value))
    }
    return result
}

/**
 * Resolves a dotted key path against a nested object.
 */
function resolveKey(obj: Record<string, unknown>, keyPath: string): unknown {
    const parts = keyPath.split('.')
    let current: unknown = obj
    for (const part of parts) {
        if (typeof current !== 'object' || current === null) return undefined
        current = (current as Record<string, unknown>)[part]
    }
    return current
}

/**
 * Build the search index at module load time from the static locale file.
 * This ensures the index is always complete regardless of vue-i18n runtime state.
 */
const SEARCH_INDEX: HelpSearchEntry[] = (() => {
    const entries: HelpSearchEntry[] = []
    for (const page of HELP_PAGE_MAP) {
        const subtree = resolveKey(deDE as Record<string, unknown>, page.i18nPrefix)
        if (!subtree) continue

        const allStrings = flattenStrings(subtree)
        const text = allStrings.join(' ')

        let title = page.section
        if (typeof subtree === 'object' && subtree !== null) {
            const t = (subtree as Record<string, unknown>)['title'] ?? (subtree as Record<string, unknown>)['overviewTitle']
            if (typeof t === 'string') title = t
        }

        entries.push({
            route: page.route,
            path: page.path,
            title,
            section: page.section,
            text,
        })
    }
    return entries
})()

export function useHelpSearch() {
    const query = ref('')
    const debouncedQuery = ref('')
    let debounceTimer: ReturnType<typeof setTimeout> | null = null

    watch(query, (val) => {
        if (debounceTimer) clearTimeout(debounceTimer)
        debounceTimer = setTimeout(() => {
            debouncedQuery.value = val.trim()
        }, 250)
    })

    const results = computed<HelpSearchResult[]>(() => {
        const q = debouncedQuery.value.toLowerCase()
        if (!q || q.length < 2) return []

        const matched: HelpSearchResult[] = []
        for (const entry of SEARCH_INDEX) {
            const textLower = entry.text.toLowerCase()
            const matchIndex = textLower.indexOf(q)
            if (matchIndex === -1) continue

            const snippetRadius = 60
            const start = Math.max(0, matchIndex - snippetRadius)
            const end = Math.min(entry.text.length, matchIndex + q.length + snippetRadius)
            let snippet = ''
            if (start > 0) snippet += '...'
            snippet += entry.text.substring(start, end)
            if (end < entry.text.length) snippet += '...'

            matched.push({
                entry,
                snippet,
                matchStart: matchIndex - start + (start > 0 ? 3 : 0),
                matchEnd: matchIndex - start + (start > 0 ? 3 : 0) + q.length,
            })

            if (matched.length >= 10) break
        }
        return matched
    })

    const isSearching = computed(() => debouncedQuery.value.length >= 2)

    function clearSearch() {
        query.value = ''
        debouncedQuery.value = ''
    }

    return {
        query,
        results,
        isSearching,
        clearSearch,
    }
}
