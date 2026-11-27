/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SizeBadge from '@/components/badge/SizeBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MovementPieceBadge from '@/components/inventory/MovementPieceBadge.vue'
import GearGlyph from '@/components/inventory/GearGlyph.vue'
import {pieceGlyph, type GlyphOwner} from '@/util/glyph'
import type { InventoryArt, InventoryItem, InventorySize } from '@/api/generated/schema'

/**
 * The pieces nobody holds, each offered for handing out.
 *
 * <p>A piece an open movement carries is free on the shelf and spoken for all the same. It names its
 * movement instead of offering a hand-out the movement would refuse. A piece sorted into a kind shows
 * that kind and is drawn with its picture, so the right one can be picked from a drawer of
 * different things.
 */
const props = withDefaults(defineProps<{
  items: InventoryItem[]
  sizes: InventorySize[] | undefined
  /** The kinds of a drawer of different things, empty for every other inventory. */
  arts?: InventoryArt[]
  /** The inventory's own picture, for pieces whose kind has none. */
  inventoryGlyph?: GlyphOwner & {homogeneous?: boolean}
  containerPathById: Map<number, string>
  /** The movement carrying each piece an open movement carries. */
  movementItemMap?: ReadonlyMap<number, number>
}>(), {arts: () => [], movementItemMap: () => new Map(), inventoryGlyph: undefined})

defineEmits<{
  assign: [itemId: number]
}>()

const { t } = useI18n()

function sizeName(sizeId: number | null | undefined): string {
  if (!sizeId || !props.sizes) return ''
  return props.sizes.find(s => s.id === sizeId)?.label ?? ''
}

function artOf(artId: number | null | undefined): InventoryArt | undefined {
  if (!artId) return undefined
  return props.arts.find(art => art.id === artId)
}

function artName(artId: number | null | undefined): string {
  return artOf(artId)?.name ?? ''
}

function locationLabel(containerId: number | null | undefined): string {
  if (!containerId) return ''
  return props.containerPathById.get(containerId) ?? ''
}
</script>

<template>
  <template v-if="items.length > 0">
    <SubHeader>{{ t('inventory.detail.freeItems') }}</SubHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
      <NeutralContainer v-for="item in items" :key="item.id" class="flex items-center justify-between gap-2">
        <GearGlyph :glyph="pieceGlyph(artOf(item.artId), inventoryGlyph)"/>
        <div class="min-w-0 flex-1">
          <div class="text-sm font-medium">
            {{ item.name }}
            <SizeBadge v-if="sizeName(item.sizeId)">{{ sizeName(item.sizeId) }}</SizeBadge>
            <InfoBadge v-if="artName(item.artId)" class="ml-1">{{ artName(item.artId) }}</InfoBadge>
          </div>
          <div v-if="item.internalId" class="text-xs text-(--text-muted)">{{ item.internalId }}</div>
          <div v-if="locationLabel(item.containerId)" class="text-xs text-(--text-muted) flex items-center gap-1 mt-0.5">
            <font-awesome-icon :icon="['fas', 'box']" class="h-3 w-3"/>
            {{ locationLabel(item.containerId) }}
          </div>
        </div>
        <MovementPieceBadge v-if="movementItemMap.has(item.id)" :movement-id="movementItemMap.get(item.id)"/>
        <PrimaryButton v-else @click="$emit('assign', item.id)">
          {{ t('inventory.detail.assign') }}
        </PrimaryButton>
      </NeutralContainer>
    </div>
  </template>
</template>
