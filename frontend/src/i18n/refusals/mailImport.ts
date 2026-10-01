/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    CHANGE_SAVED_BUT_NOT_READ_BACK,
} from './shared'

const MAILBOX_NOT_HERE = 'Dieses Postfach gibt es nicht mehr'
const MAILBOX_RULE_NOT_HERE = 'Diese Postfachregel gibt es nicht mehr'

/** What the refusals of mail import say in German, keyed by their `MI-` code. */
export default {
    'MI-001': 'Diese Wache liest keine Mails in ihre Dokumente ein',
    'MI-002': MAILBOX_NOT_HERE,
    'MI-003': MAILBOX_RULE_NOT_HERE,
    'MI-004': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'MI-005': CHANGE_SAVED_BUT_NOT_READ_BACK,
    'MI-006': 'Ein Postfach braucht ein Passwort, es wurde nichts gespeichert',
    'MI-007': 'Ein Postfach braucht ein Passwort, es wurde nichts geändert',
    'MI-008': 'Das Einlesen von Mails in Dokumente ist auf dieser Instanz abgeschaltet',
    'MI-009': 'Eine Regel braucht einen Namen, es wurde nichts gespeichert',
    'MI-010': 'Der Name der Regel ist zu lang, es wurde nichts gespeichert',
    'MI-011': 'Eine Regel muss sagen, welchen Absendern sie vertraut, sonst nimmt sie nichts an. Es wurde nichts gespeichert',
    'MI-012': "Ein Absender muss eine Adresse oder eine Domain in der Form *{'@'}domain sein, es wurde nichts gespeichert",
    'MI-013': 'Eine Regel muss sagen, welche Dateiarten sie annimmt, es wurde nichts gespeichert',
    'MI-014': 'Diese Dateiart kann das Einlesen nicht erkennen, es wurde nichts gespeichert',
    'MI-015': 'Zum Verschieben einer Mail braucht es einen Zielordner, es wurde nichts gespeichert',
    'MI-016': 'Mit diesem Postfach verbindet sich diese Instanz nicht, es wurde nichts gespeichert',
    'MI-017': 'Auf dieser Instanz ist kein Schlüssel eingerichtet, ein Postfachpasswort lässt sich daher nicht sicher aufbewahren. Es wurde nichts gespeichert',
    'MI-018': 'Ein Postfach braucht einen Namen, es wurde nichts gespeichert',
    'MI-019': 'Der Name des Postfachs ist zu lang, es wurde nichts gespeichert',
    'MI-020': 'Ein Postfach braucht einen Server, es wurde nichts gespeichert',
    'MI-021': 'Der Servername ist zu lang, es wurde nichts gespeichert',
    'MI-022': 'Ein Postfach braucht einen Benutzernamen, es wurde nichts gespeichert',
    'MI-023': 'Der Benutzername ist zu lang, es wurde nichts gespeichert',
}
