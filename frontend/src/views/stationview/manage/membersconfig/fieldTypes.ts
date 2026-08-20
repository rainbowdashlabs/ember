/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FieldTypes, type FieldTypeName} from '@/api/profileFields'
import {FieldWidths, type FieldWidthName} from '@/components/profilefields/fieldLayout'

/**
 * The field types a station can choose from, in the order they are offered. Held here rather than
 * beside each list that shows them: a type known to the dialog but not to the table showed up in
 * the table as its bare name.
 */
export const FIELD_TYPE_ORDER: FieldTypeName[] = [
    FieldTypes.TEXT,
    FieldTypes.NUMBER,
    FieldTypes.DATE,
    FieldTypes.BIRTH_DATE,
    FieldTypes.BOOLEAN,
    FieldTypes.ENUM,
    FieldTypes.AGE,
    FieldTypes.SECTION,
]

type Translate = (key: string) => string

/** The name of a type as a station reads it, falling back to the bare value for an unknown one. */
export function fieldTypeLabel(translate: Translate, value: string): string {
    const label = translate(`membersConfig.typeLabels.${value}`)
    return label.endsWith(value) ? value : label
}

/** How much of a row a field takes, written short enough for a column of its own. */
export function widthLabel(translate: Translate, width: FieldWidthName): string {
    const keys: Record<FieldWidthName, string> = {
        [FieldWidths.FULL]: 'membersConfig.widthShortFull',
        [FieldWidths.HALF]: 'membersConfig.widthShortHalf',
        [FieldWidths.THIRD]: 'membersConfig.widthShortThird',
    }
    return translate(keys[width])
}
