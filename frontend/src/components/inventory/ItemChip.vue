/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import GearGlyph from '@/components/inventory/GearGlyph.vue'
import ItemChipSize from '@/components/inventory/ItemChipSize.vue'
import type {GlyphSurface} from '@/util/glyphOutline'
import type {Glyph} from '@/util/glyph'

/**
 * What is known about one piece when it is named in a list.
 *
 * <p>One shape for every call site, so a row of a picker, the chip a filled-in form shows and a row
 * of the movement queue say the same things in the same order.
 *
 * @property glyph         the picture it is drawn with
 * @property name          what the piece is called
 * @property internalId    what is written on it, absent when nothing is
 * @property sizeName      its size, absent where the inventory keeps none
 * @property replacedSize  the size it takes the place of, where an exchange swaps one size for another
 * @property sizeWanted    whether its size is the one asked for, which draws the size as a fit
 * @property inventoryName which inventory it is out of, absent where the reader already knows
 * @property location      where it is or who has it, already put into words by the caller
 */
export interface ItemChipSource {
  glyph: Glyph
  name: string
  internalId?: string | null
  sizeName?: string | null
  replacedSize?: string | null
  sizeWanted?: boolean
  inventoryName?: string | null
  location?: string | null
}

/**
 * One piece of gear, named the same way everywhere.
 *
 * <p>The first line is what the piece is: its picture, its name, its size and the identifier written
 * on it. The size is a chip of its own rather than a word glued to the name with a separator, because
 * a size is a fact about the piece and not part of what it is called. An exchange shows the size it
 * hands in before the size it asks for, with an arrow between, so the swap reads at a glance. Where
 * the line runs out, the size and the identifier wrap under the name rather than being squeezed off
 * the end of it.
 *
 * <p>The second line is where it is, which a reader only needs where a list spans more than one
 * inventory or more than one holder.
 */
const props = defineProps<{
  source: ItemChipSource
  /** The surface it is drawn on, so a highlighted row gets the outline it needs. */
  surface?: GlyphSurface
}>()

const hasLocation = () => Boolean(props.source.inventoryName || props.source.location)
</script>

<template>
  <span class="flex min-w-0 flex-1 items-center gap-2" data-testid="item-chip">
    <GearGlyph :glyph="props.source.glyph" :surface="props.surface"/>
    <span class="flex min-w-0 flex-col items-start text-left">
      <span class="flex min-w-0 flex-wrap items-center gap-x-1.5 gap-y-0.5">
        <span class="truncate font-medium">{{ props.source.name }}</span>
        <template v-if="props.source.replacedSize">
          <ItemChipSize :label="props.source.replacedSize" data-testid="item-chip-replaced-size"/>
          <font-awesome-icon :icon="['fas', 'arrow-right']" class="shrink-0 text-xs text-(--text-muted)"/>
        </template>
        <ItemChipSize
            v-if="props.source.sizeName"
            :label="props.source.sizeName"
            :wanted="props.source.sizeWanted"
            data-testid="item-chip-size"
        />
        <span
            v-if="props.source.internalId"
            class="shrink-0 font-mono text-xs text-(--text-muted)"
            data-testid="item-chip-id"
        >{{ props.source.internalId }}</span>
      </span>
      <span v-if="hasLocation()" class="truncate text-xs text-(--text-muted)">
        <template v-if="props.source.inventoryName">{{ props.source.inventoryName }}</template>
        <template v-if="props.source.inventoryName && props.source.location"> · </template>
        <template v-if="props.source.location">{{ props.source.location }}</template>
      </span>
    </span>
  </span>
</template>
