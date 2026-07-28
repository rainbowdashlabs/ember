/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MemberCheckSummary} from '@/api/inventoryCheck'
import {formatDateTime} from '@/util/format'

/** Full display name of the member. */
export function memberName(member: MemberCheckSummary): string {
  return `${member.firstName ?? ''} ${member.lastName ?? ''}`.trim()
}

/** Full display name of the last checker, or '-' when never checked. */
export function checkerName(member: MemberCheckSummary): string {
  if (!member.checkerFirstName) return '-'
  return `${member.checkerFirstName} ${member.checkerLastName ?? ''}`.trim()
}

/** Full display name of the person currently holding the lock, or empty string. */
export function lockerName(member: MemberCheckSummary): string {
  if (!member.lockerFirstName) return ''
  return `${member.lockerFirstName} ${member.lockerLastName ?? ''}`.trim()
}

/** Localised date or the provided fallback label when the date is missing. */
export function formatDate(dateStr: string | null | undefined, neverCheckedLabel: string): string {
  if (!dateStr) return neverCheckedLabel
  return formatDateTime(dateStr)
}

/** True when the lock on this member is held by the current member. */
export function isLockedByMe(member: MemberCheckSummary, currentMemberId: number | undefined): boolean {
  return member.locked && member.lockedBy === currentMemberId
}

/** True when the lock on this member is held by anyone other than the current member. */
export function isLockedByOther(member: MemberCheckSummary, currentMemberId: number | undefined): boolean {
  return member.locked && member.lockedBy !== currentMemberId
}
