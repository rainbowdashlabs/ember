/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * A value shown in place of its editor, which opens the editor on a click or on Enter and Space.
 *
 * <p>It stays a button for everybody, so a reader who may not edit it is told so by it being disabled
 * rather than by it vanishing from the tab order; the one who may sees it light up under the pointer.
 */
defineProps<{
  canEdit: boolean
}>()

const emit = defineEmits<{
  activate: []
}>()
</script>

<template>
  <div
      class="rounded-theme px-2 py-1 text-sm"
      :class="canEdit ? 'cursor-pointer hover:bg-(--bg-accent)' : ''"
      role="button"
      tabindex="0"
      :aria-disabled="!canEdit"
      @click.stop="emit('activate')"
      @keydown.enter.stop.prevent="emit('activate')"
      @keydown.space.stop.prevent="emit('activate')"
  >
    <slot/>
  </div>
</template>
