/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {memberConstraintOf, namesMembers} from '@/api/fieldTypes'
import {
    FieldType,
    type AttendanceFieldConfig,
    type AttendanceTemplateField,
    type TemplateFieldRequest,
} from '@/api/generated/schema'
import {
    defaultAsText,
    typedDefault,
    type QuestionSetting,
    type QuestionSettingsModel,
} from '@/components/input/questionsettings/questionSettings'
import {FieldWidths, type PreviewField} from '@/components/profilefields/fieldLayout'

/**
 * A field of an attendance sheet as it is edited: the shared settings in the shape the shared
 * editor edits, and beside them what only a sheet's field has. A field that is not saved yet has no
 * id.
 */
export interface AttendanceFieldDraft {
    id: number | null
    name: string
    fieldType: FieldType
    settings: QuestionSettingsModel
    /** Whether the members a member field names are counted as present on the sheet. */
    autoAttend: boolean
    width: string
}

/** A field nobody has written yet: a line of text without a name, a whole row wide. */
export function blankDraft(): AttendanceFieldDraft {
    return {id: null, name: '', fieldType: FieldType.TEXT, settings: {}, autoAttend: false, width: FieldWidths.FULL}
}

/** A saved field, read into the shape it is edited in. */
export function draftOf(field: AttendanceTemplateField): AttendanceFieldDraft {
    return {
        id: field.id,
        name: field.name,
        fieldType: field.fieldType,
        settings: {
            required: field.config.required,
            options: [...(field.config.options ?? [])],
            groupId: field.config.groupId,
            defaultValue: defaultAsText(field.config.defaultValue),
        },
        autoAttend: field.config.autoAttend,
        width: field.config.width ?? FieldWidths.FULL,
    }
}

/**
 * What a sheet's field offers to set. A member field starts empty, because whom a sheet names is
 * decided by who comes, and a date starts from today or not at all.
 */
export function offersOf(fieldType: FieldType): QuestionSetting[] {
    return namesMembers(fieldType)
        ? ['options', 'members', 'required']
        : ['options', 'default', 'todayDefault', 'members', 'required']
}

/**
 * The settings as the sheet keeps them: a yes as a yes, a number as a number, and nothing a type
 * has no use for.
 *
 * <p>The editor keeps a choice's answers and a member field's group while the type is changed back
 * and forth, so nothing typed is lost to a slip of the select. What is saved is only what the type
 * reads.
 */
export function configOf(draft: AttendanceFieldDraft): AttendanceFieldConfig {
    const type = draft.fieldType
    const chosen = draft.settings
    const options = chosen.options ?? []
    return {
        required: chosen.required ?? false,
        width: draft.width && draft.width !== FieldWidths.FULL ? draft.width : null,
        groupId: memberConstraintOf(type) === 'group' ? chosen.groupId ?? null : null,
        autoAttend: namesMembers(type) && draft.autoAttend,
        options: type === FieldType.CHOICE && options.length > 0 ? [...options] : null,
        defaultValue: offersOf(type).includes('default') ? typedDefault(type, chosen.defaultValue) ?? null : null,
    }
}

/** The field as it is saved, at the place in the sheet it is drawn at. */
export function requestOf(draft: AttendanceFieldDraft, position: number): TemplateFieldRequest {
    return {name: draft.name.trim(), fieldType: draft.fieldType, config: configOf(draft), position}
}

/** The field as the preview of the sheet draws it. */
export function previewOf(draft: AttendanceFieldDraft): PreviewField {
    return {
        name: draft.name,
        fieldType: draft.fieldType,
        required: draft.settings.required ?? false,
        width: draft.width === FieldWidths.FULL ? null : draft.width,
    }
}
