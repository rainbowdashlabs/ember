/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import type {Accent} from './pitchTypes'
import {accentBorder, accentSoftBg, accentText} from './pitchAccents'

const props = withDefaults(defineProps<{
  accent?: Accent
}>(), {accent: 'primary'})

const classes = computed(() =>
    [accentBorder(props.accent), accentSoftBg(props.accent), accentText(props.accent)].join(' '))
</script>

<template>
  <span :class="classes"
        class="inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold tracking-wide">
    <slot/>
  </span>
</template>
