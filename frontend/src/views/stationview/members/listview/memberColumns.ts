/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FieldTypes, parseFieldConfig, type ProfileField} from '@/api/profileFields'
import {StationUserType, StationUserTypeLabels, type StationMember} from '@/api/types'
import {ColumnTypes, type CellValue, type ColumnType, type TableColumn} from '@/components/table/tableColumn'
import {memberDisplayName} from './useMemberData'

/** What the member columns need to know about the people beyond the people themselves. */
export interface MemberColumnSources {
    t: (key: string) => string
    groupsOf: (memberId: number) => string[]
    tagsOf: (memberId: number) => string[]
    rolesOf: (memberId: number) => string[]
    fieldValue: (memberId: number, fieldId: number) => unknown
    isAskedOf: (fieldId: number, role: string) => boolean
    /** Whether groups and tags are columns, which they are only on one station's own list. */
    stationLocalColumns: boolean
}

const FIELD_TYPE_COLUMNS: Record<string, ColumnType> = {
    [FieldTypes.NUMBER]: ColumnTypes.NUMBER,
    [FieldTypes.AGE]: ColumnTypes.NUMBER,
    [FieldTypes.DATE]: ColumnTypes.DATE,
    [FieldTypes.BIRTH_DATE]: ColumnTypes.BIRTH_DATE,
    [FieldTypes.BOOLEAN]: ColumnTypes.BOOLEAN,
    [FieldTypes.ENUM]: ColumnTypes.ENUM,
}

/** The kind a member counts as when one question is put to several of them. */
export function roleOf(roles: readonly string[]): string {
    for (const kind of [StationUserType.MANAGER, StationUserType.TEAM, StationUserType.GUARDIAN, StationUserType.TRIAL]) {
        if (roles.includes(kind)) return kind
    }
    return StationUserType.MEMBER
}

function fieldCell(value: unknown): CellValue {
    if (value === null || value === undefined || value === '') return null
    if (Array.isArray(value)) return value.map(String)
    return typeof value === 'object' ? JSON.stringify(value) : value as string | number | boolean
}

/**
 * One profile question as a column.
 *
 * <p>Somebody the question is not put to has an empty cell rather than a blank answer, so a filter
 * for the empty answers finds the people who left it open and not everybody who was never asked.
 * The key is the field's id, which is what saved filters have always named it by.
 */
function fieldColumn(field: ProfileField, sources: MemberColumnSources): TableColumn<StationMember> {
    const choices = parseFieldConfig(field.config).options
    const options = Array.isArray(choices) ? choices.map(choice => ({value: String(choice), label: String(choice)})) : undefined
    return {
        key: String(field.id),
        label: field.name ?? '',
        type: FIELD_TYPE_COLUMNS[field.fieldType ?? ''] ?? ColumnTypes.TEXT,
        value: member => sources.isAskedOf(field.id, roleOf(sources.rolesOf(member.id)))
            ? fieldCell(sources.fieldValue(member.id, field.id))
            : null,
        options,
        defaultVisible: parseFieldConfig(field.config).overview === true,
    }
}

/**
 * The columns of the member list: the name, the kind of member, the address, on a station's own
 * list its groups and tags, and one per question the tab's people are asked.
 */
export function memberColumns(fields: readonly ProfileField[], sources: MemberColumnSources): TableColumn<StationMember>[] {
    const {t} = sources
    const kinds = Object.entries(StationUserTypeLabels).map(([value, label]) => ({value, label}))
    return [
        {key: 'name', label: t('membersList.colName'), type: ColumnTypes.TEXT, value: memberDisplayName, pinned: true},
        {key: 'userType', label: t('membersList.colRole'), type: ColumnTypes.ENUM, value: member => member.userType, options: kinds},
        {key: 'email', label: t('membersList.colEmail'), type: ColumnTypes.TEXT, value: member => member.email},
        ...(sources.stationLocalColumns ? [
            {key: 'groups', label: t('membersList.colGroups'), type: ColumnTypes.TEXT, value: (member: StationMember) => sources.groupsOf(member.id)},
            {key: 'tags', label: t('membersList.colTags'), type: ColumnTypes.TEXT, value: (member: StationMember) => sources.tagsOf(member.id)},
        ] : []),
        ...fields.map(field => fieldColumn(field, sources)),
    ]
}
