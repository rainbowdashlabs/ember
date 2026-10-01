/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {parseFieldConfig} from '@/api/profileFields'
import {FieldTypes} from '@/api/fieldTypes'
import type {ProfileField, StationUserType as StationUserTypeName} from '@/api/generated/schema'
import {StationUserType, StationUserTypeLabels} from '@/api/types'
import {
    ColumnTypes, columnTypeOf, enumOptions,
    type CellValue, type ColumnOption, type TableColumn,
} from '@/components/table/tableColumn'
import {expirySettingsOf} from '@/util/expiry'
import {memberDisplayName, type RosterMember} from './useMemberData'

/** What the member columns need to know about the people beyond the people themselves. */
export interface MemberColumnSources {
    t: (key: string) => string
    groupsOf: (memberId: number) => string[]
    tagsOf: (memberId: number) => string[]
    /** A member's answer to a question as a cell, empty where the question is not put to them. */
    answerOf: (memberId: number, fieldId: number) => CellValue
    /** Whether groups and tags are columns, which they are only on one station's own list. */
    stationLocalColumns: boolean
}

/** The kind a member counts as when one question is put to several of them. */
export function roleOf(roles: readonly string[]): string {
    for (const kind of [StationUserType.MANAGER, StationUserType.TEAM, StationUserType.GUARDIAN, StationUserType.TRIAL]) {
        if (roles.includes(kind)) return kind
    }
    return StationUserType.MEMBER
}

/** The kinds of member as the options of an enum column, worded the one way every table words them. */
export function userTypeOptions(): ColumnOption[] {
    return enumOptions(Object.keys(StationUserTypeLabels), value => StationUserTypeLabels[value as StationUserTypeName])
}

export const MEMBER_NAME_KEY = 'name'

/** The member's name as the pinned first column of any table of members. */
export function memberNameColumn<T extends Parameters<typeof memberDisplayName>[0]>(t: (key: string) => string): TableColumn<T> {
    return {key: MEMBER_NAME_KEY, label: t('membersList.colName'), type: ColumnTypes.TEXT, value: memberDisplayName, pinned: true}
}

/**
 * One profile question as a column.
 *
 * <p>Somebody the question is not put to has an empty cell rather than a blank answer, so a filter
 * for the empty answers finds the people who left it open and not everybody who was never asked.
 * The key is the field's id, which is what saved filters have always named it by.
 */
function fieldColumn(field: ProfileField, sources: MemberColumnSources): TableColumn<RosterMember> {
    const config = parseFieldConfig(field.config)
    const choices = config.options
    const options = Array.isArray(choices) ? choices.map(choice => ({value: String(choice), label: String(choice)})) : undefined
    return {
        key: String(field.id),
        label: field.name ?? '',
        type: columnTypeOf(field.fieldType),
        value: member => sources.answerOf(member.id, field.id),
        options,
        warnFromDays: expirySettingsOf(config).warnFromDays,
        defaultVisible: config.overview === true,
    }
}

/** Whether a question is answered at all, which a heading or a spacer in the form never is. */
export function holdsAnswer(field: Pick<ProfileField, 'fieldType'>): boolean {
    return field.fieldType !== FieldTypes.SECTION && field.fieldType !== FieldTypes.SPACER
}

/**
 * The columns of the member list: the name, the kind of member, the address, on a station's own
 * list its groups and tags, and one per question the tab's people are asked and can answer.
 */
export function memberColumns(fields: readonly ProfileField[], sources: MemberColumnSources): TableColumn<RosterMember>[] {
    const {t} = sources
    return [
        memberNameColumn(t),
        {key: 'userType', label: t('membersList.colRole'), type: ColumnTypes.ENUM, value: member => member.userType, options: userTypeOptions()},
        {key: 'email', label: t('membersList.colEmail'), type: ColumnTypes.TEXT, value: member => member.email},
        ...(sources.stationLocalColumns ? [
            {key: 'groups', label: t('membersList.colGroups'), type: ColumnTypes.TEXT, value: (member: RosterMember) => sources.groupsOf(member.id)},
            {key: 'tags', label: t('membersList.colTags'), type: ColumnTypes.TEXT, value: (member: RosterMember) => sources.tagsOf(member.id)},
        ] : []),
        ...fields.filter(holdsAnswer).map(field => fieldColumn(field, sources)),
    ]
}
