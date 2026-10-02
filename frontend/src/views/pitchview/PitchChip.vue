/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import type {Accent} from './pitchTypes'
import {accentBorder, accentSoftBg, accentText} from './pitchAccents'
import PillBadge from '@/components/badge/PillBadge.vue'

const props = withDefaults(defineProps<{
  accent?: Accent
}>(), {accent: 'primary'})

const classes = computed(() =>
    [accentBorder(props.accent), accentSoftBg(props.accent), accentText(props.accent)].join(' '))
</script>

<template>
  <PillBadge :class="classes" class="border px-3 py-1 text-xs font-semibold tracking-wide">
    <slot/>
  </PillBadge>
</template>
