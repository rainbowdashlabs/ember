/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {eventGroups, events} from '@/api'
import {OfferedFieldTypes} from '@/api/fieldTypes'
import type {
    AppointmentTemplateField,
    EventAttachment,
    EventFieldEntry,
    EventTemplate,
    RegistrationFieldDefinition,
    TemplateDetailResponse,
} from '@/api/generated/schema'

/** The fields of an appointment template split into those an event group takes and the names of the rest. */
export interface GroupFields {
    kept: EventFieldEntry[]
    leftOut: string[]
}

/**
 * The fields of an appointment template an event group can hand to its parts. A field answered per
 * date or tied to a field of the attendance sheet belongs to one date, and a kind the event group does
 * not offer cannot be stored on it, so those are named instead of taken.
 *
 * @param fields the template's fields
 */
export function groupFieldsOf(fields: readonly AppointmentTemplateField[]): GroupFields {
    const offered = new Set<string>(OfferedFieldTypes.EVENT_GROUP)
    const kept: EventFieldEntry[] = []
    const leftOut: string[] = []
    for (const field of fields) {
        if (field.attendanceFieldId != null || field.config.perDate || !offered.has(field.fieldType)) {
            leftOut.push(field.name)
            continue
        }
        kept.push({
            name: field.name,
            fieldType: field.fieldType,
            config: field.config,
            value: field.defaultValue ?? '',
            overview: field.overview,
            attendanceFieldId: null,
            isPublic: field.isPublic,
        })
    }
    return {kept, leftOut}
}

/**
 * The registration questions of an appointment template as the event group editor holds them.
 *
 * @param detail the template
 */
export function groupQuestionsOf(detail: TemplateDetailResponse): RegistrationFieldDefinition[] {
    return detail.registrationFields.map(({name, fieldType, config, overview}) => ({name, fieldType, config, overview}))
}

/**
 * Starting a new event group from an appointment template: the templates on offer, the fields the
 * chosen one carried that the event group could not take, and its files. The event group does not
 * exist before it is saved, so the files are only named until then and attached right after.
 */
export function useEventGroupTemplate() {
    const templates = ref<EventTemplate[]>([])
    const leftOut = ref<string[]>([])
    const files = ref<EventAttachment[]>([])

    async function loadTemplates() {
        templates.value = await events.listTemplates().catch(() => [])
    }

    /** Reads a template and notes its files, for the editor to take the rest into its form. */
    async function read(templateId: number): Promise<TemplateDetailResponse> {
        const [detail, templateFiles] = await Promise.all([
            events.getTemplate(templateId),
            events.listTemplateAttachments(templateId),
        ])
        files.value = templateFiles
        return detail
    }

    /** Attaches the noted files to the event group just created, in the template's order and with its labels. */
    async function attachFiles(eventGroupId: number) {
        for (const file of files.value) {
            await eventGroups.attachFile(eventGroupId, file.fileId, file.label, file.internal)
        }
        files.value = []
    }

    const props = computed(() => ({templates: templates.value, leftOut: leftOut.value, files: files.value}))

    return {templates, leftOut, files, props, loadTemplates, read, attachFiles}
}
