/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    CHANGE_SAVED_BUT_NOT_READ_BACK,
} from './shared'

const PROTOCOL_NOT_HERE_BEHIND_RUN = 'Den Prüfungsbogen zu diesem Prüfungslauf gibt es nicht mehr'

/** What the refusals of test protocols say in German, keyed by their `T-` code. */
export default {
    'T-001': 'Diesen Abschnitt gibt es nicht mehr',
    'T-002': 'Diesen Punkt gibt es nicht mehr',
    'T-003': 'Ein Prüfungsbogen braucht einen Namen, es wurde nichts gespeichert',
    'T-004': 'Diesen Prüfungsbogen gibt es nicht mehr, es wurde nichts geändert',
    'T-005': 'Jemand anderes prüft dieses Mitglied gerade, es wurde nichts geändert',
    'T-006': PROTOCOL_NOT_HERE_BEHIND_RUN,
    'T-007': PROTOCOL_NOT_HERE_BEHIND_RUN,
    'T-008': 'Dieser Prüfungslauf konnte nicht in eine Datei gepackt werden. Versuch es noch einmal',
    'T-009': PROTOCOL_NOT_HERE_BEHIND_RUN,
    'T-010': PROTOCOL_NOT_HERE_BEHIND_RUN,
    'T-011': 'Unter dieser Nummer ist hier kein Prüfungsbogen mit dir geteilt',
    'T-012': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'T-013': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'T-014': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'T-015': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'T-016': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'T-017': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'T-018': 'Die Reihenfolge passt nicht mehr zum Prüfungsbogen, es wurde nichts verschoben. Lade die Seite neu und versuch es noch einmal',
    'T-019': 'Dieser Abschnitt gehört nicht zu diesem Prüfungsbogen, es wurde nichts hinzugefügt',
    'T-020': 'Dieser Abschnitt gehört nicht zu diesem Prüfungsbogen, es wurde nichts verschoben',
    'T-021': 'Ein Abschnitt lässt sich nicht in sich selbst oder einen seiner Unterabschnitte verschieben, es wurde nichts verschoben',
    'T-022': 'Nur die Prüfer dieses Prüfungslaufs dürfen ihn bewerten',
    'T-023': 'Dieser Abschnitt gehört nicht zum Prüfungsbogen dieses Laufs, es wurden keine Prüfer gespeichert',
    'T-024': 'Prüfer können nur Mitglieder sein, die Prüfungsbögen bewerten dürfen, es wurden keine Prüfer gespeichert',
    'T-025': 'Dieser Abschnitt gehört anderen Prüfern, er wurde nicht abgeschlossen',
}
