/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {PlaceholderCategory, Placeholder} from '@/api/generated/schema'

/** A step of the picker that leads further: a category, the profile, a heading of the form, a pronoun role. */
export interface PlaceholderBranch {
    kind: 'branch'
    name: string
    children: PlaceholderNode[]
}

/** A step of the picker that inserts a placeholder. */
export interface PlaceholderLeaf {
    kind: 'leaf'
    name: string
    placeholder: Placeholder
}

export type PlaceholderNode = PlaceholderBranch | PlaceholderLeaf

/** A category of the picker, the first step of every path. */
export interface PlaceholderCategoryBranch extends PlaceholderBranch {
    category: PlaceholderCategory
}

/** What a place in the editor may insert. */
export interface PlaceholderOffer {
    /** A legal template names people by their official names, so the name a member is called by is left out. */
    legal: boolean
}

/**
 * The placeholders a place in the editor offers.
 *
 * @param placeholders the placeholders the template can name
 * @param offer        what may stand there
 */
export function offeredPlaceholders(placeholders: readonly Placeholder[], offer: PlaceholderOffer): Placeholder[] {
    return placeholders.filter(placeholder => !(offer.legal && placeholder.informal))
}

/**
 * The placeholders a template can name: the values of an appointment only where the template is for
 * appointments, since only then a document is generated for one and they are filled.
 *
 * @param placeholders    every placeholder of the station
 * @param forAppointments whether appointments may ask for the template
 */
export function placeholdersOfTemplate(placeholders: readonly Placeholder[], forAppointments: boolean): Placeholder[] {
    return placeholders.filter(placeholder => forAppointments || !placeholder.eventOnly)
}

/**
 * The placeholders as the path the picker walks: one branch per category, below it the steps of each
 * placeholder's path in the order the station's catalogue lists them, and the placeholder at the end.
 *
 * @param placeholders the placeholders offered, in the catalogue's order
 * @returns the categories that hold any
 */
export function placeholderTree(placeholders: readonly Placeholder[]): PlaceholderCategoryBranch[] {
    const categories: PlaceholderCategoryBranch[] = []
    for (const placeholder of placeholders) {
        const [first, ...steps] = placeholder.path
        const leafName = steps.pop()
        if (first === undefined || leafName === undefined) continue
        let category = categories.find(candidate => candidate.category === placeholder.category)
        if (!category) {
            category = {kind: 'branch', name: first, category: placeholder.category, children: []}
            categories.push(category)
        }
        const branch = steps.reduce<PlaceholderBranch>((parent, name) => branchIn(parent, name), category)
        branch.children.push({kind: 'leaf', name: leafName, placeholder})
    }
    return categories
}

function branchIn(parent: PlaceholderBranch, name: string): PlaceholderBranch {
    const found = parent.children.find((child): child is PlaceholderBranch => child.kind === 'branch' && child.name === name)
    if (found) return found
    const created: PlaceholderBranch = {kind: 'branch', name, children: []}
    parent.children.push(created)
    return created
}

/**
 * The branches along a trail of names, starting at a category.
 *
 * @param categories the categories of the tree
 * @param trail      the names of the steps taken, the category first
 * @returns the branches the trail reaches, as far as it is still there
 */
export function branchesAlong(categories: readonly PlaceholderCategoryBranch[], trail: readonly string[]): PlaceholderBranch[] {
    const [first, ...rest] = trail
    const category = categories.find(candidate => candidate.name === first)
    if (!category) return []
    const branches: PlaceholderBranch[] = [category]
    let current: PlaceholderBranch = category
    for (const name of rest) {
        const next = current.children
            .find((child): child is PlaceholderBranch => child.kind === 'branch' && child.name === name)
        if (!next) break
        branches.push(next)
        current = next
    }
    return branches
}

/**
 * The placeholders a search finds: every word of it has to stand in the label, a step of the path or
 * the key, whatever the case.
 *
 * @param placeholders the placeholders offered
 * @param query        what was typed
 * @returns the matches in the catalogue's order, none for an empty search
 */
export function searchPlaceholders(placeholders: readonly Placeholder[], query: string): Placeholder[] {
    const words = query.toLocaleLowerCase().split(/\s+/).filter(word => word.length > 0)
    if (words.length === 0) return []
    return placeholders.filter(placeholder => {
        const text = [placeholder.label, placeholder.key, ...placeholder.path].join(' ').toLocaleLowerCase()
        return words.every(word => text.includes(word))
    })
}
