/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    STATION_NOT_HERE,
    NOT_A_STATION_IDENTITY,
    FILE_NOT_HERE,
    SESSION_WITHOUT_ACCOUNT,
    TRANSFER_TOKEN_NOT_GOOD,
} from './shared'

const STORAGE_MOVE_NOT_DONE = 'Die Dateien ließen sich nicht umziehen, es wurde nichts geändert und sie liegen '
    + 'weiterhin dort, wo sie waren. Der Grund steht im Log der Instanz'
const STATION_BEING_TRANSFERRED = 'Diese Wache zieht gerade auf eine andere Instanz um, bis dahin lässt sich nichts ändern'

/** What the refusals of storage say in German, keyed by their `ST-` code. */
export default {
    'ST-001': STORAGE_MOVE_NOT_DONE,
    'ST-002': 'Diese Wache hat keinen eigenen Speicher, der sich testen ließe',
    'ST-004': STATION_NOT_HERE,
    'ST-005': SESSION_WITHOUT_ACCOUNT,
    'ST-006': 'Diese Wache gehört zu keinem Verbund, es wurde nichts geändert',
    'ST-007': 'Dieser Verbund hält für seine Wachen keinen Speicher bereit, es wurde nichts geändert',
    'ST-008': 'Dieser Verbund hat keinen eigenen Speicher, es wurde nichts geändert',
    'ST-009': 'Der Verbund dieser Wache entscheidet, wo ihre Dateien liegen, es wurde nichts geändert',
    'ST-010': 'Diese Adresse darf diese Instanz nicht ansprechen',
    'ST-011': 'Gib den Zugriffsschlüssel und den geheimen Schlüssel für diesen Speicher an, '
        + 'es wurde nichts gespeichert',
    'ST-012': 'Gib den Benutzernamen und das Passwort für diesen Speicher an, es wurde nichts gespeichert',
    'ST-013': 'Gib entweder ein Passwort oder einen privaten Schlüssel für diesen Speicher an, '
        + 'und nur eines von beiden',
    'ST-014': 'Sag, wo die Dateien liegen sollen, es wurde nichts geändert',
    'ST-015': STORAGE_MOVE_NOT_DONE,
    'ST-016': SESSION_WITHOUT_ACCOUNT,
    'ST-017': 'Der Umzug ist mittendrin abgebrochen und wurde zurückgenommen, die Dateien liegen weiterhin dort, '
        + 'wo sie waren. Der Grund steht im Log der Instanz',
    'ST-018': 'Der Speicher dieser Wache ist gerade nicht erreichbar. Ein neuer Versuch in einem Moment kann klappen',
    'ST-019': STATION_NOT_HERE,
    'ST-020': NOT_A_STATION_IDENTITY,
    'ST-021': 'Der Zeitpunkt, vor dem die Änderungen aufgelistet werden sollen, ist kein Zeitpunkt',
    'ST-022': STATION_NOT_HERE,
    'ST-023': FILE_NOT_HERE,
    'ST-024': STATION_BEING_TRANSFERRED,
    'ST-025': STATION_BEING_TRANSFERRED,
    'ST-026': STATION_BEING_TRANSFERRED,
    'ST-027': STATION_BEING_TRANSFERRED,
    'ST-028': 'Die Instanz zieht ihre Dateien gerade in einen anderen Speicher um, es wurde nichts gespeichert. Versuch es nach dem Umzug noch einmal',
    'ST-029': TRANSFER_TOKEN_NOT_GOOD,
    'ST-030': 'Der Speicher dieses Umzugs wurde bereits übergeben, und er wird nur einmal übergeben',
    'ST-031': TRANSFER_TOKEN_NOT_GOOD,
    'ST-032': TRANSFER_TOKEN_NOT_GOOD,
    'ST-033': 'Nenne die Datei, die gebraucht wird',
    'ST-034': TRANSFER_TOKEN_NOT_GOOD,
    'ST-035': 'Dieses Konto hat kein Profilbild, das mitgenommen werden kann',
    'ST-036': 'Nenne die Art von Dateien, die gebraucht wird',
    'ST-037': 'Diese Art von Dateien führt diese Instanz nicht',
    'ST-038': 'Diese Art von Dateien gehört nicht zu einer Wache',
    'ST-039': 'Diese Art von Dateien zieht nicht mit einer Wache um',
}
