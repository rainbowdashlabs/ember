/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {RouteLocationRaw} from 'vue-router'
import AppIcon from '@/components/display/AppIcon.vue'

/**
 * The shape every button of the app shares. With `to` it is a link to that page that looks the same,
 * so a page a button opens also opens in a new tab with the middle button or the context menu; a
 * disabled one stays a button.
 */
const props = defineProps<{
  disabled?: boolean
  fullWidth?: boolean
  icon?: string | string[] | null
  compact?: boolean
  /** The page the button opens, which makes it a link. */
  to?: RouteLocationRaw | null
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()

const classes = computed(() => [
  props.fullWidth ? 'w-full justify-center' : '',
  props.compact ? 'px-2 py-1 text-xs' : 'px-3 py-1.5 text-sm',
  'inline-flex items-center whitespace-nowrap rounded-theme font-medium transition-all duration-150 active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100 disabled:hover:brightness-100',
])
</script>

<template>
  <NuxtLink v-if="to && !disabled" :to="to" :class="classes" @click="$emit('click', $event)">
    <AppIcon v-if="icon" :icon="icon" class="mr-1"/>
    <slot/>
  </NuxtLink>
  <button
      v-else
      type="button"
      :disabled="disabled"
      :class="classes"
      @click="$emit('click', $event)"
  >
    <AppIcon v-if="icon" :icon="icon" class="mr-1"/>
    <slot/>
  </button>
</template>
