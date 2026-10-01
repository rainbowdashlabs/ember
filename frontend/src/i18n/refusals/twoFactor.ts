/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    TOO_MANY_ATTEMPTS,
    REQUEST_INCOMPLETE,
} from './shared'

const VERIFICATION_CODE_WRONG = 'Dieser Code war nicht richtig, es wurde nichts bestätigt'
const NO_SECOND_FACTOR_SET_UP = 'Für dieses Konto ist kein zweiter Faktor eingerichtet'
const SIGN_IN_NOT_WAITING = 'Diese Anmeldung wartet nicht mehr auf einen zweiten Faktor. Melde dich erneut an'
const SECURITY_KEY_NOT_ACCEPTED = 'Dieser Sicherheitsschlüssel wurde nicht angenommen, es wurde nichts bestätigt'
const FACTOR_NOT_HERE = 'Diesen zweiten Faktor gibt es nicht mehr'
const POLICY_NOT_HERE = 'Diese Regel gibt es nicht mehr'
const SECOND_FACTOR_NOT_RESET = 'Dieser zweite Faktor konnte nicht zurückgesetzt werden, es wurde nichts geändert'

/** What the refusals of two-factor say in German, keyed by their `TF-` code. */
export default {
    'TF-001': 'Für dieses Konto ist bereits ein zweiter Faktor eingerichtet',
    'TF-002': REQUEST_INCOMPLETE,
    'TF-003': VERIFICATION_CODE_WRONG,
    'TF-004': 'Für dieses Konto ist kein App-Code eingerichtet, der entfernt werden könnte',
    'TF-005': NO_SECOND_FACTOR_SET_UP,
    'TF-006': REQUEST_INCOMPLETE,
    'TF-007': SIGN_IN_NOT_WAITING,
    'TF-008': VERIFICATION_CODE_WRONG,
    'TF-009': 'Diesem Gerät vertraut dieses Konto nicht mehr',
    'TF-010': REQUEST_INCOMPLETE,
    'TF-011': NO_SECOND_FACTOR_SET_UP,
    'TF-012': VERIFICATION_CODE_WRONG,
    'TF-013': REQUEST_INCOMPLETE,
    'TF-014': 'Dieser Sicherheitsschlüssel konnte nicht eingerichtet werden, es wurde nichts gespeichert',
    'TF-015': FACTOR_NOT_HERE,
    'TF-016': 'Dieser Name konnte nicht vergeben werden, der zweite Faktor blieb, wie er war',
    'TF-017': REQUEST_INCOMPLETE,
    'TF-018': REQUEST_INCOMPLETE,
    'TF-019': SECURITY_KEY_NOT_ACCEPTED,
    'TF-020': REQUEST_INCOMPLETE,
    'TF-021': SECURITY_KEY_NOT_ACCEPTED,
    'TF-022': SIGN_IN_NOT_WAITING,
    'TF-023': 'Kein anderes Gerät dieses Kontos konnte das bestätigen',
    'TF-024': REQUEST_INCOMPLETE,
    'TF-025': REQUEST_INCOMPLETE,
    'TF-026': 'Danach fragt dich diese Instanz nicht',
    'TF-027': REQUEST_INCOMPLETE,
    'TF-028': 'Ein Passwort reicht bei diesem Konto nicht aus, um das zu bestätigen',
    'TF-029': 'Dieses Passwort war nicht richtig, es wurde nichts bestätigt',
    'TF-030': 'Dieses Konto hat keinen Passkey zum Bestätigen',
    'TF-031': REQUEST_INCOMPLETE,
    'TF-032': 'Dieser Passkey hat es nicht bestätigt, es wurde nichts bestätigt',
    'TF-035': POLICY_NOT_HERE,
    'TF-036': POLICY_NOT_HERE,
    'TF-037': SECOND_FACTOR_NOT_RESET,
    'TF-039': 'Dieses Mitglied ist nicht auf deiner Wache, es wurde nichts geändert',
    'TF-040': 'Das kann bei einer anderen Instanz-Verwaltung nur eine Instanz-Verwaltung zurücksetzen',
    'TF-041': SECOND_FACTOR_NOT_RESET,
    'TF-042': TOO_MANY_ATTEMPTS,
    'TF-043': TOO_MANY_ATTEMPTS,
    'TF-044': TOO_MANY_ATTEMPTS,
    'TF-045': TOO_MANY_ATTEMPTS,
    'TF-046': TOO_MANY_ATTEMPTS,
    'TF-047': TOO_MANY_ATTEMPTS,
    'TF-048': TOO_MANY_ATTEMPTS,
    'TF-049': TOO_MANY_ATTEMPTS,
    'TF-050': 'Eine Anmeldung, die ein anderes Gerät bestätigt hat, kann kein weiteres Gerät bestätigen. Nimm ein Gerät, auf dem du dich selbst angemeldet hast',
}
