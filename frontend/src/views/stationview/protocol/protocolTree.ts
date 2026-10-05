/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {TestProtocolItem, TestProtocolSection} from '@/api/generated/schema'
import {maxPointsOf} from './protocolPoints'

/**
 * A protocol's sections and points read as the tree they form, at any depth. Every screen that walks a
 * protocol (the editor, grading, the evaluation) asks this instead of filtering by parent itself, so a
 * fourth level is read the same everywhere.
 */
export interface ProtocolTree {
    /** The sections directly under a parent, or the top level for {@code null}, in their order. */
    childrenOf(parentId: number | null): TestProtocolSection[]
    /** The points of one section, without those of its subsections, in their order. */
    itemsOf(sectionId: number): TestProtocolItem[]
    /** The points of a section and of every section under it. */
    itemsUnder(sectionId: number): TestProtocolItem[]
    /** A section and every section under it. */
    subtreeOf(sectionId: number): Set<number>
    /** What a section and everything under it can reach at most, bonus points left out. */
    maxPointsUnder(sectionId: number): number
}

/** Builds the tree of the given sections and points. */
export function protocolTree(sections: readonly TestProtocolSection[], items: readonly TestProtocolItem[]): ProtocolTree {
    const byPosition = <T extends {position: number}>(a: T, b: T) => a.position - b.position

    function childrenOf(parentId: number | null): TestProtocolSection[] {
        return sections.filter(section => (section.parentId ?? null) === parentId).sort(byPosition)
    }

    function itemsOf(sectionId: number): TestProtocolItem[] {
        return items.filter(item => item.sectionId === sectionId).sort(byPosition)
    }

    function itemsUnder(sectionId: number): TestProtocolItem[] {
        return [...itemsOf(sectionId), ...childrenOf(sectionId).flatMap(child => itemsUnder(child.id))]
    }

    function subtreeOf(sectionId: number): Set<number> {
        const found = new Set<number>([sectionId])
        for (const child of childrenOf(sectionId)) for (const id of subtreeOf(child.id)) found.add(id)
        return found
    }

    return {
        childrenOf,
        itemsOf,
        itemsUnder,
        subtreeOf,
        maxPointsUnder: sectionId => maxPointsOf(itemsUnder(sectionId)),
    }
}
