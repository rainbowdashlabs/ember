/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  active: boolean
  variant?: 'text' | 'banner'
}>()
</script>

<template>
  <span :class="active ? ['pride-flag', variant === 'banner' ? 'pride-banner' : 'pride-text'] : ''"><slot/></span>
</template>

<style scoped>
.pride-flag {
  background:
    linear-gradient(150deg,
      #1a1a1a 0%, #1a1a1a 8%,
      #5c3018 8%, #5c3018 16%,
      #3eafc9 16%, #3eafc9 24%,
      #d16287 24%, #d16287 32%,
      transparent 32%
    ),
    linear-gradient(180deg,
      #b5121b 0%, #b5121b 16.66%,
      #c66a12 16.66%, #c66a12 33.33%,
      #c6a818 33.33%, #c6a818 50%,
      #1a7a2e 50%, #1a7a2e 66.66%,
      #1a4db5 66.66%, #1a4db5 83.33%,
      #6b1a8a 83.33%, #6b1a8a 100%
    );
}

.pride-text {
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  -webkit-text-stroke: 0.5px rgba(0, 0, 0, 0.4);
  paint-order: stroke fill;
}

.pride-banner {
  padding: 0.1em 0.4em;
  border-radius: 0.25em;
  -webkit-text-fill-color: rgba(255, 255, 255, 0.85);
  text-shadow: 0 0 3px rgba(0, 0, 0, 0.8), 0 2px 6px rgba(0, 0, 0, 0.6);
  -webkit-text-stroke: 1px rgba(0, 0, 0, 0.6);
  paint-order: stroke fill;
}
</style>
