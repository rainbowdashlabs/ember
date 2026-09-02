/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import PrimaryBadge from '@/components/badge/PrimaryBadge.vue'
import {InventoryTypes, type InventoryTypeName} from '@/api/inventory'

/**
 * What an inventory is, said in labels rather than in a sentence, so a list of a dozen of them can
 * be read without opening any.
 *
 * <p>Three things stand here: who owns it, whether it uses sizes, and which of the two kinds it is.
 * Sizes appear only where there are sizes, because "no sizes" is not a property anybody looks for.
 *
 * <p>A collection also says how many kinds are defined in it, which is the number that tells a box
 * somebody has sorted from a box nobody has. A uniform inventory has no such number: there is only
 * ever one thing in it, and writing a one would be noise.
 */
const props = defineProps<{
  inventoryType: InventoryTypeName | null | undefined
  hasSizes: boolean
  homogeneous: boolean
  /** How many kinds are defined in the inventory, or {@code null} where nobody has counted. */
  artCount?: number | null
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center gap-1.5">
    <InfoBadge data-testid="inventory-badge-type">
      {{ t('inventory.manage.type.' + (props.inventoryType ?? InventoryTypes.INTERNAL)) }}
    </InfoBadge>
    <PrimaryBadge data-testid="inventory-badge-kind">
      {{ props.homogeneous ? t('inventory.manage.kindStockName') : t('inventory.manage.kindCollectionName') }}
    </PrimaryBadge>
    <SecondaryBadge v-if="props.hasSizes" data-testid="inventory-badge-sizes">
      {{ t('inventory.manage.withSizes') }}
    </SecondaryBadge>
    <SecondaryBadge v-if="!props.homogeneous && props.artCount != null" data-testid="inventory-badge-arts">
      {{ props.artCount === 1 ? t('inventory.manage.artCountOne') : t('inventory.manage.artCount', {count: props.artCount}) }}
    </SecondaryBadge>
  </div>
</template>
