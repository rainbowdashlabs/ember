/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {STATION_NOT_HERE} from './shared'

/** What the refusals of mail say in German, keyed by their `ML-` code. */
export default {
    'ML-001': 'Hier nimmt nichts diese Meldung entgegen',
    'ML-002': 'Eine fehlgeschlagene E-Mail der Instanz mit dieser Nummer gibt es nicht',
    'ML-003': 'Die Empfängerseite hat diese E-Mail abgelehnt, ein erneuter Versand würde wieder abgelehnt',
    'ML-004': 'Diese E-Mail ist fehlgeschlagen, bevor der Grund festgehalten wurde, und kann nicht erneut gesendet werden',
    'ML-005': STATION_NOT_HERE,
    'ML-006': 'Zur Kenntnis nehmen braucht den zuletzt gesehenen Fehlschlag, es wurde nichts zur Kenntnis genommen',
}
