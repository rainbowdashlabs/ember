/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {LocationQuery} from 'vue-router'

/**
 * Whether the create page first asks how the procedure starts: from a template or empty. Editing, a
 * template already named in the address and a blank start go straight to the form.
 */
export function startsWithChoice(editing: boolean, presetTemplateId: number | null, query: LocationQuery): boolean {
    return !editing && presetTemplateId == null && query.blank == null
}
