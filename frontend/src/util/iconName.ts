/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * How an icon's name says which set it belongs to.
 *
 * <p>One place for the two rules, because they have to agree: a name is turned into a set here and
 * back into a component name here, and a spelling that survives one direction but not the other
 * resolves to an element nobody registered and draws nothing at all. PhTShirt is the case that
 * taught this: it kebabs to t-shirt only if a run of capitals is split, and tshirt would come back
 * as PhTshirt.
 */

/** What marks a stored name as Phosphor's. A name without it is FontAwesome's. */
export const PHOSPHOR_PREFIX = 'ph:'

/** The prefixes the icon components speak. */
export type IconSet = 'fas' | 'fab' | 'ph'

/**
 * The set a stored name belongs to, and what it is called inside that set.
 *
 * @param stored the name as a kind, an inventory or a catalogue entry keeps it
 */
export function iconSetOf(stored: string): {set: IconSet; name: string} {
    return stored.startsWith(PHOSPHOR_PREFIX)
        ? {set: 'ph', name: stored.slice(PHOSPHOR_PREFIX.length)}
        : {set: 'fas', name: stored}
}

/**
 * The globally registered component a Phosphor name stands for.
 *
 * @param name the kebab-cased name, as a template or the gear catalogue writes it
 */
export function phosphorComponentOf(name: string): string {
    return `Ph${name.split('-').map(part => part.charAt(0).toUpperCase() + part.slice(1)).join('')}`
}
