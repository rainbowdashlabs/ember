/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    PICTURE_NOT_HERE,
    STATION_NOT_HERE,
} from './shared'

/** What the refusals of the feed say in German, keyed by their `FD-` code. */
export default {
    'FD-001': 'Dieser Feed-Link führt nirgendwo mehr hin',
    'FD-002': STATION_NOT_HERE,
    'FD-003': 'Diesen Eintrag im Fundbüro gibt es nicht mehr',
    'FD-004': PICTURE_NOT_HERE,
    'FD-005': STATION_NOT_HERE,
    'FD-006': 'Der Feed ließ sich nicht zusammenstellen. Ein erneuter Versuch kann klappen; '
        + 'wenn es weiter passiert, melde es bitte',
}
