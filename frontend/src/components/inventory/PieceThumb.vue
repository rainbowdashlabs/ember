/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import AuthImage from '@/components/display/AuthImage.vue'
import GearGlyph from '@/components/inventory/GearGlyph.vue'
import type {Glyph} from '@/util/glyph'

/**
 * A small picture of a piece, a kind or an inventory: its photo where it has one, otherwise its icon.
 * The icon also stands in while the photo loads and where it cannot be loaded.
 */
const props = withDefaults(defineProps<{
  /** The address of the photo, or null to draw the icon. */
  pictureSrc?: string | null
  glyph: Glyph
  /**
   * How the picture fills its place: `thumb` as a small square in a row, `cover` filling a frame
   * and cut to it, `contain` filling a frame whole. The icon grows with the frame.
   */
  fit?: 'thumb' | 'cover' | 'contain'
}>(), {pictureSrc: null, fit: 'thumb'})

const FIT_CLASS = {
  thumb: 'h-8 w-8 shrink-0 rounded-theme border border-(--border) object-cover',
  cover: 'h-full w-full object-cover',
  contain: 'h-full w-full object-contain',
} as const

const glyphSize = computed(() => (props.fit === 'thumb' ? undefined : 'lg'))
</script>

<template>
  <AuthImage v-if="pictureSrc" :src="pictureSrc" alt="" data-testid="piece-thumb-photo" :class="FIT_CLASS[props.fit]">
    <template #error><GearGlyph :glyph="glyph" :size="glyphSize"/></template>
    <template #loading><GearGlyph :glyph="glyph" :size="glyphSize"/></template>
  </AuthImage>
  <GearGlyph v-else :glyph="glyph" :size="glyphSize"/>
</template>
