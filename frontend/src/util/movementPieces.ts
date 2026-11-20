/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {MovementPurpose, MovementState, type MovementResponse} from '@/api/generated/schema'

/**
 * The pieces open movements carry, each with the movement that carries it.
 *
 * <p>A movement names a piece on either side: the one setting out and the one arriving, which may be
 * no more than promised. Both are spoken for while the movement is open, however free their custody
 * looks on the shelf.
 *
 * @param movements the movements to read, open or not
 * @return the movement id per piece
 */
export function piecesOnOpenMovements(movements: readonly MovementResponse[]): ReadonlyMap<number, number> {
    const byItem = new Map<number, number>()
    for (const movement of movements) {
        if (movement.state !== MovementState.OPEN) continue
        if (movement.itemId != null) byItem.set(movement.itemId, movement.id)
        if (movement.incomingItemId != null) byItem.set(movement.incomingItemId, movement.id)
    }
    return byItem
}

/**
 * The open movements bringing a member something they do not hold yet: a piece already named, or a
 * planned hand-out that names only the inventory and the size.
 *
 * <p>A movement that only takes something back brings nothing. One whose arriving piece is already
 * among what the member holds has arrived, even where a receipt is still to be confirmed.
 *
 * @param movements the movements to read
 * @param memberId  the member
 * @param heldIds   the pieces the member holds now
 * @return the movements still on their way to the member
 */
export function arrivingForMember(
    movements: readonly MovementResponse[],
    memberId: number,
    heldIds: ReadonlySet<number>,
): MovementResponse[] {
    return movements.filter(movement =>
        movement.state === MovementState.OPEN
        && movement.memberId === memberId
        && movement.purpose !== MovementPurpose.RETURN
        && (movement.incomingItemId == null || !heldIds.has(movement.incomingItemId)))
}

/**
 * Whether a movement on its way to a member stands in for one of their missing pieces.
 *
 * <p>A hand-out brings one more piece. An exchange brings one in place of another, so it fills a gap
 * only once the piece it swaps has left the member; until then they still hold it, and the count of
 * what they have already includes it.
 *
 * @param movement the movement on its way
 * @param heldIds  the pieces the member holds now
 * @return whether it counts against a gap
 */
export function fillsAGap(movement: MovementResponse, heldIds: ReadonlySet<number>): boolean {
    if (movement.purpose !== MovementPurpose.EXCHANGE) return true
    return movement.itemId == null || !heldIds.has(movement.itemId)
}
