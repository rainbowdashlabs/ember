/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {StationModules} from '@/api/types'
import type {StationModule} from '@/api/generated/schema'

/** One module a station can switch on or off, as every list of modules shows it. */
export interface StationModuleOption {
    value: StationModule
    /** The i18n key of its name. */
    labelKey: string
    icon: string[]
    /** The i18n key of the sentence saying what it is for. */
    descriptionKey: string
}

type ModuleDetails = Omit<StationModuleOption, 'value'>

/**
 * What each module is called and looks like, in the order the station's own module screen lists
 * them. Keyed by the module, so a module added to {@link StationModules} and forgotten here does not
 * compile rather than silently missing from one of the lists.
 */
const DETAILS: Record<StationModule, ModuleDetails> = {
    [StationModules.INVENTORY]: {labelKey: 'stationManage.moduleInventory', icon: ['fas', 'boxes-stacked'], descriptionKey: 'stationManage.moduleInventoryText'},
    [StationModules.NEWS]: {labelKey: 'stationManage.moduleNews', icon: ['fas', 'newspaper'], descriptionKey: 'stationManage.moduleNewsText'},
    [StationModules.EVENTS]: {labelKey: 'stationManage.moduleEvents', icon: ['fas', 'calendar-days'], descriptionKey: 'stationManage.moduleEventsText'},
    [StationModules.ATTENDANCE]: {labelKey: 'stationManage.moduleAttendance', icon: ['fas', 'clipboard-user'], descriptionKey: 'stationManage.moduleAttendanceText'},
    [StationModules.FORMS]: {labelKey: 'stationManage.moduleForms', icon: ['fas', 'square-poll-vertical'], descriptionKey: 'stationManage.moduleFormsText'},
    [StationModules.LOST_AND_FOUND]: {labelKey: 'stationManage.moduleLostAndFound', icon: ['fas', 'box-open'], descriptionKey: 'stationManage.moduleLostAndFoundText'},
    [StationModules.WAITING_LIST]: {labelKey: 'stationManage.moduleWaitingList', icon: ['fas', 'user-clock'], descriptionKey: 'stationManage.moduleWaitingListText'},
    [StationModules.QUIZ]: {labelKey: 'stationManage.moduleQuiz', icon: ['fas', 'graduation-cap'], descriptionKey: 'stationManage.moduleQuizText'},
    [StationModules.TEST_PROTOCOL]: {labelKey: 'stationManage.moduleTestProtocol', icon: ['fas', 'clipboard-list'], descriptionKey: 'stationManage.moduleTestProtocolText'},
    [StationModules.KNOWLEDGE_BASE]: {labelKey: 'stationManage.moduleKnowledgeBase', icon: ['fas', 'book-open'], descriptionKey: 'stationManage.moduleKnowledgeBaseText'},
    [StationModules.BOARDS]: {labelKey: 'stationManage.moduleBoards', icon: ['fas', 'table-columns'], descriptionKey: 'stationManage.moduleBoardsText'},
    [StationModules.PROCEDURES]: {labelKey: 'stationManage.moduleProcedures', icon: ['fas', 'list-check'], descriptionKey: 'stationManage.moduleProceduresText'},
    [StationModules.DOCUMENTS]: {labelKey: 'stationManage.moduleDocuments', icon: ['fas', 'file'], descriptionKey: 'stationManage.moduleDocumentsText'},
}

/**
 * Every module a station can have. The station's module screen, the setup wizard, the
 * association's module screen and the help pages about them all list exactly these, so none of
 * them can offer fewer modules than the station has.
 */
export const STATION_MODULE_OPTIONS: readonly StationModuleOption[] = Object.entries(DETAILS)
    .map(([value, details]) => ({value: value as StationModule, ...details}))
