/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Component} from 'vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import {InventoryTypes} from '@/api/types'

/**
 * Translation function shape compatible with the {@code t} returned by
 * {@code useI18n()} — callers pass {@code t} directly.
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
