/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** A value written once, or worked out from what the reader holds. */
export type NavValue<C, T> = T | ((context: C) => T)

/** What every entry of a sidebar shares: its name, its icon, whether the reader sees it and its count. */
interface NavEntryBase<C> {
    /** Stable identity: the accordion's key for a section, the list key for a page. */
    key: string
    /** The i18n key of the label. */
    label: NavValue<C, string>
    /** A Font Awesome solid icon name. */
    icon: NavValue<C, string>
    visible?: (context: C) => boolean
    badge?: (context: C) => number
    /** The anchor the onboarding tour points at. */
    onboarding?: string
}

/** One page of the sidebar: a link. */
export interface NavPageDef<C> extends NavEntryBase<C> {
    kind: 'page'
    to: NavValue<C, string>
    /** A name nobody translates, such as a board's own, shown instead of {@link label}. */
    text?: string
    /** A second icon after the label, marking a page that lives at another station. */
    trailingIcon?: string
    /** Shown faded: a page that is there but does not apply to this station. */
    dimmed?: (context: C) => boolean
    /** Reached from inside another page and from the palette, never listed in the sidebar itself. */
    paletteOnly?: boolean
}

/** A section or a subgroup: a row that holds pages. */
export interface NavGroupDef<C> extends NavEntryBase<C> {
    kind: 'group'
    /** Addresses the group covers that none of its pages lead to, such as a board's own page. */
    prefix?: NavValue<C, string[]>
    /** When true, the pages are listed in the parent without a row of their own. */
    inline?: (context: C) => boolean
    children: NavChildDef<C>[]
}

/** Pages only known at runtime, such as the station's boards. */
export type NavPagesDef<C> = (context: C) => NavPageDef<C>[]

export type NavChildDef<C> = NavPageDef<C> | NavGroupDef<C> | NavPagesDef<C>

export type NavEntryDef<C> = NavPageDef<C> | NavGroupDef<C>

/** A page as the sidebar and the palette show it to this reader. */
export interface NavPage {
    kind: 'page'
    key: string
    label: string
    icon: string
    to: string
    badge: number
    onboarding?: string
    trailingIcon?: string
    dimmed: boolean
    paletteOnly: boolean
}

/** A section or subgroup as this reader sees it, holding only what they may reach. */
export interface NavGroup {
    kind: 'group'
    key: string
    label: string
    icon: string
    /** The first page listed under the group, which opening the group leads to. */
    to: string
    /** Every address the group covers: what it declares and where each page under it leads. */
    prefix: string[]
    badge: number
    onboarding?: string
    /**
     * The onboarding marks of everything under the group, at any depth. While its pages are not on the
     * page, the guide points at the group that holds the one it is after.
     */
    holds: string[]
    inline: boolean
    children: NavNode[]
}

export type NavNode = NavPage | NavGroup
