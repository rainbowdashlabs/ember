/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FieldWidths, type FieldWidthName} from '@/components/profilefields/fieldLayout'

type Translate = (key: string) => string

/** How much of a row a field takes, written short enough for a column of its own. */
export function widthLabel(translate: Translate, width: FieldWidthName): string {
    const keys: Record<FieldWidthName, string> = {
        [FieldWidths.FULL]: 'membersConfig.widthShortFull',
        [FieldWidths.HALF]: 'membersConfig.widthShortHalf',
        [FieldWidths.THIRD]: 'membersConfig.widthShortThird',
    }
    return translate(keys[width])
}
