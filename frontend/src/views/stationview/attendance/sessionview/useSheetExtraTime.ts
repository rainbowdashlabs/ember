/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {inject, provide, ref, type InjectionKey, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {attendance} from '@/api'
import type {ExtraTimeCredit} from '@/api/generated/schema'
import {describeFailure, type Failure} from '@/util/failure'

/** A switch that could not be written: on which row, and what went wrong. */
export interface SwitchFailure {
    entryId: number
    failure: Failure
}

/**
 * The preparation and follow-up a sheet's rules credit to the people on it, and the switch that takes
 * it away from one of them.
 */
export interface SheetExtraTime {
    /** What each row is credited with, by the row's id; a row the rules do not name is missing. */
    credits: Readonly<Ref<ReadonlyMap<number, ExtraTimeCredit>>>
    /** Whether the switch may be used, which is the sheet being open to the reader. */
    canEdit: Readonly<Ref<boolean>>
    /** The credits could not be read again; the ones shown are the last that could. */
    failure: Readonly<Ref<Failure | null>>
    /** The row whose switch is being written, which takes no second press meanwhile. */
    busyEntryId: Readonly<Ref<number | null>>
    /** The row whose switch could not be written, with what went wrong, shown on that row. */
    switchFailure: Readonly<Ref<SwitchFailure | null>>
    setSkipped: (entryId: number, skip: boolean) => Promise<void>
    reload: () => Promise<void>
}

const SHEET_EXTRA_TIME: InjectionKey<SheetExtraTime> = Symbol('sheetExtraTime')

/**
 * Reads what a sheet's rules credit and hands it to every row below, so the rows say it without each
 * asking on its own and without the sheet threading it through every panel in between.
 *
 * <p>Only the answer to the latest read is shown: a read started before a switch was written must not
 * put back what the switch just changed. A read that fails keeps what was shown and says so, rather
 * than wiping every note off the sheet.
 *
 * @param sessionId the sheet
 * @param canEdit   whether the reader may change the sheet
 */
export function provideSheetExtraTime(sessionId: Ref<number>, canEdit: Ref<boolean>): SheetExtraTime {
    const {t} = useI18n()
    const credits = ref<ReadonlyMap<number, ExtraTimeCredit>>(new Map())
    const failure = ref<Failure | null>(null)
    const busyEntryId = ref<number | null>(null)
    const switchFailure = ref<SwitchFailure | null>(null)
    let latestRead = 0

    async function reload() {
        const read = ++latestRead
        try {
            const list = await attendance.listExtraTime(sessionId.value)
            if (read !== latestRead) return
            credits.value = new Map(list.map(credit => [credit.entryId, credit]))
            failure.value = null
        } catch (e) {
            if (read === latestRead) failure.value = describeFailure(e, t)
        }
    }

    async function setSkipped(entryId: number, skip: boolean) {
        if (busyEntryId.value !== null) return
        busyEntryId.value = entryId
        switchFailure.value = null
        try {
            await attendance.setExtraTimeSkipped(entryId, skip)
            await reload()
        } catch (e) {
            switchFailure.value = {entryId, failure: describeFailure(e, t)}
        } finally {
            busyEntryId.value = null
        }
    }

    const state: SheetExtraTime = {credits, canEdit, failure, busyEntryId, switchFailure, setSkipped, reload}
    provide(SHEET_EXTRA_TIME, state)
    return state
}

/** What the sheet above provides, or nothing where a row is drawn outside a sheet. */
export function useSheetExtraTime(): SheetExtraTime | null {
    return inject(SHEET_EXTRA_TIME, null)
}
