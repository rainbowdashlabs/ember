/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import type {RouteLocationRaw} from 'vue-router'
import AppIcon from '@/components/display/AppIcon.vue'
import BareButton from './BareButton.vue'

/**
 * One of the round buttons in the bottom right corner: the bug report, the help center, the way back
 * into the setup, the developer tools.
 *
 * <p>They stand side by side, so they share one size, one border and one box for what they show: an icon
 * or, given in the slot, a picture of the same size. Only the colour tells them apart, which is what makes
 * each one recognisable at a glance. With `to` it is a link, as the help center is.
 */
const props = withDefaults(defineProps<{
  /** What the button does, shown on hover and read out by screen readers. */
  label: string
  icon?: string[]
  /** The colour: neutral for the quiet ones, error for the bug report, secondary for the developer tools. */
  tone?: 'neutral' | 'error' | 'secondary'
  to?: RouteLocationRaw
  target?: string
}>(), {
  icon: undefined,
  tone: 'neutral',
  to: undefined,
  target: undefined,
})

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

const TONES = {
  neutral: 'border-(--border) bg-(--bg) text-primary hover:border-primary',
  error: 'border-transparent bg-error text-error-text hover:bg-error/80',
  secondary: 'border-transparent bg-secondary text-secondary-text hover:bg-secondary/80',
} as const

const classes = computed(() => [
  'flex h-10 w-10 shrink-0 items-center justify-center rounded-full border shadow-lg no-underline transition-colors',
  TONES[props.tone],
])
</script>

<template>
  <router-link v-if="to" :to="to" :target="target" :title="label" :aria-label="label" :class="classes">
    <span class="flex h-5 w-5 items-center justify-center">
      <slot><AppIcon v-if="icon" :icon="icon" class="h-5 w-5"/></slot>
    </span>
  </router-link>
  <BareButton v-else :title="label" :aria-label="label" :class="classes" @click="(event: MouseEvent) => emit('click', event)">
    <span class="flex h-5 w-5 items-center justify-center">
      <slot><AppIcon v-if="icon" :icon="icon" class="h-5 w-5"/></slot>
    </span>
  </BareButton>
</template>
