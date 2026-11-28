/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, readonly} from 'vue'
import * as carts from '@/api/lendingCart'
import type {CartLineView, CartView, NewCartLine} from '@/api/generated/schema'
import {useSidebarCounts} from '@/composables/useSidebarCounts'

/** What the cart holds of one product: its lines and how many pieces they ask for together. */
export interface CartProductShare {
    lines: CartLineView[]
    quantity: number
}

function productKey(stationId: string, inventoryId: number): string {
    return `${stationId}:${inventoryId}`
}

/**
 * The station's cart, shared by the catalog, the product pages and the cart page, so a line put in on
 * one shows on the others and the sidebar count follows. Every change answers with the whole cart.
 */
export function useLendingCart() {
    const cart = useState<CartView | null>('useLendingCart', () => null)
    const {refresh: refreshCounts} = useSidebarCounts()

    async function apply(change: Promise<CartView>): Promise<CartView> {
        const next = await change
        cart.value = next
        void refreshCounts()
        return next
    }

    /**
     * Reads the cart where it was not read yet. For the badges of the catalog and the product pages,
     * which show without them where the cart cannot be read; the cart page reads it with load.
     */
    function ensureLoaded(): void {
        if (cart.value) return
        load().catch(() => {})
    }

    async function load(): Promise<CartView> {
        const next = await carts.getCart()
        cart.value = next
        return next
    }

    const lines = computed(() => cart.value?.lines ?? [])
    const stations = computed(() => new Set(lines.value.map(line => line.stationId)))
    const byProduct = computed(() => {
        const shares = new Map<string, CartProductShare>()
        for (const line of lines.value) {
            const key = productKey(line.stationId, line.inventoryId)
            const share = shares.get(key) ?? {lines: [], quantity: 0}
            share.lines.push(line)
            share.quantity += line.quantity
            shares.set(key, share)
        }
        return shares
    })

    /** What the cart holds of a product, or nothing. */
    function shareOf(stationId: string, inventoryId: number): CartProductShare | undefined {
        return byProduct.value.get(productKey(stationId, inventoryId))
    }

    return {
        cart: readonly(cart),
        lines,
        stations,
        shareOf,
        load,
        ensureLoaded,
        add: (line: NewCartLine) => apply(carts.addCartLine(line)),
        setQuantity: (lineId: number, quantity: number) => apply(carts.setCartQuantity(lineId, quantity)),
        remove: (lineId: number) => apply(carts.removeCartLine(lineId)),
        setDetails: (details: Parameters<typeof carts.setCartDetails>[0]) => apply(carts.setCartDetails(details)),
        /** Takes the answer of a change made elsewhere, such as sending, as the cart. */
        replace: (next: CartView | null) => {
            cart.value = next
            void refreshCounts()
        },
    }
}
