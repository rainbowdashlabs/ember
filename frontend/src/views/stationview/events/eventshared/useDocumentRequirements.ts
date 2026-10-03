/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {appointmentDocuments} from '@/api'
import type {RequirementOwner} from '@/api/appointmentDocuments'
import type {RequiredTemplate} from '@/api/generated/schema'
import {recentlyUsedFirst} from '@/components/documents/recentlyUsedFirst'

/**
 * The documents an appointment or an appointment template asks participants to bring, as the editor
 * holds them until it saves: the templates for appointments the station offers, the most recently used
 * first, and the ones chosen.
 *
 * <p>An appointment made from an appointment template takes the template's documents into the
 * editor's list, the same way it takes its registration questions, so the save that creates the
 * appointment writes them.
 */
export function useDocumentRequirements() {
    const offered = ref<RequiredTemplate[]>([])
    const chosen = ref<RequiredTemplate[]>([])

    /** Reads what can be asked for and, for a saved appointment or template, what it asks for. */
    async function load(owner: RequirementOwner | null) {
        const [offer, current] = await Promise.all([
            appointmentDocuments.offeredTemplates(),
            owner ? appointmentDocuments.listRequirements(owner) : Promise.resolve([]),
        ])
        offered.value = recentlyUsedFirst(offer)
        chosen.value = current
    }

    /** Adds the documents an appointment template asks for, after the ones chosen already. */
    async function takeFrom(owner: RequirementOwner) {
        const handed = await appointmentDocuments.listRequirements(owner)
        chosen.value = withoutTwice([...chosen.value, ...handed])
    }

    /** Writes the chosen documents for a saved appointment or template. */
    async function save(owner: RequirementOwner) {
        chosen.value = await appointmentDocuments.setRequirements(owner, chosen.value.map(template => template.templateId))
    }

    return {offered, chosen, load, takeFrom, save}
}

/**
 * The templates in their order, each once.
 *
 * @param templates the templates, possibly naming one twice
 */
export function withoutTwice(templates: readonly RequiredTemplate[]): RequiredTemplate[] {
    return templates.filter((template, index) =>
        templates.findIndex(other => other.templateId === template.templateId) === index)
}
