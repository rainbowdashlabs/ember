/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {DocumentTemplateSummary} from '@/api/generated/schema'
import type {TemplateScreens} from '../templateScreens'

/**
 * Where a row of the template list leads: the editor for a template of the list's owner, and for a
 * template of the association in a station's list the page where the station sets how it uses it,
 * since only the association changes the template itself.
 *
 * @param row     the template
 * @param screens whose list it is
 * @returns the route to open
 */
export function templateTarget(
    row: Pick<DocumentTemplateSummary, 'id' | 'ofAssociation'>, screens: Pick<TemplateScreens, 'editRoute' | 'useRoute'>) {
    const name = row.ofAssociation && screens.useRoute ? screens.useRoute : screens.editRoute
    return {name, params: {id: row.id}}
}
