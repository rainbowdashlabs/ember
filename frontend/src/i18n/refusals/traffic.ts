/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const TRAFFIC_SPAN_MISSING = 'Gib an, über welchen Zeitraum gezählt werden soll'
const TRAFFIC_SPAN_NOT_A_TIME = 'Beide Enden des Zeitraums müssen ein Datum mit Uhrzeit sein'
const TRAFFIC_KIND_UNKNOWN = 'Frag nach angemeldeten, abgemeldeten oder föderierten Anfragen, '
    + 'oder nach allen zusammen'
const TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS = 'Der Zeitraum kann nicht vor seinem Beginn enden'

/** What the refusals of traffic say in German, keyed by their `TR-` code. */
export default {
    'TR-001': TRAFFIC_SPAN_MISSING,
    'TR-002': TRAFFIC_SPAN_NOT_A_TIME,
    'TR-003': 'Das muss eine ganze Zahl sein',
    'TR-004': TRAFFIC_KIND_UNKNOWN,
    'TR-005': TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS,
    'TR-006': TRAFFIC_SPAN_MISSING,
    'TR-007': TRAFFIC_SPAN_NOT_A_TIME,
    'TR-008': TRAFFIC_KIND_UNKNOWN,
    'TR-010': TRAFFIC_SPAN_ENDS_BEFORE_IT_STARTS,
}
