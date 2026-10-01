/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    TOO_MANY_ATTEMPTS,
} from './shared'

const MAP_TILE_NOT_A_NUMBER = 'Eine Kachel der Karte wird durch ganze Zahlen benannt'

/** What the refusals of maps say in German, keyed by their `MP-` code. */
export default {
    'MP-001': 'Nenne die Kachel der Karte, die du willst',
    'MP-002': MAP_TILE_NOT_A_NUMBER,
    'MP-003': MAP_TILE_NOT_A_NUMBER,
    'MP-004': 'Die Karte wird in dieser Zoomstufe nicht angeboten',
    'MP-005': 'Diese Kachel liegt außerhalb der Karte',
    'MP-006': TOO_MANY_ATTEMPTS,
    'MP-007': 'Wähle, woher die Karte kommt, es wurde nichts gespeichert',
    'MP-008': 'Der Zoom muss von 0 bis höchstens 22 reichen, der kleinere Wert zuerst, es wurde nichts gespeichert',
    'MP-009': 'Dieser Kartenanbieter braucht einen Schlüssel, es wurde nichts gespeichert',
    'MP-010': 'Eine eigene Karte braucht die Adresse, von der ihre Bilder kommen, es wurde nichts gespeichert',
    'MP-011': 'Wähle, wer Adressen nachschlägt, es wurde nichts gespeichert',
    'MP-012': 'Der Kartenzwischenspeicher muss zwischen 0 und 10000 MB groß sein, es wurde nichts gespeichert',
}
