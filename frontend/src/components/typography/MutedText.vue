/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Muted body text. `size="base"` inherits the surrounding font size and is the variant for
 * muted copy that must not shrink relative to its container.
 */
withDefaults(defineProps<{
  size?: 'xs' | 'sm' | 'base'
  tag?: 'span' | 'p' | 'div'
}>(), {
  size: 'xs',
  tag: 'span',
})
</script>

<template>
  <component :is="tag" :class="{'text-xs': size === 'xs', 'text-sm': size === 'sm'}" class="text-(--text-muted)">
    <slot/>
  </component>
</template>
