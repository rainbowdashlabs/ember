/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {associationTemplateSource, stationTemplateSource, type TemplateSource} from '@/api/documentTemplates'
import {associationFontSource, stationFontSource, type FontSource} from '@/api/documentFonts'

/**
 * What the template list and the template editor need to know about the owner they show: where its
 * templates and fonts are, which pages they lead to, and whether it has members of its own.
 *
 * <p>A station and an association keep templates the same way, so both use the same two screens with
 * one of these handed in. The page titles are read from the route names, under `pages.<route>`.
 */
export interface TemplateScreens {
    source: TemplateSource
    fonts: FontSource
    /** The route of the list of templates. */
    listRoute: string
    /** The route of the editor, which takes the template id or `new`. */
    editRoute: string
    /** The route where a station sets how it uses a template of its association, or null where the list holds none. */
    useRoute: string | null
    /**
     * Whether the owner has members of its own: a self service audience to choose and members to look at
     * a template for. An association only offers a template; its stations choose the audience.
     */
    hasMembers: boolean
}

/** The templates of the station, and those of its association it uses. */
export const STATION_TEMPLATE_SCREENS: TemplateScreens = {
    source: stationTemplateSource,
    fonts: stationFontSource,
    listRoute: 'member-document-templates',
    editRoute: 'member-document-template-edit',
    useRoute: 'member-document-template-use',
    hasMembers: true,
}

/** The templates the association keeps for all its stations. */
export const ASSOCIATION_TEMPLATE_SCREENS: TemplateScreens = {
    source: associationTemplateSource,
    fonts: associationFontSource,
    listRoute: 'cluster-document-templates',
    editRoute: 'cluster-document-template-edit',
    useRoute: null,
    hasMembers: false,
}
