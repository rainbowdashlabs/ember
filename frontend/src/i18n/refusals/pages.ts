/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    CHANGE_SAVED_BUT_NOT_READ_BACK,
    STATION_NOT_HERE,
    UPLOAD_WITHOUT_FILE,
    UPLOAD_NOT_SAVED,
    UPLOAD_NOT_PROCESSED,
    PAGE_NOT_HERE,
} from './shared'

const PAGE_NEEDS_A_TITLE = 'Eine Seite braucht einen Titel, es wurde nichts gespeichert'

/** What the refusals of pages say in German, keyed by their `P-` code. */
export default {
    'P-013': 'Das Gesendete war nicht lesbar, es wurde keine Mitgliederliste ermittelt',
    'P-014': PAGE_NEEDS_A_TITLE,
    'P-015': 'Die Seite konnte nicht erstellt werden, es wurde nichts gespeichert',
    'P-016': PAGE_NEEDS_A_TITLE,
    'P-017': 'Eine Seite braucht eine eigene Adresse, es wurde nichts gespeichert',
    'P-018': 'Sag, wer die Seite sehen darf, es wurde nichts geändert',
    'P-019': 'Diese Seite hat seit deinem letzten Blick einen anderen Link bekommen, es wurde nichts geändert',
    'P-020': 'Diese Seite kann nicht die Startseite sein, es wurde nichts geändert',
    'P-021': UPLOAD_WITHOUT_FILE,
    'P-022': 'Diese Datei ist größer, als diese Instanz annimmt',
    'P-023': UPLOAD_NOT_SAVED,
    'P-024': UPLOAD_NOT_PROCESSED,
    'P-025': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'P-026': CHANGE_SAVED_BUT_NOT_READ_BACK,

    'P-001': PAGE_NOT_HERE,
    'P-002': 'Die Seite konnte nicht gespeichert werden',
    'P-003': PAGE_NOT_HERE,
    'P-004': PAGE_NOT_HERE,
    'P-005': PAGE_NOT_HERE,
    'P-006': PAGE_NOT_HERE,
    'P-007': PAGE_NOT_HERE,
    'P-008': PAGE_NOT_HERE,
    'P-009': PAGE_NOT_HERE,
    'P-010': STATION_NOT_HERE,
    'P-011': 'Dieser Link führt zu keiner Seite',
    'P-027': 'Eine Seite, die nur über ihren Link erreichbar ist, kann keine Unterseiten haben, es wurde nichts gespeichert',
    'P-028': 'Eine Seite, die nur über ihren Link erreichbar ist, steht nicht unter einer anderen, es wurde nichts gespeichert',
    'P-029': 'Eine Seite mit Unterseiten lässt sich nicht nur über ihren Link erreichbar machen, es wurde nichts geändert',
    'P-030': 'Diese Seite kann von außen niemand öffnen, daher gibt es keinen Link zu ersetzen',
    'P-031': 'Diese Seite gehört zu einer anderen Wache, es wurde nichts geändert',
    'P-032': 'Die Startseite muss öffentlich sein, es wurde nichts geändert',
    'P-033': 'Die Startseite kann nicht unter einer anderen Seite stehen, es wurde nichts geändert',
    'P-034': 'Seiten lassen sich höchstens drei Ebenen tief verschachteln, es wurde nichts gespeichert',
    'P-035': 'Dieser Block geht nur auf einer Seite, nicht in einer Neuigkeit oder einem Artikel, es wurde nichts gespeichert',
    'P-036': 'Dieser Block geht nur in einer Briefvorlage, es wurde nichts gespeichert',
}
