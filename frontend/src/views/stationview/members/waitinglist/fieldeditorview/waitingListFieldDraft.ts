/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FieldType, type WaitingListField, type WaitingListFieldRequest} from '@/api/generated/schema'
import type {QuestionSettingsModel} from '@/components/input/questionsettings/questionSettings'
import {usableOptions} from '@/util/choiceOptions'

/**
 * A field of a waiting list as it is edited: the shared settings in the shape the shared editor
 * edits, and whether the public form asks it. A field that is not saved yet has no id.
 */
export interface WaitingListFieldDraft {
    id: number | null
    name: string
    fieldType: FieldType
    settings: QuestionSettingsModel
    isPublic: boolean
}

/** A field nobody has written yet: a line of text without a name, asked on the public form. */
export function blankDraft(): WaitingListFieldDraft {
    return {id: null, name: '', fieldType: FieldType.TEXT, settings: {}, isPublic: true}
}

/** A saved field, read into the shape it is edited in. */
export function draftOf(field: WaitingListField): WaitingListFieldDraft {
    return {
        id: field.id,
        name: field.name,
        fieldType: field.fieldType,
        settings: {required: field.required, options: [...(field.config?.options ?? [])]},
        isPublic: field.isPublic ?? true,
    }
}

/**
 * The field as it is saved, at the place in the form it stands at. A choice keeps its answers
 * without the empty rows; any other type keeps none, however the type was changed back and forth.
 */
export function requestOf(draft: WaitingListFieldDraft, position: number): WaitingListFieldRequest {
    const options = usableOptions(draft.settings.options ?? [])
    return {
        name: draft.name.trim(),
        fieldType: draft.fieldType,
        config: draft.fieldType === FieldType.CHOICE && options.length > 0 ? {options} : {},
        position,
        required: draft.settings.required ?? false,
        isPublic: draft.isPublic,
    }
}
