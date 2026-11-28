/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {OfferProduct, OfferShare} from '@/api/generated/schema'
import type {ShareTarget} from '@/api/lending'

/** One row of the offer page: a product, one of its kinds, or one of its pieces. */
export interface OfferRow {
    key: string
    target: ShareTarget
    id: number
    inventoryId: number
    artId: number | null
    name: string
    detail: string
    description: string
    pictureKey: string | null
    share: OfferShare | null
    visible: boolean | null
    /** How far the row sits below its product: 0 for the product, 1 for a kind, 2 for a piece of a kind. */
    depth: number
}

/** What one share control can set. A product has no "as product"; kinds and pieces have no "none". */
export type ShareChoice = 'none' | 'inherit' | 'all' | 'some' | 'withheld'

/** The filters the offer page offers. */
export type OfferFilter = 'all' | 'offered' | 'notOffered' | 'noDescription' | 'noPicture'

export const OFFER_FILTERS: readonly OfferFilter[] = ['all', 'offered', 'notOffered', 'noDescription', 'noPicture']

/** The choice a rule written at one level stands for. */
export function choiceOf(share: OfferShare | null, target: ShareTarget): ShareChoice {
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
export function rowsOf(product: OfferProduct): OfferRow[] {
    const rows: OfferRow[] = [{
        key: `inventory:${product.inventoryId}`, target: 'inventory', id: product.inventoryId, inventoryId: product.inventoryId,
        artId: null, name: product.name, detail: '', description: product.description, pictureKey: product.pictureKey,
        share: product.share, visible: product.visible, depth: 0,
    }]
    const pieceRow = (piece: OfferProduct['pieces'][number], depth: number): OfferRow => ({
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
export function isOffered(product: OfferProduct): boolean {
    return product.offered
}

export function matchesFilter(product: OfferProduct, filter: OfferFilter): boolean {
    switch (filter) {
        case 'offered': return isOffered(product)
        case 'notOffered': return !isOffered(product)
        case 'noDescription': return !product.description.trim()
        case 'noPicture': return !product.pictureKey
        default: return true
    }
}

/** How many products are offered, and how many products lack a text or a photo, as the offer page's filters count them. */
export function offerSummary(products: readonly OfferProduct[]) {
    return {
        offered: products.filter(isOffered).length,
        noDescription: products.filter(product => matchesFilter(product, 'noDescription')).length,
        noPicture: products.filter(product => matchesFilter(product, 'noPicture')).length,
    }
}
