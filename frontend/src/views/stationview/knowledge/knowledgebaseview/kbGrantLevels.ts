/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {KbAccessLevelName, KbGrant} from '@/api/knowledgeBase'

/**
 * The level each audience entry of one knowledge base entry holds, keyed by the entry it belongs
 * to. A missing key and a {@code null} value mean the same thing: the entry names an audience and
 * leaves what they may do to their station permission.
 */
export type GrantLevels = Record<string, KbAccessLevelName | null>

export function userTypeKey(userType: string): string {
    return `userType:${userType}`
}

export function groupKey(groupId: number): string {
    return `group:${groupId}`
}

export function tagKey(tagId: number): string {
    return `tag:${tagId}`
}

/**
 * The key a stored grant belongs under. Grants naming an individual member are not editable here
 * and answer with an empty key, which keeps them out of the dialog's map.
 */
export function grantKey(grant: KbGrant): string {
    if (grant.userType) return userTypeKey(grant.userType)
    if (grant.groupId != null) return groupKey(grant.groupId)
    if (grant.tagId != null) return tagKey(grant.tagId)
    return ''
}
