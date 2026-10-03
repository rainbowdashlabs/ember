/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    CHOOSE_A_STATION,
} from './shared'

const STATION_NOT_FOUND_IN_DISCOVERY = 'Diese Wache lässt sich hier nicht finden'
const PEER_ADDRESS_MISSING = 'Gib die Adresse der Instanz an'
const PEER_NOT_HERE = 'Diese Instanz gibt es nicht mehr'

/** What the refusals of discovery say in German, keyed by their `DC-` code. */
export default {
    'DC-001': CHOOSE_A_STATION,
    'DC-002': STATION_NOT_FOUND_IN_DISCOVERY,
    'DC-003': CHOOSE_A_STATION,
    'DC-004': STATION_NOT_FOUND_IN_DISCOVERY,
    'DC-005': 'Diese Wache ist bereits eine Partnerwache von dir',
    'DC-006': 'Eine Anfrage an diese Wache wartet bereits auf eine Antwort',
    'DC-007': PEER_ADDRESS_MISSING,
    'DC-008': PEER_ADDRESS_MISSING,
    'DC-009': 'Diese Instanz hat einen anderen Schlüssel genannt als den erwarteten, es wurde nichts gespeichert',
    'DC-010': 'Diese Instanz nimmt nicht an der Suche nach Instanzen teil, es wurde nichts gespeichert',
    'DC-011': PEER_NOT_HERE,
    'DC-012': PEER_NOT_HERE,
    'DC-013': PEER_NOT_HERE,
    'DC-014': 'Sag, was blockiert wird und um welche Art es sich handelt, es wurde nichts gespeichert',
    'DC-015': 'Für diese Wache liegt hier kein Logo vor',
}
