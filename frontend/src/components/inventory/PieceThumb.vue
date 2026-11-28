/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import AuthImage from '@/components/display/AuthImage.vue'
import GearGlyph from '@/components/inventory/GearGlyph.vue'
import type {Glyph} from '@/util/glyph'

/**
 * A small picture of a piece, a kind or an inventory: its photo where it has one, otherwise its icon.
 * The icon also stands in while the photo loads and where it cannot be loaded.
 */
withDefaults(defineProps<{
  /** The address of the photo, or null to draw the icon. */
  pictureSrc?: string | null
  glyph: Glyph
}>(), {pictureSrc: null})
</script>

<template>
  <AuthImage v-if="pictureSrc" :src="pictureSrc" alt="" data-testid="piece-thumb-photo"
             class="h-8 w-8 shrink-0 rounded-theme border border-(--border) object-cover">
    <template #error><GearGlyph :glyph="glyph"/></template>
    <template #loading><GearGlyph :glyph="glyph"/></template>
  </AuthImage>
  <GearGlyph v-else :glyph="glyph"/>
</template>
