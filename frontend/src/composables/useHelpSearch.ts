/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, watch} from 'vue'
import i18n from '@/i18n'
import {loadHelpcenterMessages} from '@/composables/useHelpcenterMessages'

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
export interface HelpPage {
    route: string
    path: string
    /**
     * Where the page's text lives under `helpCenter`. A list where one page renders more than one
     * block, which the events overview does: its manager half sits under a key of its own and would
     * otherwise be in no index at all.
     */
    i18nPrefix: string | string[]
    section: string
}

export const HELP_PAGE_MAP: HelpPage[] = [
    // Basics
    {route: 'help-welcome', path: '/helpcenter/station/basics', i18nPrefix: 'helpCenter.welcome', section: 'Grundlagen'},
    {route: 'help-basics-overview', path: '/helpcenter/station/basics/overview', i18nPrefix: 'helpCenter.basics.overview', section: 'Grundlagen > Was ist Ember?'},
    {route: 'help-basics-permissions', path: '/helpcenter/station/basics/permissions', i18nPrefix: 'helpCenter.basics.permissions', section: 'Grundlagen > Berechtigungen'},
    {route: 'help-basics-modules', path: '/helpcenter/station/basics/modules', i18nPrefix: 'helpCenter.basics.modules', section: 'Grundlagen > Module'},
    {route: 'help-basics-hosting', path: '/helpcenter/station/basics/hosting', i18nPrefix: 'helpCenter.basics.hosting', section: 'Grundlagen > Selbst betreiben'},
    {route: 'help-basics-hosting-configuration', path: '/helpcenter/station/basics/hosting/configuration', i18nPrefix: 'helpCenter.basics.configuration', section: 'Grundlagen > Selbst betreiben > Konfiguration'},
    {route: 'help-basics-federation', path: '/helpcenter/station/basics/federation', i18nPrefix: 'helpCenter.basics.federation', section: 'Grundlagen > Föderation'},
    // Setup wizard
    {route: 'help-setup-module-overview', path: '/helpcenter/station/setup', i18nPrefix: 'helpCenter.setup', section: 'Einrichtung'},
    {route: 'help-station-setup-welcome', path: '/helpcenter/station/setup/welcome', i18nPrefix: 'helpCenter.setupWelcome', section: 'Einrichtung > Willkommen'},
    {route: 'help-station-setup-address', path: '/helpcenter/station/setup/address', i18nPrefix: 'helpCenter.setupAddress', section: 'Einrichtung > Adresse'},
    {route: 'help-station-setup-modules', path: '/helpcenter/station/setup/modules', i18nPrefix: 'helpCenter.setupModules', section: 'Einrichtung > Module'},
    {route: 'help-station-setup-member-types', path: '/helpcenter/station/setup/member-types', i18nPrefix: 'helpCenter.setupMemberTypes', section: 'Einrichtung > Rollen'},
    {route: 'help-station-setup-groups', path: '/helpcenter/station/setup/groups', i18nPrefix: 'helpCenter.setupGroups', section: 'Einrichtung > Gruppen'},
    {route: 'help-station-setup-mail', path: '/helpcenter/station/setup/mail', i18nPrefix: 'helpCenter.setupMail', section: 'Einrichtung > Mailing'},
    {route: 'help-station-setup-branding', path: '/helpcenter/station/setup/branding', i18nPrefix: 'helpCenter.setupBranding', section: 'Einrichtung > Erscheinungsbild'},
    {route: 'help-station-setup-first-event', path: '/helpcenter/station/setup/first-event', i18nPrefix: 'helpCenter.setupFirstEvent', section: 'Einrichtung > Erstes Event'},
    {route: 'help-station-setup-kb-seed', path: '/helpcenter/station/setup/kb-seed', i18nPrefix: 'helpCenter.setupKbSeed', section: 'Einrichtung > Wiki'},
    {route: 'help-station-setup-federation', path: '/helpcenter/station/setup/federation', i18nPrefix: 'helpCenter.setupFederation', section: 'Einrichtung > Sichtbarkeit'},
    {route: 'help-station-setup-invites', path: '/helpcenter/station/setup/invites', i18nPrefix: 'helpCenter.setupInvites', section: 'Einrichtung > Einladungen'},
    {route: 'help-station-setup-finish', path: '/helpcenter/station/setup/finish', i18nPrefix: 'helpCenter.setupFinish', section: 'Einrichtung > Fertig'},
    // Dashboard
    {route: 'help-dashboard-module-overview', path: '/helpcenter/station/dashboard', i18nPrefix: 'helpCenter.dashboardOverview', section: 'Dashboard'},
    {route: 'help-dashboard-overview', path: '/helpcenter/station/dashboard/overview', i18nPrefix: 'helpCenter.dashboard', section: 'Dashboard > Übersicht'},
    {route: 'help-dashboard-statistics', path: '/helpcenter/station/dashboard/statistics', i18nPrefix: 'helpCenter.statistics', section: 'Dashboard > Statistiken'},
    // Requirements
    {route: 'help-station-requirements', path: '/helpcenter/station/requirements', i18nPrefix: 'helpCenter.requirements', section: 'Aufgaben'},
    // News
    {route: 'help-news-module-overview', path: '/helpcenter/station/news', i18nPrefix: 'helpCenter.newsOverview', section: 'Neuigkeiten'},
    {route: 'help-news-create', path: '/helpcenter/station/news/create', i18nPrefix: 'helpCenter.newsEdit', section: 'Neuigkeiten > Erstellen'},
    {route: 'help-news-edit', path: '/helpcenter/station/news/0/edit', i18nPrefix: 'helpCenter.newsEdit', section: 'Neuigkeiten > Bearbeiten'},
    {route: 'help-news-detail', path: '/helpcenter/station/news/0', i18nPrefix: 'helpCenter.newsDetail', section: 'Neuigkeiten > Beitrag lesen'},
    // Profile
    {route: 'help-profile-module-overview', path: '/helpcenter/station/profile', i18nPrefix: 'helpCenter.profileOverview', section: 'Profil'},
    {route: 'help-profile-absences', path: '/helpcenter/station/profile/absences', i18nPrefix: 'helpCenter.absences', section: 'Profil > Abwesenheit'},
    {route: 'help-profile-managed', path: '/helpcenter/station/profile/managed', i18nPrefix: 'helpCenter.managed', section: 'Profil > Verwaltete Profile'},
    {route: 'help-profile-inventory', path: '/helpcenter/station/profile/inventory', i18nPrefix: 'helpCenter.inventoryMy', section: 'Profil > Mein Inventar'},
    {route: 'help-profile-settings', path: '/helpcenter/station/profile/settings', i18nPrefix: 'helpCenter.settings', section: 'Profil > Einstellungen'},
    {route: 'help-profile-theme', path: '/helpcenter/station/profile/theme', i18nPrefix: 'helpCenter.themeUser', section: 'Profil > Erscheinungsbild'},
    // Station management
    {route: 'help-manage-module-overview', path: '/helpcenter/station/manage', i18nPrefix: 'helpCenter.manageOverview', section: 'Verwaltung'},
    {route: 'help-station-attendance-config', path: '/helpcenter/station/attendance/config', i18nPrefix: 'helpCenter.attendanceConfig', section: 'Anwesenheit > Konfiguration'},
    {route: 'help-station-attendance-config-edit', path: '/helpcenter/station/attendance/config/edit', i18nPrefix: 'helpCenter.attendanceConfigEdit', section: 'Anwesenheit > Vorlage bearbeiten'},
    {route: 'help-station-members-config', path: '/helpcenter/station/members/config', i18nPrefix: 'helpCenter.membersConfig', section: 'Mitglieder > Konfiguration'},
    {route: 'help-station-federation', path: '/helpcenter/station/federate', i18nPrefix: 'helpCenter.federation', section: 'Verwaltung > Föderation'},
    {route: 'help-station-federation-partner', path: '/helpcenter/station/federate/0', i18nPrefix: 'helpCenter.federationPartner', section: 'Verwaltung > Föderationspartner'},
    // Members
    {route: 'help-members-module-overview', path: '/helpcenter/station/members', i18nPrefix: 'helpCenter.membersOverview', section: 'Mitglieder'},
    {route: 'help-members-create', path: '/helpcenter/station/members/create', i18nPrefix: 'helpCenter.membersCreate', section: 'Mitglieder > Anlegen'},
    {route: 'help-members-list', path: '/helpcenter/station/members/list', i18nPrefix: 'helpCenter.membersList', section: 'Mitglieder > Liste'},
    {route: 'help-members-import', path: '/helpcenter/station/members/import', i18nPrefix: 'helpCenter.membersImport', section: 'Mitglieder > Import'},
    {route: 'help-members-import-team', path: '/helpcenter/station/members/import-team', i18nPrefix: 'helpCenter.membersImportTeam', section: 'Mitglieder > Team-Import'},
    {route: 'help-members-detail', path: '/helpcenter/station/members/detail', i18nPrefix: 'helpCenter.membersDetail', section: 'Mitglieder > Detail'},
    {route: 'help-members-edit', path: '/helpcenter/station/members/edit', i18nPrefix: 'helpCenter.membersEdit', section: 'Mitglieder > Bearbeiten'},
    {route: 'help-members-groups', path: '/helpcenter/station/members/groups', i18nPrefix: 'helpCenter.membersGroups', section: 'Mitglieder > Gruppen'},
    {route: 'help-members-type-permissions', path: '/helpcenter/station/members/type-permissions', i18nPrefix: 'helpCenter.typePermissions', section: 'Mitglieder > Typberechtigungen'},
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
    {route: 'help-inventory-item-detail', path: '/helpcenter/station/inventory/item/0', i18nPrefix: 'helpCenter.itemDetail', section: 'Inventar > Gegenstand'},
    {route: 'help-inventory-manage', path: '/helpcenter/station/inventory/manage', i18nPrefix: 'helpCenter.inventoryManage', section: 'Inventar > Verwalten'},
    {route: 'help-inventory-requirements', path: '/helpcenter/station/inventory/requirements', i18nPrefix: 'helpCenter.inventoryRequirements', section: 'Inventar > Anforderungen'},
    {route: 'help-inventory-checks', path: '/helpcenter/station/inventory/checks', i18nPrefix: 'helpCenter.inventoryChecks', section: 'Inventar > Prüfung'},
    {route: 'help-inventory-check-member-overview', path: '/helpcenter/station/inventory/checks/member', i18nPrefix: 'helpCenter.inventoryChecks', section: 'Inventar > Prüfung > Mitglieder auswählen'},
    {route: 'help-inventory-check-member-detail', path: '/helpcenter/station/inventory/checks/0', i18nPrefix: 'helpCenter.inventoryCheckMember', section: 'Inventar > Prüfung > Mitglied'},
    {route: 'help-inventory-check-result', path: '/helpcenter/station/inventory/checks/result', i18nPrefix: 'helpCenter.inventoryCheckResult', section: 'Inventar > Prüfung > Ergebnis'},
    {route: 'help-inventory-procurement', path: '/helpcenter/station/inventory/procurement', i18nPrefix: 'helpCenter.inventoryProcurement', section: 'Inventar > Beschaffung'},
    {route: 'help-inventory-lending', path: '/helpcenter/station/inventory/lending', i18nPrefix: 'helpCenter.inventoryLending', section: 'Inventar > Ausleihe'},
    {route: 'help-inventory-lending-blocks', path: '/helpcenter/station/inventory/lending/blocks', i18nPrefix: 'helpCenter.inventoryLendingBlocks', section: 'Inventar > Ausleihe > Sperrzeiten'},
    {route: 'help-inventory-lending-browse', path: '/helpcenter/station/inventory/lending/browse', i18nPrefix: 'helpCenter.inventoryLendingBrowse', section: 'Inventar > Ausleihe > Durchsuchen'},
    {route: 'help-inventory-lending-request', path: '/helpcenter/station/inventory/lending/request/0', i18nPrefix: 'helpCenter.inventoryLendingRequest', section: 'Inventar > Ausleihe > Anfrage'},
    {route: 'help-inventory-lending-create', path: '/helpcenter/station/inventory/lending/request/new', i18nPrefix: 'helpCenter.inventoryLendingCreate', section: 'Inventar > Ausleihe > Anfrage erstellen'},
    // Attendance
    {route: 'help-attendance-module-overview', path: '/helpcenter/station/attendance', i18nPrefix: 'helpCenter.attendanceOverview', section: 'Anwesenheit'},
    {route: 'help-attendance-new', path: '/helpcenter/station/attendance/new', i18nPrefix: 'helpCenter.attendanceNew', section: 'Anwesenheit > Neue Anwesenheit'},
    {route: 'help-attendance-session', path: '/helpcenter/station/attendance/session', i18nPrefix: 'helpCenter.attendanceSession', section: 'Anwesenheit > Sitzung'},
    {route: 'help-attendance-past', path: '/helpcenter/station/attendance/past', i18nPrefix: 'helpCenter.attendancePast', section: 'Anwesenheit > Vergangene'},
    {route: 'help-attendance-report', path: '/helpcenter/station/attendance/report', i18nPrefix: 'helpCenter.attendanceReport', section: 'Anwesenheit > Bericht'},
    // Events
    {route: 'help-events-module-overview', path: '/helpcenter/station/events', i18nPrefix: ['helpCenter.eventsOverview', 'helpCenter.eventsManage'], section: 'Termine'},
    {route: 'help-events-upcoming', path: '/helpcenter/station/events/upcoming', i18nPrefix: 'helpCenter.eventsUpcoming', section: 'Termine > Anstehend'},
    {route: 'help-events-registrations', path: '/helpcenter/station/events/registrations', i18nPrefix: 'helpCenter.eventsRegistrations', section: 'Termine > Anmeldungen'},
    {route: 'help-event-new', path: '/helpcenter/station/events/new', i18nPrefix: 'helpCenter.eventEdit', section: 'Termine > Erstellen'},
    {route: 'help-event-edit', path: '/helpcenter/station/events/edit', i18nPrefix: 'helpCenter.eventEdit', section: 'Termine > Bearbeiten'},
    {route: 'help-event-detail', path: '/helpcenter/station/events/0', i18nPrefix: 'helpCenter.eventDetail', section: 'Termine > Detail'},
    {route: 'help-event-detail-date', path: '/helpcenter/station/events/0/2026-05-04', i18nPrefix: 'helpCenter.eventDetail', section: 'Termine > Einzeltermin einer Reihe'},
    {route: 'help-event-templates', path: '/helpcenter/station/events/templates', i18nPrefix: 'helpCenter.eventTemplates', section: 'Termine > Vorlagen'},
    {route: 'help-event-template-edit', path: '/helpcenter/station/events/templates/0', i18nPrefix: 'helpCenter.eventTemplateEdit', section: 'Termine > Vorlage bearbeiten'},
    // Forms
    {route: 'help-forms-module-overview', path: '/helpcenter/station/forms', i18nPrefix: 'helpCenter.formsOverview', section: 'Formulare'},
    {route: 'help-forms-create', path: '/helpcenter/station/forms/create', i18nPrefix: 'helpCenter.formsBuilder', section: 'Formulare > Erstellen'},
    {route: 'help-forms-edit', path: '/helpcenter/station/forms/0/edit', i18nPrefix: 'helpCenter.formsEdit', section: 'Formulare > Bearbeiten'},
    {route: 'help-forms-fill', path: '/helpcenter/station/forms/0/fill', i18nPrefix: 'helpCenter.formsFill', section: 'Formulare > Ausfüllen'},
    {route: 'help-forms-analytics', path: '/helpcenter/station/forms/0/analytics', i18nPrefix: 'helpCenter.formsAnalytics', section: 'Formulare > Auswertung'},
    // Lost and Found
    {route: 'help-lost-and-found', path: '/helpcenter/station/lost-and-found', i18nPrefix: 'helpCenter.lostAndFound', section: 'Fundbüro'},
    // Quiz
    {route: 'help-quiz-module-overview', path: '/helpcenter/station/quiz', i18nPrefix: 'helpCenter.quiz', section: 'Quiz'},
    {route: 'help-quiz-catalogs', path: '/helpcenter/station/quiz/catalogs', i18nPrefix: 'helpCenter.quiz', section: 'Quiz > Kataloge'},
    {route: 'help-quiz-catalog-detail', path: '/helpcenter/station/quiz/catalogs/0', i18nPrefix: 'helpCenter.quiz', section: 'Quiz > Katalog Detail'},
    {route: 'help-quiz-ai', path: '/helpcenter/station/quiz/ai', i18nPrefix: 'helpCenter.quiz.ai', section: 'Quiz > KI-Generierung'},
    {route: 'help-quiz-tests', path: '/helpcenter/station/quiz/tests', i18nPrefix: 'helpCenter.quiz', section: 'Quiz > Tests'},
    {route: 'help-quiz-test-detail', path: '/helpcenter/station/quiz/tests/0', i18nPrefix: 'helpCenter.quiz', section: 'Quiz > Test Detail'},
    {route: 'help-quiz-training', path: '/helpcenter/station/quiz/training', i18nPrefix: 'helpCenter.quiz', section: 'Quiz > Training'},
    {route: 'help-quiz-catalog-generate', path: '/helpcenter/station/quiz/catalogs/0/generate', i18nPrefix: 'helpCenter.quizCatalogGenerate', section: 'Quiz > Fragen generieren'},
    {route: 'help-quiz-catalog-import', path: '/helpcenter/station/quiz/catalogs/0/import', i18nPrefix: 'helpCenter.quizCatalogImport', section: 'Quiz > Fragen importieren'},
    {route: 'help-quiz-catalog-mc-fill', path: '/helpcenter/station/quiz/catalogs/0/mc-fill', i18nPrefix: 'helpCenter.quizCatalogMcFill', section: 'Quiz > Falsche Antworten ergänzen'},
    {route: 'help-quiz-test-create', path: '/helpcenter/station/quiz/tests/create', i18nPrefix: 'helpCenter.quizTestCreate', section: 'Quiz > Test erstellen'},
    {route: 'help-quiz-test-edit', path: '/helpcenter/station/quiz/tests/0/edit', i18nPrefix: 'helpCenter.quizTestEdit', section: 'Quiz > Test bearbeiten'},
    {route: 'help-quiz-test-take', path: '/helpcenter/station/quiz/tests/0/take', i18nPrefix: 'helpCenter.quizTestTake', section: 'Quiz > Test schreiben'},
    {route: 'help-quiz-test-evaluate', path: '/helpcenter/station/quiz/tests/0/evaluate/0', i18nPrefix: 'helpCenter.quizTestEvaluate', section: 'Quiz > Test auswerten'},
    // Protocol
    {route: 'help-protocol', path: '/helpcenter/station/protocols', i18nPrefix: 'helpCenter.protocol', section: 'Prüfungsprotokolle'},
    {route: 'help-protocol-detail', path: '/helpcenter/station/protocols/0', i18nPrefix: 'helpCenter.protocolDetail', section: 'Prüfungsprotokolle > Protokoll-Details'},
    {route: 'help-protocol-run-list', path: '/helpcenter/station/protocols/runs', i18nPrefix: 'helpCenter.protocolRunList', section: 'Prüfungsprotokolle > Prüfungsläufe'},
    {route: 'help-protocol-run-detail', path: '/helpcenter/station/protocols/runs/0', i18nPrefix: 'helpCenter.protocolRunDetail', section: 'Prüfungsprotokolle > Lauf-Details'},
    {route: 'help-protocol-evaluation', path: '/helpcenter/station/protocols/runs/0/evaluation', i18nPrefix: 'helpCenter.protocolEvaluation', section: 'Prüfungsprotokolle > Auswertung'},
    {route: 'help-protocol-grade', path: '/helpcenter/station/protocols/runs/0/grade/0', i18nPrefix: 'helpCenter.protocolGrading', section: 'Prüfungsprotokolle > Mitglied prüfen'},
    // Boards
    {route: 'help-board-overview', path: '/helpcenter/station/boards', i18nPrefix: 'helpCenter.boardOverview', section: 'Boards'},
    {route: 'help-board-manage', path: '/helpcenter/station/boards/manage', i18nPrefix: 'helpCenter.boardManage', section: 'Boards > Verwalten'},
    {route: 'help-board-view', path: '/helpcenter/station/boards/BOARD', i18nPrefix: 'helpCenter.boardView', section: 'Boards > Board-Ansicht'},
    {route: 'help-ticket-create', path: '/helpcenter/station/boards/BOARD/tickets/new', i18nPrefix: 'helpCenter.ticketCreate', section: 'Boards > Ticket erstellen'},
    {route: 'help-ticket-detail', path: '/helpcenter/station/boards/BOARD/tickets/1', i18nPrefix: 'helpCenter.ticketDetail', section: 'Boards > Ticket-Details'},
    {route: 'help-board-backlog', path: '/helpcenter/station/boards/BOARD/backlog', i18nPrefix: 'helpCenter.backlog', section: 'Boards > Backlog'},
    {route: 'help-board-archived', path: '/helpcenter/station/boards/BOARD/archived', i18nPrefix: 'helpCenter.archived', section: 'Boards > Archiv'},
    {route: 'help-board-settings', path: '/helpcenter/station/boards/BOARD/settings', i18nPrefix: 'helpCenter.boardSettings', section: 'Boards > Einstellungen'},
    {route: 'help-federated-boards', path: '/helpcenter/station/federation/boards', i18nPrefix: 'helpCenter.federatedBoards', section: 'Boards > Föderierte Boards'},
    {route: 'help-federated-board-view', path: '/helpcenter/station/federation/boards/0/BOARD', i18nPrefix: 'helpCenter.federatedBoardView', section: 'Boards > Board einer Partnerwache'},
    {route: 'help-federated-ticket-detail', path: '/helpcenter/station/federation/boards/0/BOARD/tickets/1', i18nPrefix: 'helpCenter.federatedTicketDetail', section: 'Boards > Ticket einer Partnerwache'},
    {route: 'help-federated-event-detail', path: '/helpcenter/station/federation/events/0/1', i18nPrefix: 'helpCenter.federatedEventDetail', section: 'Termine > Termin einer Partnerwache'},
    {route: 'help-federated-news-detail', path: '/helpcenter/station/federation/news/0/1', i18nPrefix: 'helpCenter.federatedNewsDetail', section: 'Neuigkeiten > Beitrag einer Partnerwache'},
    // Procedures
    {route: 'help-procedure-module-overview', path: '/helpcenter/station/procedures/overview', i18nPrefix: 'helpCenter.procedureOverview', section: 'Abläufe'},
    {route: 'help-procedure-list', path: '/helpcenter/station/procedures', i18nPrefix: 'helpCenter.procedureList', section: 'Abläufe > Liste'},
    {route: 'help-procedure-create', path: '/helpcenter/station/procedures/create', i18nPrefix: 'helpCenter.procedureCreate', section: 'Abläufe > Anlegen'},
    {route: 'help-procedure-detail', path: '/helpcenter/station/procedures/0', i18nPrefix: 'helpCenter.procedureDetail', section: 'Abläufe > Abarbeiten'},
    {route: 'help-procedure-template-list', path: '/helpcenter/station/procedures/templates', i18nPrefix: 'helpCenter.procedureTemplates', section: 'Abläufe > Vorlagen'},
    {route: 'help-procedure-template-edit', path: '/helpcenter/station/procedures/templates/0', i18nPrefix: 'helpCenter.procedureTemplateEdit', section: 'Abläufe > Vorlage bearbeiten'},
    // Knowledge Base
    {route: 'help-knowledge-module-overview', path: '/helpcenter/station/knowledge', i18nPrefix: 'helpCenter.knowledgeModuleOverview', section: 'Wiki'},
    {route: 'help-knowledge-base', path: '/helpcenter/station/knowledge/browse', i18nPrefix: 'helpCenter.kb', section: 'Wiki > Durchsuchen'},
    {route: 'help-knowledge-editor', path: '/helpcenter/station/knowledge/editor', i18nPrefix: 'helpCenter.kb.editor', section: 'Wiki > Editor'},
    {route: 'help-knowledge-federated', path: '/helpcenter/station/knowledge/federated', i18nPrefix: 'helpCenter.federatedKb', section: 'Wiki > Geteilte Inhalte'},
    {route: 'help-kb-file', path: '/helpcenter/station/knowledge/file/0', i18nPrefix: 'helpCenter.kbFileView', section: 'Wiki > Datei anzeigen'},
    {route: 'help-kb-versions', path: '/helpcenter/station/knowledge/file/0/versions', i18nPrefix: 'helpCenter.kbVersions', section: 'Wiki > Versionsverlauf'},
    // Public Pages
    {route: 'help-pages', path: '/helpcenter/station/pages', i18nPrefix: 'helpCenter.pages', section: 'Öffentliche Seiten'},
    // Admin
    {route: 'help-admin-module-overview', path: '/helpcenter/admin', i18nPrefix: 'helpCenter.adminOverview', section: 'Administration'},
    {route: 'help-admin-stations-module-overview', path: '/helpcenter/admin/stations/overview', i18nPrefix: 'helpCenter.adminStationsOverview', section: 'Administration > Wachen'},
    {route: 'help-admin-stations', path: '/helpcenter/admin/stations', i18nPrefix: 'helpCenter.adminStations', section: 'Administration > Wachen > Liste'},
    {route: 'help-admin-station-edit', path: '/helpcenter/admin/station-edit', i18nPrefix: 'helpCenter.adminStationEdit', section: 'Administration > Wache bearbeiten'},
    {route: 'help-admin-station-applications', path: '/helpcenter/admin/applications', i18nPrefix: 'helpCenter.adminApplications', section: 'Administration > Anträge'},
    {route: 'help-admin-statistics', path: '/helpcenter/admin/statistics', i18nPrefix: 'helpCenter.adminStatistics', section: 'Administration > Statistiken'},
    {route: 'help-admin-settings', path: '/helpcenter/admin/settings', i18nPrefix: 'helpCenter.adminSettings', section: 'Administration > Einstellungen'},
    {route: 'help-admin-api-status', path: '/helpcenter/admin/monitoring/api-status', i18nPrefix: 'helpCenter.adminApiStatus', section: 'Administration > Monitoring > API-Status'},
    {route: 'help-admin-feed-metrics', path: '/helpcenter/admin/monitoring/feed-metrics', i18nPrefix: 'helpCenter.adminFeedMetrics', section: 'Administration > Monitoring > Feed-Telemetrie'},
    {route: 'help-admin-traffic', path: '/helpcenter/admin/monitoring/traffic', i18nPrefix: 'helpCenter.adminTraffic', section: 'Administration > Monitoring > Traffic'},
    {route: 'help-station-traffic', path: '/helpcenter/station/manage/traffic', i18nPrefix: 'helpCenter.stationTraffic', section: 'Wache > Verwalten > Traffic'},
    {route: 'help-station-insights', path: '/helpcenter/station/manage/insights', i18nPrefix: 'helpCenter.stationInsights', section: 'Wache > Verwalten > Seiten-Statistik'},
    {route: 'help-admin-problems', path: '/helpcenter/admin/monitoring/problems', i18nPrefix: 'helpCenter.adminProblems', section: 'Administration > Monitoring > Problemprotokoll'},
    {route: 'help-admin-problem-reports', path: '/helpcenter/admin/monitoring/problem-reports', i18nPrefix: 'helpCenter.adminProblemReports', section: 'Administration > Monitoring > Problemmeldungen'},
    {route: 'help-admin-security', path: '/helpcenter/admin/settings/security', i18nPrefix: 'helpCenter.adminSecurity', section: 'Administration > Einstellungen > Sicherheit'},
    {route: 'help-admin-security-tokens', path: '/helpcenter/admin/settings/security/tokens', i18nPrefix: 'helpCenter.adminSecurityTokens', section: 'Administration > Einstellungen > Sicherheit > Tokens'},
    {route: 'help-admin-security-hibp', path: '/helpcenter/admin/settings/security/hibp', i18nPrefix: 'helpCenter.adminSecurityHibp', section: 'Administration > Einstellungen > Sicherheit > HIBP'},
    {route: 'help-admin-security-two-factor', path: '/helpcenter/admin/settings/security/two-factor', i18nPrefix: 'helpCenter.adminSecurityTwoFactor', section: 'Administration > Einstellungen > Sicherheit > 2FA'},
    {route: 'help-admin-two-factor', path: '/helpcenter/admin/2fa', i18nPrefix: 'helpCenter.adminTwoFactor', section: 'Administration > 2FA'},
    {route: 'help-station-security', path: '/helpcenter/station/manage/security', i18nPrefix: 'helpCenter.stationSecurity', section: 'Wache > Verwalten > Sicherheit'},
    {route: 'help-cluster-overview', path: '/helpcenter/cluster', i18nPrefix: 'helpCenter.clusterOverview', section: 'Verband'},
    {route: 'help-cluster-settings', path: '/helpcenter/cluster/settings', i18nPrefix: 'helpCenter.clusterSettings', section: 'Verband > Einstellungen'},
    {route: 'help-cluster-stations', path: '/helpcenter/cluster/stations', i18nPrefix: 'helpCenter.clusterStations', section: 'Verband > Wachen'},
    {route: 'help-cluster-station-groups', path: '/helpcenter/cluster/stations/groups', i18nPrefix: 'helpCenter.clusterStationGroups', section: 'Verband > Wachen > Wachgruppen'},
    {route: 'help-cluster-applications', path: '/helpcenter/cluster/applications', i18nPrefix: 'helpCenter.clusterApplications', section: 'Verband > Wachen > Beitrittsanfragen'},
    {route: 'help-cluster-team', path: '/helpcenter/cluster/team', i18nPrefix: 'helpCenter.clusterMembers', section: 'Verband > Verbandsteam'},
    {route: 'help-cluster-team-groups', path: '/helpcenter/cluster/team/groups', i18nPrefix: 'helpCenter.clusterMemberGroups', section: 'Verband > Verbandsteam > Gruppen'},
    {route: 'help-cluster-members', path: '/helpcenter/cluster/members', i18nPrefix: 'helpCenter.clusterMemberManagement', section: 'Verband > Mitglieder'},
    {route: 'help-cluster-member-detail', path: '/helpcenter/cluster/members/0', i18nPrefix: 'helpCenter.clusterMemberDetail', section: 'Verband > Mitglieder > Mitglied'},
    {route: 'help-cluster-fields', path: '/helpcenter/cluster/members/fields', i18nPrefix: 'helpCenter.clusterFields', section: 'Verband > Mitglieder > Profilfelder'},
    {route: 'help-cluster-modules', path: '/helpcenter/cluster/modules', i18nPrefix: 'helpCenter.clusterModules', section: 'Verband > Vorgaben > Module'},
    {route: 'help-cluster-look-and-feel', path: '/helpcenter/cluster/look-and-feel', i18nPrefix: 'helpCenter.clusterLookAndFeel', section: 'Verband > Vorgaben > Erscheinungsbild'},
    {route: 'help-cluster-storage', path: '/helpcenter/cluster/storage', i18nPrefix: 'helpCenter.clusterStorage', section: 'Verband > Vorgaben > Speicherplatz'},
    {route: 'help-cluster-storage-backend', path: '/helpcenter/cluster/storage/backend', i18nPrefix: 'helpCenter.clusterStorageBackend', section: 'Verband > Vorgaben > Eigener Speicher'},
    {route: 'help-cluster-knowledge', path: '/helpcenter/cluster/knowledge', i18nPrefix: 'helpCenter.clusterKnowledge', section: 'Verband > Wiki'},
    {route: 'help-cluster-news', path: '/helpcenter/cluster/news', i18nPrefix: 'helpCenter.clusterNews', section: 'Verband > Neuigkeiten'},
    {route: 'help-cluster-events', path: '/helpcenter/cluster/events', i18nPrefix: 'helpCenter.clusterEvents', section: 'Verband > Termine'},
    {route: 'help-cluster-inventory', path: '/helpcenter/cluster/inventory', i18nPrefix: 'helpCenter.clusterInventory', section: 'Verband > Material'},
    {route: 'help-cluster-inventory-out', path: '/helpcenter/cluster/inventory/out', i18nPrefix: 'helpCenter.clusterInventoryOut', section: 'Verband > Material > Unterwegs'},
    {route: 'help-cluster-movements', path: '/helpcenter/cluster/inventory/movements', i18nPrefix: 'helpCenter.clusterMovements', section: 'Verband > Material > Offene Schritte'},
    {route: 'help-cluster-inventory-movement', path: '/helpcenter/cluster/inventory/movement/0', i18nPrefix: 'helpCenter.clusterMovementDetail', section: 'Verband > Material > Bewegung'},
    {route: 'help-cluster-inventory-detail', path: '/helpcenter/cluster/inventory/detail/0', i18nPrefix: 'helpCenter.clusterInventoryDetail', section: 'Verband > Material > Inventar'},
    {route: 'help-cluster-inventory-edit', path: '/helpcenter/cluster/inventory/edit/0', i18nPrefix: 'helpCenter.clusterInventoryEdit', section: 'Verband > Material > Inventar bearbeiten'},
    {route: 'help-cluster-inventory-item', path: '/helpcenter/cluster/inventory/item/0', i18nPrefix: 'helpCenter.clusterInventoryItem', section: 'Verband > Material > Gegenstand'},
    {route: 'help-cluster-inventory-storage', path: '/helpcenter/cluster/inventory/storage', i18nPrefix: 'helpCenter.clusterInventoryStorage', section: 'Verband > Material > Lagerorte'},
    {route: 'help-cluster-inventory-container', path: '/helpcenter/cluster/inventory/storage/0', i18nPrefix: 'helpCenter.clusterInventoryContainer', section: 'Verband > Material > Lagerort'},
    {route: 'help-cluster-inventory-checks', path: '/helpcenter/cluster/inventory/checks/container', i18nPrefix: 'helpCenter.clusterInventoryChecks', section: 'Verband > Material > Prüfungen'},
    {route: 'help-cluster-inventory-check-walk', path: '/helpcenter/cluster/inventory/checks/container/0', i18nPrefix: 'helpCenter.clusterInventoryCheckWalk', section: 'Verband > Material > Prüfung durchgehen'},
    {route: 'help-cluster-inventory-dispatch', path: '/helpcenter/cluster/inventory/dispatch', i18nPrefix: 'helpCenter.clusterInventoryDispatch', section: 'Verband > Material > Ausgabe'},
    {route: 'help-cluster-inventory-procurement', path: '/helpcenter/cluster/inventory/procurement', i18nPrefix: 'helpCenter.clusterInventoryProcurement', section: 'Verband > Material > Beschaffung'},
    {route: 'help-cluster-inventory-requirements', path: '/helpcenter/cluster/inventory/requirements', i18nPrefix: 'helpCenter.clusterInventoryRequirements', section: 'Verband > Material > Vorgaben'},
    {route: 'help-cluster-inventory-settings', path: '/helpcenter/cluster/inventory/settings', i18nPrefix: 'helpCenter.clusterInventorySettings', section: 'Verband > Material > Einstellungen'},
    {route: 'help-cluster-inventory-statistics', path: '/helpcenter/cluster/inventory/statistics', i18nPrefix: 'helpCenter.clusterInventoryStatistics', section: 'Verband > Material > Zahlen'},
    {route: 'help-admin-clusters', path: '/helpcenter/admin/clusters', i18nPrefix: 'helpCenter.adminClusters', section: 'Administration > Verbände'},
    {route: 'help-admin-news', path: '/helpcenter/admin/news', i18nPrefix: 'helpCenter.adminSystemNews', section: 'Administration > Systemnachrichten'},
    {route: 'help-admin-station-import', path: '/helpcenter/admin/stations/import/0', i18nPrefix: 'helpCenter.adminStationImport', section: 'Administration > Wachen > Import'},
    {route: 'help-admin-legal', path: '/helpcenter/admin/settings/legal', i18nPrefix: 'helpCenter.adminLegal', section: 'Administration > Einstellungen > Rechtliches'},
    {route: 'help-admin-mailing', path: '/helpcenter/admin/settings/mailing', i18nPrefix: 'helpCenter.adminMailing', section: 'Administration > Einstellungen > Mailing'},
    {route: 'help-admin-data-tracking', path: '/helpcenter/admin/dev/data-tracking', i18nPrefix: 'helpCenter.adminDataTracking', section: 'Administration > Entwicklung > Datenerfassung'},
    {route: 'help-admin-discovery', path: '/helpcenter/admin/monitoring/discovery', i18nPrefix: 'helpCenter.adminDiscovery', section: 'Administration > Monitoring > Verzeichnis'},
    {route: 'help-admin-log', path: '/helpcenter/admin/monitoring/log', i18nPrefix: 'helpCenter.adminLog', section: 'Administration > Monitoring > Protokoll'},
    {route: 'help-admin-mail-log', path: '/helpcenter/admin/monitoring/mail-log', i18nPrefix: 'helpCenter.adminMailLog', section: 'Administration > Monitoring > Mail-Protokoll'},
    {route: 'help-admin-maps', path: '/helpcenter/admin/monitoring/maps', i18nPrefix: 'helpCenter.adminMaps', section: 'Administration > Monitoring > Karten'},
    {route: 'help-admin-storage', path: '/helpcenter/admin/monitoring/storage', i18nPrefix: 'helpCenter.adminStorage', section: 'Administration > Monitoring > Speicherplatz'},
    {route: 'help-admin-storage-backend', path: '/helpcenter/admin/monitoring/storage/backend', i18nPrefix: 'helpCenter.adminStorageBackend', section: 'Administration > Monitoring > Speicher-Backend'},
    {route: 'help-admin-storage-audit', path: '/helpcenter/admin/monitoring/storage/audit', i18nPrefix: 'helpCenter.adminStorageAudit', section: 'Administration > Monitoring > Speicher-Prüfung'},
    {route: 'help-admin-api-status-detail', path: '/helpcenter/admin/monitoring/api-status/detail', i18nPrefix: 'helpCenter.adminApiStatusDetail', section: 'Administration > Monitoring > API-Status'},
    {route: 'help-cluster-news-create', path: '/helpcenter/cluster/news/create', i18nPrefix: 'helpCenter.newsEdit', section: 'Verband > Neuigkeiten > Erstellen'},
    {route: 'help-cluster-news-edit', path: '/helpcenter/cluster/news/0/edit', i18nPrefix: 'helpCenter.newsEdit', section: 'Verband > Neuigkeiten > Bearbeiten'},
    {route: 'help-cluster-news-detail', path: '/helpcenter/cluster/news/0', i18nPrefix: 'helpCenter.newsDetail', section: 'Verband > Neuigkeiten > Beitrag lesen'},
    {route: 'help-cluster-kb-file', path: '/helpcenter/cluster/knowledge/file/0', i18nPrefix: 'helpCenter.kbFileView', section: 'Verband > Wiki > Artikel'},
    {route: 'help-cluster-kb-versions', path: '/helpcenter/cluster/knowledge/file/0/versions', i18nPrefix: 'helpCenter.kbVersions', section: 'Verband > Wiki > Versionen'},
    {route: 'help-cluster-event-batch', path: '/helpcenter/cluster/events/batch', i18nPrefix: 'helpCenter.batchCreate', section: 'Verband > Termine > Serie'},
    {route: 'help-cluster-event-categories', path: '/helpcenter/cluster/events/categories', i18nPrefix: 'helpCenter.categories', section: 'Verband > Termine > Kategorien'},
    {route: 'help-cluster-event-new', path: '/helpcenter/cluster/events/new', i18nPrefix: 'helpCenter.eventEdit', section: 'Verband > Termine > Anlegen'},
    {route: 'help-cluster-event-detail-date', path: '/helpcenter/cluster/events/0/0', i18nPrefix: 'helpCenter.eventDetail', section: 'Verband > Termine > Termin an einem Tag'},
    {route: 'help-cluster-event-edit', path: '/helpcenter/cluster/events/0/edit', i18nPrefix: 'helpCenter.eventEdit', section: 'Verband > Termine > Bearbeiten'},
    {route: 'help-cluster-event-detail', path: '/helpcenter/cluster/events/0', i18nPrefix: 'helpCenter.eventDetail', section: 'Verband > Termine > Termin'},
    {route: 'help-station-media', path: '/helpcenter/station/media', i18nPrefix: 'helpCenter.media', section: 'Wache > Mediathek'},
    {route: 'help-station-discovery-network', path: '/helpcenter/station/discovery', i18nPrefix: 'helpCenter.stationDiscoveryNetwork', section: 'Wache > Verzeichnis'},
    {route: 'help-page-editor', path: '/helpcenter/station/pages/0', i18nPrefix: 'helpCenter.pageEditor', section: 'Seiten > Seiteneditor'},
    {route: 'help-pages-forms', path: '/helpcenter/station/pages/forms', i18nPrefix: 'helpCenter.pageForms', section: 'Seiten > Formulare'},
    {route: 'help-pages-polls', path: '/helpcenter/station/pages/polls', i18nPrefix: 'helpCenter.pagePolls', section: 'Seiten > Umfragen'},
    {route: 'help-pages-polls-analytics', path: '/helpcenter/station/pages/polls/forms/0/analytics', i18nPrefix: 'helpCenter.formsAnalytics', section: 'Seiten > Umfragen > Auswertung'},
    {route: 'help-pages-forms-submissions', path: '/helpcenter/station/pages/forms/0/submissions', i18nPrefix: 'helpCenter.pagesFormsSubmissions', section: 'Seiten > Formulare > Einsendungen'},
    {route: 'help-station-manage-cluster', path: '/helpcenter/station/manage/cluster', i18nPrefix: 'helpCenter.stationCluster', section: 'Wache > Verwalten > Verband'},
    {route: 'help-station-import', path: '/helpcenter/station/manage/import', i18nPrefix: 'helpCenter.import', section: 'Wache > Verwalten > Import'},
    {route: 'help-station-modules', path: '/helpcenter/station/manage/modules', i18nPrefix: 'helpCenter.modules', section: 'Wache > Verwalten > Module'},
    {route: 'help-station-theme', path: '/helpcenter/station/manage/theme', i18nPrefix: 'helpCenter.themeManage', section: 'Wache > Verwalten > Erscheinungsbild'},
    {route: 'help-station-mailing-vendor', path: '/helpcenter/station/manage/mailing/smtp', i18nPrefix: 'helpCenter.mailVendor', section: 'Wache > Verwalten > Mailing > Anbieter'},
    {route: 'help-station-mailing', path: '/helpcenter/station/manage/mailing', i18nPrefix: 'helpCenter.mailConfig', section: 'Wache > Verwalten > Mailing'},
    {route: 'help-station-storage', path: '/helpcenter/station/manage/storage', i18nPrefix: 'helpCenter.stationStorage', section: 'Wache > Verwalten > Speicherplatz'},
    {route: 'help-station-storage-backend', path: '/helpcenter/station/manage/storage/backend', i18nPrefix: 'helpCenter.stationStorageBackend', section: 'Wache > Verwalten > Eigener Speicher'},
    {route: 'help-checklist-detail', path: '/helpcenter/station/checklist/0', i18nPrefix: 'helpCenter.checklist', section: 'Checklisten > Checkliste'},
    {route: 'help-checklist-list', path: '/helpcenter/station/checklist', i18nPrefix: 'helpCenter.checklist', section: 'Checklisten'},
    {route: 'help-federated-kb-file', path: '/helpcenter/station/federation/knowledge/0/1', i18nPrefix: 'helpCenter.federatedKbFile', section: 'Föderation > Wiki-Artikel'},
    {route: 'help-federated-protocol', path: '/helpcenter/station/federation/protocols/0/1', i18nPrefix: 'helpCenter.federatedProtocol', section: 'Föderation > Protokoll'},
    {route: 'help-federated-quiz-catalog', path: '/helpcenter/station/federation/quiz/0/1', i18nPrefix: 'helpCenter.federatedQuizCatalog', section: 'Föderation > Fragenkatalog'},
    {route: 'help-inventory-assign', path: '/helpcenter/station/inventory/assign', i18nPrefix: 'helpCenter.inventoryAssignHelp', section: 'Inventar > Ausgabe'},
    {route: 'help-inventory-check-container-walk', path: '/helpcenter/station/inventory/checks/container/0', i18nPrefix: 'helpCenter.inventoryCheckContainerWalk', section: 'Inventar > Prüfung durchgehen'},
    {route: 'help-inventory-check-container', path: '/helpcenter/station/inventory/checks/container', i18nPrefix: 'helpCenter.inventoryCheckContainerHelp', section: 'Inventar > Prüfungen'},
    {route: 'help-inventory-movement-detail', path: '/helpcenter/station/inventory/movement/0', i18nPrefix: 'helpCenter.movementDetail', section: 'Inventar > Bewegung'},
    {route: 'help-inventory-container-detail', path: '/helpcenter/station/inventory/storage/0', i18nPrefix: 'helpCenter.inventoryContainerDetailHelp', section: 'Inventar > Lagerort'},
    {route: 'help-inventory-storage', path: '/helpcenter/station/inventory/storage', i18nPrefix: 'helpCenter.inventoryStorageHelp', section: 'Inventar > Lagerorte'},
    {route: 'help-inventory-lending-blocks-create', path: '/helpcenter/station/inventory/lending/blocks/create', i18nPrefix: 'helpCenter.lendingBlocksCreate', section: 'Inventar > Verleih > Sperren anlegen'},
    {route: 'help-station-moved-delete', path: '/helpcenter/station/moved/delete', i18nPrefix: 'helpCenter.stationMoved', section: 'Wache umgezogen > Löschen'},
    {route: 'help-station-moved', path: '/helpcenter/station/moved', i18nPrefix: 'helpCenter.stationMoved', section: 'Wache umgezogen'},
    {route: 'help-profile-notifications', path: '/helpcenter/station/profile/settings/notifications', i18nPrefix: 'helpCenter.notifications', section: 'Profil > Einstellungen > Benachrichtigungen'},
    {route: 'help-profile-security', path: '/helpcenter/station/profile/settings/security', i18nPrefix: 'helpCenter.security', section: 'Profil > Einstellungen > Sicherheit'},
    {route: 'help-profile-sessions', path: '/helpcenter/station/profile/settings/sessions', i18nPrefix: 'helpCenter.sessions', section: 'Profil > Einstellungen > Sitzungen'},
    {route: 'help-profile-theming', path: '/helpcenter/station/profile/settings/theming', i18nPrefix: 'helpCenter.theming', section: 'Profil > Einstellungen > Erscheinungsbild'},
    {route: 'help-profile-ical-feed', path: '/helpcenter/station/profile/feeds/ical', i18nPrefix: 'helpCenter.icalFeed', section: 'Profil > Feeds > Kalender'},
    {route: 'help-profile-rss-feed', path: '/helpcenter/station/profile/feeds/rss', i18nPrefix: 'helpCenter.rssFeed', section: 'Profil > Feeds > RSS'},
    {route: 'help-event-batch', path: '/helpcenter/station/events/batch', i18nPrefix: 'helpCenter.batchCreate', section: 'Termine > Serie'},
    {route: 'help-event-categories', path: '/helpcenter/station/events/categories', i18nPrefix: 'helpCenter.categories', section: 'Termine > Kategorien'},
    {route: 'help-station-discovery', path: '/helpcenter/station/federate/discovery', i18nPrefix: 'helpCenter.discovery', section: 'Föderation > Verzeichnis'},
    {route: 'help-station-federation-settings', path: '/helpcenter/station/federate/settings', i18nPrefix: 'helpCenter.federationSettings', section: 'Föderation > Einstellungen'},
    {route: 'help-procedure-edit', path: '/helpcenter/station/procedures/0/edit', i18nPrefix: 'helpCenter.procedureCreate', section: 'Abläufe > Bearbeiten'},
    {route: 'help-member-documents', path: '/helpcenter/station/members/documents', i18nPrefix: 'helpCenter.memberDocuments', section: 'Mitglieder > Dokumente'},
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
 * Builds the index from the text the pages actually render.
 *
 * <p>It used to be built at module load from the static locale file, on the reasoning that this made it
 * complete regardless of vue-i18n runtime state. That was true when it was written. The help text then
 * moved into its own chunk, merged into the active locale when a help center layout renders, and what
 * stayed in the static file under `helpCenter` was two keys: an index meant to hold 166 pages held one,
 * which is why the search answered nothing for any word on any page but the waiting lists.
 *
 * <p>Reading the merged messages is the fix, and it costs nothing: the search box only ever renders
 * inside a help center layout, and that layout has awaited the chunk before anybody can type. Importing
 * the chunk statically here would work too and would undo the code splitting it exists for.
 */
export async function buildHelpSearchIndex(): Promise<HelpSearchEntry[]> {
    await loadHelpcenterMessages()
    const messages = i18n.global.getLocaleMessage('de-DE') as Record<string, unknown>
    const entries: HelpSearchEntry[] = []
    for (const page of HELP_PAGE_MAP) {
        const prefixes = Array.isArray(page.i18nPrefix) ? page.i18nPrefix : [page.i18nPrefix]
        const subtrees = prefixes.map(prefix => resolveKey(messages, prefix)).filter(Boolean)
        if (subtrees.length === 0) continue

        const text = subtrees.flatMap(subtree => flattenStrings(subtree)).join(' ')

        let title = page.section
        for (const subtree of subtrees) {
            if (typeof subtree !== 'object' || subtree === null) continue
            const t = (subtree as Record<string, unknown>)['title'] ?? (subtree as Record<string, unknown>)['overviewTitle']
            if (typeof t === 'string') {
                title = t
                break
            }
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
}

/**
 * Built once and shared by every box that asks afterwards.
 *
 * <p>Started as the help centre renders rather than on the first keystroke: the chunk the text lives in
 * is four thousand lines, and nobody should be typing into a box that is still reading it.
 *
 * <p>A failed attempt is forgotten rather than remembered. The chunk comes over the network, a fetch can
 * fail, and a remembered failure would leave the box answering nothing for the rest of the visit, which
 * is the shape of the fault this whole repair is about.
 */
const index = ref<HelpSearchEntry[]>([])
let building: Promise<void> | null = null

function ensureIndex(): void {
    if (building) return
    building = buildHelpSearchIndex()
        .then(entries => {
            index.value = entries
        })
        .catch(() => {
            building = null
        })
}

export function useHelpSearch() {
    ensureIndex()

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
        for (const entry of index.value) {
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
