/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {MemberNotes} from '@/api/attendance'

/**
 * How much stands beside one member's name, counted the same way by the line that offers to show it
 * and by the panel that shows it.
 *
 * <p>A swap and a piece that was found are each something to do with the member in the room. A
 * birthday is not: nobody settles it, so it is worth showing and not worth counting.
 */
export function actionCount(notes?: MemberNotes): number {
    return (notes?.swaps.length ?? 0) + (notes?.foundItems.length ?? 0)
}

/** Whether there is a birthday to mention, which stands beside the count rather than inside it. */
export function hasBirthday(notes?: MemberNotes): boolean {
    return notes?.birthdayDaysAgo !== null && notes?.birthdayDaysAgo !== undefined
}

/** Whether anything at all is outstanding, which is what decides the panel is drawn. */
export function hasAnything(notes?: MemberNotes): boolean {
    return actionCount(notes) > 0 || hasBirthday(notes)
}
