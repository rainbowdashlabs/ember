/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {InventorySize} from '@/api/generated/schema'

/** Why a piece is swapped. Anything that is none of the three common ones is said in words. */
export const ExchangeReason = {
    TOO_SMALL: 'TOO_SMALL',
    TOO_LARGE: 'TOO_LARGE',
    BROKEN: 'BROKEN',
    OTHER: 'OTHER',
} as const

export type ExchangeReasonName = (typeof ExchangeReason)[keyof typeof ExchangeReason]

export const EXCHANGE_REASONS: ExchangeReasonName[] = [
    ExchangeReason.TOO_SMALL,
    ExchangeReason.TOO_LARGE,
    ExchangeReason.BROKEN,
    ExchangeReason.OTHER,
]

/** How the wanted size stands to the one the piece has now. */
export type SizeRelation = 'larger' | 'smaller' | 'same' | 'unknown'

function positionOf(sizes: InventorySize[], sizeId: number | null | undefined): number {
    if (sizeId == null) return -1
    return sizes.findIndex(size => size.id === sizeId)
}

/**
 * The size a reason asks for, read off the order the inventory keeps its sizes in, smallest first.
 *
 * <p>Too small wants the next one along and too large the one before. Where there is none, or the piece
 * has no size the list knows, the current size stays: a guess that runs off the end is no guess, and the
 * reader corrects it in the same place they see it. Broken wants the same size back, and another reason
 * leaves whatever was chosen.
 *
 * @param reason  why the piece is swapped
 * @param sizes   the inventory's sizes, smallest first
 * @param current the size the piece has now
 * @param chosen  the size chosen so far, kept for a reason that says nothing about size
 * @return the size to ask for, or {@code null} for none
 */
export function guessSize(
    reason: ExchangeReasonName,
    sizes: InventorySize[],
    current: number | null | undefined,
    chosen: number | null,
): number | null {
    const at = positionOf(sizes, current)
    if (reason === ExchangeReason.OTHER) return chosen
    if (at < 0) return current ?? null
    if (reason === ExchangeReason.TOO_SMALL) return sizes[at + 1]?.id ?? current ?? null
    if (reason === ExchangeReason.TOO_LARGE) return sizes[at - 1]?.id ?? current ?? null
    return current ?? null
}

/**
 * Whether the reason asks for a size the inventory does not have: larger than the largest, or smaller
 * than the smallest.
 */
export function runsOffTheEnd(
    reason: ExchangeReasonName,
    sizes: InventorySize[],
    current: number | null | undefined,
): boolean {
    const at = positionOf(sizes, current)
    if (at < 0) return false
    if (reason === ExchangeReason.TOO_SMALL) return at === sizes.length - 1
    if (reason === ExchangeReason.TOO_LARGE) return at === 0
    return false
}

/** How the wanted size stands to the current one, or unknown where either is not on the list. */
export function sizeRelation(
    sizes: InventorySize[],
    current: number | null | undefined,
    wanted: number | null,
): SizeRelation {
    const from = positionOf(sizes, current)
    const to = positionOf(sizes, wanted)
    if (from < 0 || to < 0) return 'unknown'
    if (to > from) return 'larger'
    if (to < from) return 'smaller'
    return 'same'
}

/**
 * What is written on the movement as its reason: the reason's short name, followed by the note where one
 * was given. Another reason is only its words, and without them there is no reason at all.
 *
 * @param reason the reason chosen, or {@code null} while none is
 * @param label  the reason's short name
 * @param note   what was typed
 * @return the reason text, empty while it is not complete
 */
export function reasonText(reason: ExchangeReasonName | null, label: string, note: string): string {
    const words = note.trim()
    if (reason === null) return ''
    if (reason === ExchangeReason.OTHER) return words
    return words ? `${label} - ${words}` : label
}
