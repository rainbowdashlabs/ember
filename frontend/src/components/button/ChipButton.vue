/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * A small label that says something about what it sits on and opens the place where that is changed.
 *
 * <p>Looks like a badge and behaves like a button: a setting tucked behind a menu shows here once it
 * differs from its default, and clicking it leads back to where it is set. A badge alone would say
 * the setting is there without saying where to change it.
 */
defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
      type="button"
      class="inline-flex items-center gap-1 rounded-full border border-(--border) bg-secondary/10 px-2.5 py-0.5 text-xs font-medium text-(--text) transition-colors hover:border-primary cursor-pointer"
      @click="$emit('click', $event)"
  >
    <slot/>
  </button>
</template>
