/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ReminderTrigger, StationState, type AdminStationSummary} from '@/api/generated/schema'

/** A station whose manager has not chosen a password yet and was reminded automatically after a week. */
export const SAMPLE_WAITING_STATION: AdminStationSummary = {
    id: '00000000-0000-0000-0000-0000000000a2', name: 'Feuerwache Süd', state: StationState.WAITING_FOR_MANAGER,
    memberCount: 0, associationName: null, managerName: null, managerEmail: 'jana.berg@example.org',
    waitingSince: '2026-10-20T09:00:00Z', lastReminder: {sentAt: '2026-10-27T09:10:00Z', trigger: ReminderTrigger.AFTER_7_DAYS},
    createdAt: '2026-10-20T09:00:00Z', movedAway: false,
}

/** One station in each state, as the station list shows them. */
export const SAMPLE_STATIONS: readonly AdminStationSummary[] = [
    {
        id: '00000000-0000-0000-0000-0000000000a1', name: 'Feuerwache Nord', state: StationState.SET_UP,
        memberCount: 42, associationName: 'Kreisfeuerwehrverband', managerName: 'Max Mustermann',
        managerEmail: 'max.mustermann@example.org', waitingSince: null, lastReminder: null,
        createdAt: '2025-03-14T10:00:00Z', movedAway: false,
    },
    SAMPLE_WAITING_STATION,
    {
        id: '00000000-0000-0000-0000-0000000000a3', name: 'Löschzug Ost', state: StationState.SETTING_UP,
        memberCount: 3, associationName: 'Kreisfeuerwehrverband', managerName: 'Paul Krüger',
        managerEmail: 'paul.krueger@example.org', waitingSince: '2026-10-28T14:00:00Z',
        lastReminder: {sentAt: '2026-11-02T16:45:00Z', trigger: ReminderTrigger.MANUAL},
        createdAt: '2026-10-28T14:00:00Z', movedAway: false,
    },
    {
        id: '00000000-0000-0000-0000-0000000000a4', name: 'Jugendfeuerwehr West', state: StationState.NO_MANAGER,
        memberCount: 0, associationName: null, managerName: null, managerEmail: null, waitingSince: null,
        lastReminder: null, createdAt: '2026-11-05T08:30:00Z', movedAway: false,
    },
]
