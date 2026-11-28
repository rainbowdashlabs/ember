/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {CatalogProduct, CatalogShare} from '@/api/generated/schema'
import type {ShareTarget} from '@/api/lending'

/** One row of the catalog: a product, one of its kinds, or one of its pieces. */
export interface CatalogRow {
    key: string
    target: ShareTarget
    id: number
    inventoryId: number
    artId: number | null
    name: string
    detail: string
    description: string
    pictureKey: string | null
    share: CatalogShare | null
    visible: boolean | null
    /** How far the row sits below its product: 0 for the product, 1 for a kind, 2 for a piece of a kind. */
    depth: number
}

/** What one share control can set. A product has no "as product"; kinds and pieces have no "none". */
export type ShareChoice = 'none' | 'inherit' | 'all' | 'some' | 'withheld'

/** The filters the catalog offers. */
export type CatalogFilter = 'all' | 'offered' | 'notOffered' | 'noDescription' | 'noPicture'

export const CATALOG_FILTERS: readonly CatalogFilter[] = ['all', 'offered', 'notOffered', 'noDescription', 'noPicture']

/** The choice a rule written at one level stands for. */
export function choiceOf(share: CatalogShare | null, target: ShareTarget): ShareChoice {
    if (!share) return target === 'inventory' ? 'none' : 'inherit'
    if (share.grant === 'WITHHOLD') return target === 'inventory' ? 'none' : 'withheld'
    return share.scope === 'SPECIFIC' ? 'some' : 'all'
}

/** The choices a level offers, in the order they are listed. */
export function choicesFor(target: ShareTarget): ShareChoice[] {
    return target === 'inventory' ? ['none', 'all', 'some'] : ['inherit', 'all', 'some', 'withheld']
}

/**
 * The product row and the rows below it: each kind followed by its pieces, and in an inventory of
 * different things the pieces without a kind last.
 */
export function rowsOf(product: CatalogProduct): CatalogRow[] {
    const rows: CatalogRow[] = [{
        key: `inventory:${product.inventoryId}`, target: 'inventory', id: product.inventoryId, inventoryId: product.inventoryId,
        artId: null, name: product.name, detail: '', description: product.description, pictureKey: product.pictureKey,
        share: product.share, visible: product.visible, depth: 0,
    }]
    const pieceRow = (piece: CatalogProduct['pieces'][number], depth: number): CatalogRow => ({
        key: `item:${piece.itemId}`, target: 'item', id: piece.itemId, inventoryId: product.inventoryId, artId: piece.artId,
        name: piece.name, detail: piece.internalId ?? '', description: piece.description, pictureKey: piece.pictureKey,
        share: piece.share, visible: piece.visible, depth,
    })
    for (const kind of product.kinds) {
        rows.push({
            key: `art:${kind.artId}`, target: 'art', id: kind.artId, inventoryId: product.inventoryId, artId: kind.artId,
            name: kind.name, detail: '', description: kind.description, pictureKey: kind.pictureKey, share: kind.share,
            visible: kind.visible, depth: 1,
        })
        rows.push(...product.pieces.filter(piece => piece.artId === kind.artId).map(piece => pieceRow(piece, 2)))
    }
    const kinds = new Set(product.kinds.map(kind => kind.artId))
    rows.push(...product.pieces.filter(piece => piece.artId == null || !kinds.has(piece.artId)).map(piece => pieceRow(piece, 1)))
    return rows
}

/** Whether a product is offered to some partner, as the server resolved it through the narrowest rules. */
export function isOffered(product: CatalogProduct): boolean {
    return product.offered
}

export function matchesFilter(product: CatalogProduct, filter: CatalogFilter): boolean {
    switch (filter) {
        case 'offered': return isOffered(product)
        case 'notOffered': return !isOffered(product)
        case 'noDescription': return !product.description.trim()
        case 'noPicture': return !product.pictureKey
        default: return true
    }
}

/** How many products are offered, and how many products lack a text or a photo, as the catalog's filters count them. */
export function offerSummary(products: readonly CatalogProduct[]) {
    return {
        offered: products.filter(isOffered).length,
        noDescription: products.filter(product => matchesFilter(product, 'noDescription')).length,
        noPicture: products.filter(product => matchesFilter(product, 'noPicture')).length,
    }
}
