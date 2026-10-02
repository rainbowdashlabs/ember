/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    PAGE_NOT_HERE,
} from './shared'

const INSIGHTS_WINDOW_BACKWARDS = 'Der Zeitraum kann nicht vor seinem Beginn enden'

/** What the refusals of insights say in German, keyed by their `IS-` code. */
export default {
    'IS-002': 'Das benennt keine Seite',
    'IS-003': 'Gib den ersten und den letzten Zeitpunkt an, den die Zahlen umfassen sollen',
    'IS-004': 'Der angefragte Zeitraum besteht nicht aus Zeitpunkten',
    'IS-005': 'Frag zwischen 1 und 500 Seiten ab',
    'IS-006': 'Wie viele Seiten gezeigt werden, muss eine Zahl sein',
    'IS-007': INSIGHTS_WINDOW_BACKWARDS,
    'IS-008': PAGE_NOT_HERE,
    'IS-009': INSIGHTS_WINDOW_BACKWARDS,
}
