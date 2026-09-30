/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {SessionAudience, TemplateDetail} from '@/api/attendance'

/**
 * Whom a template expects on its sheets: its user types and its groups, the groups in the template's
 * own order.
 */
export function templateAudience(template: TemplateDetail): SessionAudience {
    return {
        userTypes: [...(template.userTypes ?? [])],
        groupIds: [...(template.groups ?? [])]
            .sort((a, b) => a.position - b.position)
            .map(group => group.groupId),
    }
}

/**
 * Whether a sheet told this audience would expect exactly whom the template does. The user types
 * are compared as a set, the groups in order, because the order of the groups is the order the sheet
 * is written in.
 */
export function sameAudience(audience: SessionAudience, template: TemplateDetail): boolean {
    const ofTemplate = templateAudience(template)
    const types = new Set(audience.userTypes)
    return types.size === ofTemplate.userTypes.length
        && ofTemplate.userTypes.every(type => types.has(type))
        && audience.groupIds.length === ofTemplate.groupIds.length
        && audience.groupIds.every((groupId, index) => groupId === ofTemplate.groupIds[index])
}
