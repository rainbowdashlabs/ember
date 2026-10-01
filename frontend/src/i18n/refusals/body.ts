/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const NOTHING_SAVED_UNUSABLE = 'Etwas am Gesendeten war nicht verwendbar, es wurde nichts gespeichert'

/** What the refusals of the request body say in German, keyed by their `B-` code. */
export default {
    'B-001': 'Die Anfrage war kein gültiges JSON, es wurde nichts gespeichert',
    'B-002': 'Die Anfrage enthält ein Feld, das diese Schnittstelle nicht annimmt',
    'B-003': 'Die Anfrage hat nicht die Form, die diese Schnittstelle erwartet',
    'B-004': 'Ein Wert in der Anfrage ist keiner, den diese Schnittstelle annimmt',
    'B-005': NOTHING_SAVED_UNUSABLE,
    'B-006': 'Die Einstellungen eines Blocks passen nicht zu seiner Art, es wurde nichts gespeichert',
}
