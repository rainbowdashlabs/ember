/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import GearGlyph from '@/components/inventory/GearGlyph.vue'
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
 * @property inventoryName which inventory it is out of, absent where the reader already knows
 * @property location      where it is or who has it, already put into words by the caller
 */
export interface ItemChipSource {
  glyph: Glyph
  name: string
  internalId?: string | null
  sizeName?: string | null
  inventoryName?: string | null
  location?: string | null
}

/**
 * One piece of gear, named the same way everywhere.
 *
 * <p>The first line is what the piece is: its picture, its name, its size and the identifier written
 * on it. The size is a chip of its own rather than a word glued to the name with a separator, because
 * a size is a fact about the piece and not part of what it is called.
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
      <span class="flex min-w-0 items-center gap-1.5">
        <span class="truncate font-medium">{{ props.source.name }}</span>
        <span
            v-if="props.source.sizeName"
            class="shrink-0 rounded-full bg-(--bg-accent) px-1.5 py-0.5 text-xs text-(--text-muted)"
            data-testid="item-chip-size"
        >{{ props.source.sizeName }}</span>
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
