/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    MEMBER_NOT_YOURS,
    ACCOUNT_NOT_HERE,
    TOO_MANY_ATTEMPTS,
    REQUEST_INCOMPLETE,
} from './shared'

const PASSKEY_ENROLMENT_REFUSED = 'Dieser Passkey konnte nicht eingerichtet werden, es wurde keiner gespeichert'
const ENROLMENT_LINK_NOT_GOOD = 'Dieser Link ist nicht mehr gültig, damit lässt sich kein Passkey einrichten'
const DEVICE_CODE_MISSING = 'Gib den Code ein, den das andere Gerät zeigt'
const DEVICE_CODE_NOT_GOOD = 'Zu diesem Code gibt es nichts zu bestätigen'
const ACCOUNT_HOLDS_NO_PASSWORD = 'Dieses Konto hat kein Passwort'
const PASSKEY_NOT_HERE = 'Diesen Passkey gibt es nicht mehr'

/** What the refusals of passkeys say in German, keyed by their `PK-` code. */
export default {
    'PK-001': REQUEST_INCOMPLETE,
    'PK-002': 'Diese Anmeldung konnte nicht abgeschlossen werden. Frag das andere Gerät noch einmal',
    'PK-003': REQUEST_INCOMPLETE,
    'PK-004': REQUEST_INCOMPLETE,
    'PK-005': PASSKEY_ENROLMENT_REFUSED,
    'PK-006': REQUEST_INCOMPLETE,
    'PK-007': PASSKEY_ENROLMENT_REFUSED,
    'PK-008': REQUEST_INCOMPLETE,
    'PK-009': ENROLMENT_LINK_NOT_GOOD,
    'PK-010': REQUEST_INCOMPLETE,
    'PK-011': PASSKEY_ENROLMENT_REFUSED,
    'PK-012': REQUEST_INCOMPLETE,
    'PK-013': PASSKEY_ENROLMENT_REFUSED,
    'PK-014': DEVICE_CODE_MISSING,
    'PK-015': DEVICE_CODE_NOT_GOOD,
    'PK-016': 'Für jemand anderen lässt sich nur eine Anmeldung bestätigen',
    'PK-017': MEMBER_NOT_YOURS,
    'PK-018': DEVICE_CODE_MISSING,
    'PK-019': DEVICE_CODE_NOT_GOOD,
    'PK-020': 'Wähle die Zahl, die das andere Gerät zeigt',
    'PK-021': 'Das ist nicht die Zahl, die das andere Gerät zeigt',
    'PK-022': 'Diese Anfrage konnte nicht mehr bestätigt werden, es wurde nichts freigegeben',
    'PK-023': 'Passkeys sind auf dieser Instanz abgeschaltet',
    'PK-024': REQUEST_INCOMPLETE,
    'PK-025': 'Die Anmeldung mit diesem Passkey hat nicht geklappt. Versuche einen anderen Weg',
    'PK-026': ACCOUNT_NOT_HERE,
    'PK-027': REQUEST_INCOMPLETE,
    'PK-028': 'Dieser Passkey konnte nicht angelegt werden, es wurde keiner gespeichert',
    'PK-029': PASSKEY_NOT_HERE,
    'PK-030': PASSKEY_NOT_HERE,
    'PK-031': 'Das ist der einzige Weg in dieses Konto, er wurde behalten. '
        + 'Lass dich erneut einrichten, um einen neuen Passkey zu bekommen',
    'PK-032': 'Diese Instanz erlaubt es nicht, die Anmeldung mit Passwort abzuschalten, '
        + 'es wurde nichts geändert',
    'PK-033': 'Zum Abschalten der Anmeldung mit Passwort braucht es eine Adresse, die eine Zurücksetz-Mail '
        + 'erreicht, es wurde nichts geändert',
    'PK-034': 'Zum Abschalten der Anmeldung mit Passwort braucht es einen Passkey, der sich schon einmal '
        + 'angemeldet hat, es wurde nichts geändert',
    'PK-035': ACCOUNT_HOLDS_NO_PASSWORD,
    'PK-036': 'Das ist keine Antwort, die dieses Angebot annimmt',
    'PK-037': REQUEST_INCOMPLETE,
    'PK-038': ACCOUNT_HOLDS_NO_PASSWORD,
    'PK-039': 'Mit keinem Passkey dieses Kontos wurde bisher angemeldet, das Passwort wurde behalten',
    'PK-040': 'Diesen Passkey-Modus kennt diese Instanz nicht',
    'PK-041': 'Ohne Passwort braucht es funktionierende Mail, belegt durch eine Testmail, die rausging',
    'PK-042': 'Einige Konten hätten ohne Passkey keinen Weg mehr hinein, der Modus blieb, wie er war',
    'PK-043': TOO_MANY_ATTEMPTS,
    'PK-044': TOO_MANY_ATTEMPTS,
    'PK-045': TOO_MANY_ATTEMPTS,
    'PK-046': TOO_MANY_ATTEMPTS,
    'PK-047': TOO_MANY_ATTEMPTS,
    'PK-048': TOO_MANY_ATTEMPTS,
    'PK-049': TOO_MANY_ATTEMPTS,
    'PK-050': TOO_MANY_ATTEMPTS,
    'PK-051': TOO_MANY_ATTEMPTS,
    'PK-052': TOO_MANY_ATTEMPTS,
    'PK-053': TOO_MANY_ATTEMPTS,
    'PK-054': TOO_MANY_ATTEMPTS,
    'PK-055': TOO_MANY_ATTEMPTS,
}
