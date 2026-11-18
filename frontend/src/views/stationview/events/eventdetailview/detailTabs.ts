/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Translate} from '@/util/failure'

/** The tabs of an appointment's page. */
export type DetailTabKey = 'info' | 'registrations' | 'attendance' | 'equipment'

/** What decides which tabs an appointment's page offers. */
export interface DetailTabFacts {
    /** Whether the appointment is signed up for, rather than everybody being expected. */
    requiresRegistration: boolean
    /** Whether the reader keeps attendance lists. */
    canManageAttendance: boolean
    /** Whether the appointment names an attendance template to start a list from. */
    hasAttendanceTemplate: boolean
    /** Whether a date is on screen, which a list belongs to. */
    hasDate: boolean
    /** The attendance list already kept for the date, or none. */
    attendanceSessionId: number | null
    /** Whether the date on screen is cancelled. */
    cancelled: boolean
    /** Whether what the appointment needs can be read. */
    canReadEquipment: boolean
}

/**
 * Whether the date on screen gets a tab for its attendance, as the menu offers it: for somebody who
 * keeps attendance, where a list is kept already, or where one can be started from the template on a
 * date that is not cancelled. A list written before the date was cancelled stays a record.
 */
export function showsAttendanceTab(facts: DetailTabFacts): boolean {
    if (!facts.canManageAttendance) return false
    return facts.attendanceSessionId != null || (!facts.cancelled && facts.hasAttendanceTemplate && facts.hasDate)
}

/**
 * The tabs of an appointment's page, in order.
 *
 * <p>The second tab follows what the appointment asks of people. Where it has to be signed up for,
 * it holds the sign-ups. Where it does not, everybody is expected and the only answer anybody gives
 * is a refusal, so it holds who is not coming and is named for that. Taking attendance is a tab of
 * its own, never the place the refusals are read.
 */
export function detailTabs(facts: DetailTabFacts, t: Translate): {key: DetailTabKey; label: string}[] {
    const tabs: {key: DetailTabKey; label: string}[] = [
        {key: 'info', label: t('eventDetail.tabInfo')},
        {key: 'registrations', label: facts.requiresRegistration ? t('eventDetail.tabRegistrations') : t('eventDetail.tabSignOffs')},
    ]
    if (showsAttendanceTab(facts)) tabs.push({key: 'attendance', label: t('eventDetail.tabAttendance')})
    if (facts.canReadEquipment) tabs.push({key: 'equipment', label: t('eventDetail.tabEquipment')})
    return tabs
}
