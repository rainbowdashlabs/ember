/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {FieldTypes, memberConstraintOf, namesMembers, type FieldTypeName} from '@/api/fieldTypes'
import type {EventFieldEntry, EventQuestionSettings} from '@/api/generated/schema'
import type {QuestionSetting, QuestionSettingsModel} from '@/components/input/questionsettings/questionSettings'
import {usableOptions} from '@/util/choiceOptions'

/**
 * Who answers a question of an appointment: the organiser once for the appointment, or every
 * registrant for themselves. The two keep their settings in one record, and each uses its own part.
 */
export type EventQuestionMode = 'organiser' | 'registrant'

/**
 * A question as either editor holds it. An appointment's own question carries an answer, a tie to the
 * attendance sheet and whether readers outside see it; a registration question carries none of those,
 * so it is the same shape with those left out.
 */
export type EventQuestionDraft = EventFieldEntry

/** The shared settings each kind of question offers; what is only its own stays on its editor. */
export const SHARED_SETTINGS: Record<EventQuestionMode, readonly QuestionSetting[]> = {
    organiser: ['options', 'members'],
    registrant: ['options', 'required', 'default', 'bounds'],
}

/** The settings of a question that carries none of its own. */
export function emptySettings(): EventQuestionSettings {
    return {selfRegistration: false, perDate: false, required: false, managersOnly: false}
}

/** A question nobody has written yet: a line of text without a name. */
export function blankQuestion(): EventQuestionDraft {
    return {name: '', fieldType: FieldTypes.TEXT, config: emptySettings(), value: '', overview: false, attendanceFieldId: null}
}

/** The shared part of a question's settings, in the shape the shared settings editor edits. */
export function settingsModelOf(config: EventQuestionSettings | null | undefined): QuestionSettingsModel {
    const settings = config ?? emptySettings()
    return {
        required: settings.required,
        defaultValue: settings.defaultValue ?? null,
        options: settings.options ?? [],
        min: settings.min ?? null,
        max: settings.max ?? null,
        groupId: settings.groupId ?? null,
        userType: settings.userType ?? null,
        tagId: settings.tagId ?? null,
    }
}

/** The settings with the shared part written back into them. */
export function withSettingsModel(
    config: EventQuestionSettings | null | undefined,
    model: QuestionSettingsModel,
): EventQuestionSettings {
    return {
        ...(config ?? emptySettings()),
        required: model.required ?? false,
        defaultValue: model.defaultValue ?? undefined,
        options: model.options,
        min: model.min ?? undefined,
        max: model.max ?? undefined,
        groupId: model.groupId ?? undefined,
        userType: model.userType ?? undefined,
        tagId: model.tagId ?? undefined,
    }
}

/**
 * A question as it is saved: only the settings its type means something for.
 *
 * <p>The editor keeps a choice's empty rows and a member field's group while the type is changed back
 * and forth, so nothing typed is lost to a slip of the select. What is saved is only what the type
 * reads: the choices without the empty rows, the group, user type or tag the type names, and whether
 * members put themselves in only for a member field.
 */
export function asSaved<T extends EventQuestionDraft>(question: T): T {
    const type: FieldTypeName = question.fieldType ?? FieldTypes.TEXT
    const settings = question.config ?? emptySettings()
    const constraint = memberConstraintOf(type)
    const options = usableOptions(settings.options ?? [])
    return {
        ...question,
        fieldType: type,
        config: {
            ...settings,
            options: type === FieldTypes.CHOICE && options.length > 0 ? options : undefined,
            groupId: constraint === 'group' ? settings.groupId : undefined,
            userType: constraint === 'userType' ? settings.userType : undefined,
            tagId: constraint === 'tag' ? settings.tagId : undefined,
            selfRegistration: namesMembers(type) && settings.selfRegistration,
            perDate: namesMembers(type) && settings.perDate,
            min: type === FieldTypes.NUMBER ? settings.min : undefined,
            max: type === FieldTypes.NUMBER ? settings.max : undefined,
        },
    }
}
