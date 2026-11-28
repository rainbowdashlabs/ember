/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {
    BorrowAgainResult,
    CartDetails,
    CartLineCheck,
    CartView,
    CheckoutView,
    NewCartLine,
} from './generated/schema'

/** The station's cart of gear to borrow from its partners. */
export async function getCart(): Promise<CartView> {
    const res = await client.get<CartView>('/lending/cart')
    return res.data
}

/** Sets the days, the occasion and the note the cart asks every lending station with. */
export async function setCartDetails(details: CartDetails): Promise<CartView> {
    const res = await client.put<CartView>('/lending/cart', details)
    return res.data
}

/** Puts gear in the cart; the same count adds up, the same piece stays one. */
export async function addCartLine(line: NewCartLine): Promise<CartView> {
    const res = await client.post<CartView>('/lending/cart/lines', line)
    return res.data
}

export async function setCartQuantity(lineId: number, quantity: number): Promise<CartView> {
    const res = await client.put<CartView>(`/lending/cart/lines/${lineId}`, {quantity})
    return res.data
}

export async function removeCartLine(lineId: number): Promise<CartView> {
    const res = await client.delete<CartView>(`/lending/cart/lines/${lineId}`)
    return res.data
}

/** What of the cart the lending stations have free on its days. */
export async function checkCart(): Promise<CartLineCheck[]> {
    const res = await client.get<CartLineCheck[]>('/lending/cart/check')
    return res.data
}

/** Sends the cart as one request per lending station. */
export async function checkoutCart(): Promise<CheckoutView> {
    const res = await client.post<CheckoutView>('/lending/cart/checkout')
    return res.data
}

/** Puts the lines of an answered request back in the cart. */
export async function borrowAgain(requestId: number): Promise<BorrowAgainResult> {
    const res = await client.post<BorrowAgainResult>(`/lending/requests/${requestId}/borrow-again`)
    return res.data
}
