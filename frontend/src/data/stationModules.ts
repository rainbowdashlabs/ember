/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {StationModule} from '@/api/generated/schema'

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
 * them. Keyed by the module, so a module added to {@link StationModule} and forgotten here does not
 * compile rather than silently missing from one of the lists.
 */
const DETAILS: Record<StationModule, ModuleDetails> = {
    [StationModule.INVENTORY]: {labelKey: 'stationManage.moduleInventory', icon: ['fas', 'boxes-stacked'], descriptionKey: 'stationManage.moduleInventoryText'},
    [StationModule.NEWS]: {labelKey: 'stationManage.moduleNews', icon: ['fas', 'newspaper'], descriptionKey: 'stationManage.moduleNewsText'},
    [StationModule.EVENTS]: {labelKey: 'stationManage.moduleEvents', icon: ['fas', 'calendar-days'], descriptionKey: 'stationManage.moduleEventsText'},
    [StationModule.ATTENDANCE]: {labelKey: 'stationManage.moduleAttendance', icon: ['fas', 'clipboard-user'], descriptionKey: 'stationManage.moduleAttendanceText'},
    [StationModule.FORMS]: {labelKey: 'stationManage.moduleForms', icon: ['fas', 'square-poll-vertical'], descriptionKey: 'stationManage.moduleFormsText'},
    [StationModule.LOST_AND_FOUND]: {labelKey: 'stationManage.moduleLostAndFound', icon: ['fas', 'box-open'], descriptionKey: 'stationManage.moduleLostAndFoundText'},
    [StationModule.WAITING_LIST]: {labelKey: 'stationManage.moduleWaitingList', icon: ['fas', 'user-clock'], descriptionKey: 'stationManage.moduleWaitingListText'},
    [StationModule.QUIZ]: {labelKey: 'stationManage.moduleQuiz', icon: ['fas', 'graduation-cap'], descriptionKey: 'stationManage.moduleQuizText'},
    [StationModule.TEST_PROTOCOL]: {labelKey: 'stationManage.moduleTestProtocol', icon: ['fas', 'clipboard-list'], descriptionKey: 'stationManage.moduleTestProtocolText'},
    [StationModule.KNOWLEDGE_BASE]: {labelKey: 'stationManage.moduleKnowledgeBase', icon: ['fas', 'book-open'], descriptionKey: 'stationManage.moduleKnowledgeBaseText'},
    [StationModule.BOARDS]: {labelKey: 'stationManage.moduleBoards', icon: ['fas', 'table-columns'], descriptionKey: 'stationManage.moduleBoardsText'},
    [StationModule.PROCEDURES]: {labelKey: 'stationManage.moduleProcedures', icon: ['fas', 'list-check'], descriptionKey: 'stationManage.moduleProceduresText'},
    [StationModule.DOCUMENTS]: {labelKey: 'stationManage.moduleDocuments', icon: ['fas', 'file'], descriptionKey: 'stationManage.moduleDocumentsText'},
}

/**
 * Every module a station can have. The station's module screen, the setup wizard, the
 * association's module screen and the help pages about them all list exactly these, so none of
 * them can offer fewer modules than the station has.
 */
export const STATION_MODULE_OPTIONS: readonly StationModuleOption[] = Object.entries(DETAILS)
    .map(([value, details]) => ({value: value as StationModule, ...details}))
