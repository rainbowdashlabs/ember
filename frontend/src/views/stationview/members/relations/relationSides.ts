/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {StationUserType} from '@/api/types'

const GIVEN_GUARDIANS: readonly string[] = [StationUserType.TRIAL, StationUserType.MEMBER]
const LOOKING_AFTER_OTHERS: readonly string[] =
    [StationUserType.GUARDIAN, StationUserType.TEAM, StationUserType.MANAGER]

/**
 * Whether a member of this type can be given a guardian.
 *
 * <p>Only members and trial members can: the server refuses a guardian for anybody else, because
 * every other type is an adult who looks after themselves.
 */
export function canHaveGuardians(userType: string): boolean {
    return GIVEN_GUARDIANS.includes(userType)
}

/**
 * Whether a member is the one looking after others rather than the one being looked after.
 *
 * <p>Guardians, team members and managers all can be put in charge of a member, so their side of
 * the link is the members in their care. Anybody who already has members in their care is on that
 * side too, whatever their type says.
 *
 * @param managedCount how many members are in their care, where that is known
 */
export function looksAfterMembers(userType: string, managedCount = 0): boolean {
    return managedCount > 0 || LOOKING_AFTER_OTHERS.includes(userType)
}

/**
 * The name of the relations tab for a member, which names their side of the link.
 *
 * @param t            the translator of the page asking
 * @param managedCount how many members are in their care, where that is known
 */
export function relationsTabLabel(t: (key: string) => string, userType: string, managedCount = 0): string {
    return looksAfterMembers(userType, managedCount) ? t('memberDetail.tabManagedMembers') : t('memberDetail.tabGuardians')
}
