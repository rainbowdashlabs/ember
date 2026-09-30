/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * The tag a member shows beside their name, in the colour its station gave it on a pale ground of
 * the same colour.
 */
defineProps<{
  tag: {name: string, color: string}
}>()
</script>

<template>
  <span
      class="inline-flex items-center rounded-full px-1.5 py-0.5 text-[10px] font-medium leading-none"
      :style="{ backgroundColor: tag.color + '20', color: tag.color }"
  >{{ tag.name }}</span>
</template>
