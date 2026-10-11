/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {RouteLocationRaw} from 'vue-router'
import AppIcon from '@/components/display/AppIcon.vue'

/**
 * A button that is only an icon, named by its label for screen readers and on hover.
 *
 * <p>With `to` it is a link to that page rather than a button, looking the same: a page opened from a
 * list then opens in a new tab with the middle button or the context menu, as any link does.
 */
defineProps<{
  icon: string | string[]
  disabled?: boolean
  label: string
  /** The page the button opens, which makes it a link; a disabled one stays a button. */
  to?: RouteLocationRaw | null
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()

const CLASSES = 'inline-flex items-center justify-center p-2 rounded-theme transition-all duration-150 active:scale-90 disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100'
</script>

<template>
  <NuxtLink
      v-if="to && !disabled"
      :to="to"
      :aria-label="label"
      :title="label"
      :class="CLASSES"
      @click="$emit('click', $event)"
  >
    <slot>
      <AppIcon :icon="icon" class="h-4 w-4"/>
    </slot>
  </NuxtLink>
  <button
      v-else
      type="button"
      :aria-label="label"
      :disabled="disabled"
      :title="label"
      :class="CLASSES"
      @click="$emit('click', $event)"
  >
    <slot>
      <AppIcon :icon="icon" class="h-4 w-4"/>
    </slot>
  </button>
</template>
