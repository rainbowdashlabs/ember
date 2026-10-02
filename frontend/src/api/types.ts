/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {StationUserType} from './generated/schema'

/**
 * Human-readable German labels for each station user type. Single source of truth
 * for any picker, badge, filter, or summary that needs to render a user type name
 * without going through i18n.
 */
export const StationUserTypeLabels: Record<StationUserType, string> = {
    TRIAL: 'Probe',
    MEMBER: 'Mitglied',
    GUARDIAN: 'Erziehungsberechtigter',
    TEAM: 'Team',
    MANAGER: 'Manager',
}

/**
 * Audience selection shared by every restriction-capable feature. The restriction
 * editor speaks this shape; each feature maps it onto its own request payload.
 */
export interface RestrictionSelection {
    userTypes: StationUserType[]
    groupIds: number[]
    tagIds: number[]
    memberIds: number[]
    mode: 'AND' | 'OR'
}
