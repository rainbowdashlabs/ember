/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
/** One setting inside a question's menu: what it is called on the left, what sets it on the right. */
defineProps<{
  label: string
}>()
</script>

<template>
  <label class="flex items-center justify-between gap-3 px-3 py-1.5 text-sm">
    <span class="text-(--text-muted)">{{ label }}</span>
    <slot/>
  </label>
</template>
