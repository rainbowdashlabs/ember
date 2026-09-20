/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import AppIcon from '@/components/display/AppIcon.vue'
import {computed} from 'vue'
import {themeRevision} from '@/util/themeState'
import {outlineFor, type GlyphSurface} from '@/util/glyphOutline'
import type {Glyph} from '@/util/glyph'

/**
 * The one place a piece of gear's picture is drawn.
 *
 * <p>A chosen colour is painted on the shape itself and never behind a word, so no list of gear
 * becomes a question about whether its letters can still be read. Where the colour is too close to
 * the surface behind it, the shape is stroked in the contrasting neutral: the outline hugs the glyph
 * because what is rendered is an SVG path on a 512 unit box, so eighteen units is a hairline at any
 * size.
 *
 * <p>A row that paints its own background says so through {@code surface}, which is the only way the
 * outline can be right for a hovered or highlighted row.
 */
const props = defineProps<{
  glyph: Glyph
  /** The surface it sits on, which decides whether an outline is needed. */
  surface?: GlyphSurface
  size?: 'sm' | 'md' | 'lg'
}>()

const sizeClass = computed(() => {
  switch (props.size ?? 'md') {
    case 'sm':
      return 'text-xs'
    case 'lg':
      return 'text-xl'
    default:
      return 'text-base'
  }
})

const outline = computed(() => {
  // Read so the answer is taken again after a repaint, which moves every surface underneath it.
  void themeRevision.value
  return outlineFor(props.glyph.color, props.surface ?? 'page')
})

const style = computed(() => {
  const colour = props.glyph.color
  if (!colour) return undefined
  const stroke = outline.value
  return stroke
    ? {color: colour, stroke, strokeWidth: '18', paintOrder: 'stroke'}
    : {color: colour}
})
</script>

<template>
  <AppIcon
      :class="[sizeClass, props.glyph.color ? '' : 'text-(--text-muted)']"
      :icon="props.glyph.icon"
      :style="style"
      data-testid="gear-glyph"
  />
</template>
