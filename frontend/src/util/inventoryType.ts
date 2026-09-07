/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Component} from 'vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import {InventoryTypes} from '@/api/inventory'

/**
 * Translation function shape compatible with the {@code t} returned by
 * {@code useI18n()} - callers pass {@code t} directly.
 */
export type InventoryTypeTranslator = (key: string) => string

/**
 * Returns the localized label for the given {@link InventoryTypes} value, or an
 * empty string when the type is missing.
 */
export function inventoryTypeLabel(t: InventoryTypeTranslator, type?: string | null): string {
    if (!type) return ''
    return t(`inventory.manage.type.${type}`)
}

/**
 * Returns the badge component that visually represents the given
 * {@link InventoryTypes} value. Unknown values fall back to
 * {@link SecondaryBadge}.
 */
export function inventoryTypeBadge(type?: string | null): Component {
    switch (type) {
        case InventoryTypes.INTERNAL:
            return InfoBadge
        case InventoryTypes.EXTERNAL:
            return SecondaryBadge
        case InventoryTypes.MIXED:
            return SuccessBadge
        default:
            return SecondaryBadge
    }
}

/**
 * The badge for who owns one particular piece.
 *
 * <p>Separate from the inventory's own badge on purpose. The inventory says which owners it may hold,
 * and in a mixed one that is "both", which says nothing about the row in front of you. Who owns the
 * piece is the half that decides where it goes and who has to confirm it.
 */
export function itemOwnerBadge(ownerKind?: string | null): Component {
    if (ownerKind === 'STATION') return InfoBadge
    if (ownerKind === 'PARTNER_STATION') return PrimaryBadge
    return SecondaryBadge
}

/** What to call that owner: the station itself, the body above it, or a partner it borrowed from. */
export function itemOwnerLabel(t: InventoryTypeTranslator, ownerKind?: string | null): string {
    if (!ownerKind) return ''
    if (ownerKind === 'STATION') return t('inventory.edit.ownerStation')
    if (ownerKind === 'PARTNER_STATION') return t('inventory.edit.ownerPartner')
    return t('inventory.edit.ownerCluster')
}
