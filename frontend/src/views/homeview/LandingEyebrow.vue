/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
withDefaults(defineProps<{
  marginBottom?: string
  opacity?: number
}>(), {
  marginBottom: '0.75rem',
  opacity: 0.55,
})
</script>

<template>
  <div class="eyebrow" :style="{marginBottom, opacity}">
    <slot/>
  </div>
</template>

<style scoped>
.eyebrow {
  font-family: 'Bitter', Georgia, serif;
  font-size: 0.7rem;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  font-weight: 700;
  color: inherit;
}
</style>
