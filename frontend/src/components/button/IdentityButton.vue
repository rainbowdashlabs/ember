/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
      type="button"
      class="flex items-center gap-2 rounded-theme px-2 py-1 text-sm text-(--text) hover:bg-primary/5 transition-colors cursor-pointer"
      @click="$emit('click', $event)"
  >
    <slot/>
  </button>
</template>
