/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Translate} from '@/util/failure'

/**
 * Preparation and follow-up as one short line, naming only the parts that count, the way the sheet
 * and the report show it beside a name. Empty where neither counts.
 */
export function extraTimeText(t: Translate, preparationMinutes: number, followUpMinutes: number): string {
    const parts: string[] = []
    if (preparationMinutes > 0) parts.push(t('attendanceTimeRules.preparationShort', {minutes: preparationMinutes}))
    if (followUpMinutes > 0) parts.push(t('attendanceTimeRules.followUpShort', {minutes: followUpMinutes}))
    return parts.join(', ')
}
