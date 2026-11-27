/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {AxiosError} from 'axios'
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
 * date belongs to one date, and a kind the event group does not offer cannot be stored on it, so those
 * are named instead of taken. A field tied to the attendance sheet keeps its tie where the event group
 * takes the template's sheet along, and is named otherwise, since it would fill in nothing.
 *
 * @param fields          the template's fields
 * @param takesTheSheet   whether the event group takes the template's attendance sheet
 */
export function groupFieldsOf(fields: readonly AppointmentTemplateField[], takesTheSheet: boolean): GroupFields {
    const offered = new Set<string>(OfferedFieldTypes.EVENT_GROUP)
    const kept: EventFieldEntry[] = []
    const leftOut: string[] = []
    for (const field of fields) {
        const tiedElsewhere = field.attendanceFieldId != null && !takesTheSheet
        if (tiedElsewhere || field.config.perDate || !offered.has(field.fieldType)) {
            leftOut.push(field.name)
            continue
        }
        kept.push({
            name: field.name,
            fieldType: field.fieldType,
            config: field.config,
            value: field.defaultValue ?? '',
            overview: field.overview,
            attendanceFieldId: field.attendanceFieldId ?? null,
            isPublic: field.isPublic,
        })
    }
    return {kept, leftOut}
}

/**
 * The entries of a template not yet in the form, matched by name the way the server matches them:
 * trimmed and ignoring case. Taking a question or field whose name the form already holds would ask
 * it twice, which the server refuses for questions and every part would show twice for fields.
 *
 * @param current  what the form holds
 * @param incoming what the template brings
 * @return the entries to add, and the names of those left out because the form has them already
 */
export function newByName<T extends {name?: string | null}>(current: readonly T[], incoming: readonly T[]): {added: T[], present: string[]} {
    const held = new Set(current.map(entry => nameKey(entry.name)))
    const added: T[] = []
    const present: string[] = []
    for (const entry of incoming) {
        const key = nameKey(entry.name)
        if (held.has(key)) {
            present.push(entry.name ?? '')
            continue
        }
        held.add(key)
        added.push(entry)
    }
    return {added, present}
}

function nameKey(name: string | null | undefined): string {
    return (name ?? '').trim().toLowerCase()
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
    const present = ref<string[]>([])
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

    /**
     * Attaches the noted files to the event group just created, in the template's order and with its
     * labels. A file leaves the list once it is attached, so a save tried again does not attach it twice.
     * A file the library no longer holds is left out rather than holding up the save, and its name is
     * given back so the page can report it.
     *
     * @return the names of the files left out because they are gone
     */
    async function attachFiles(eventGroupId: number): Promise<string[]> {
        const gone: string[] = []
        while (files.value.length > 0) {
            const file = files.value[0]!
            try {
                await eventGroups.attachFile(eventGroupId, file.fileId, file.label, file.internal)
            } catch (e) {
                if (!(e instanceof AxiosError) || e.response?.status !== 404) throw e
                gone.push(file.label ?? file.fileName)
            }
            files.value = files.value.slice(1)
        }
        return gone
    }

    const props = computed(() => ({templates: templates.value, leftOut: leftOut.value, present: present.value, files: files.value}))

    return {templates, leftOut, present, files, props, loadTemplates, read, attachFiles}
}
