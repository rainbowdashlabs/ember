/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {TestProtocolSection} from '@/api/generated/schema'

/**
 * The station a member goes to once an examiner closed theirs: the next top-level section in the order
 * of the sheet that is not marked as checked yet, starting over at the first after the last.
 *
 * @param stations the top-level sections of the sheet, in their order
 * @param done     the sections already marked as checked for the member
 * @param closedId the section just closed
 * @returns the next open section, or null once every section is checked
 */
export function nextStation(
    stations: readonly TestProtocolSection[],
    done: ReadonlySet<number>,
    closedId: number,
): TestProtocolSection | null {
    const closedAt = stations.findIndex(station => station.id === closedId)
    const after = [...stations.slice(closedAt + 1), ...stations.slice(0, closedAt + 1)]
    return after.find(station => !done.has(station.id)) ?? null
}
