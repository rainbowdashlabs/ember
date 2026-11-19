/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EventGroupSummary} from '@/api/generated/schema'

/**
 * The event groups with sign-ups waiting for a decision, as the open sign-ups list them and count
 * them. A cancelled event group waits for nothing, and the sidebar count leaves it out the same way.
 */
export function waitingEventGroups(groups: EventGroupSummary[]): EventGroupSummary[] {
    return groups.filter(group => group.pending > 0 && !group.cancelled)
}
