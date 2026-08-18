/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import type {PitchCard} from './pitchTypes'
import {accentSoftBg, accentText} from './pitchAccents'

const props = defineProps<{
  card: PitchCard
}>()

const iconClasses = computed(() =>
    [accentSoftBg(props.card.accent), accentText(props.card.accent)].join(' '))
</script>

<template>
  <NeutralContainer class="h-full space-y-2">
    <span :class="iconClasses"
          class="inline-flex h-9 w-9 items-center justify-center rounded-lg text-base font-extrabold">
      <font-awesome-icon v-if="card.icon" :icon="['fas', card.icon]"/>
      <template v-else>{{ card.label }}</template>
    </span>
    <p class="font-semibold">{{ card.title }}</p>
    <p class="text-sm leading-relaxed text-(--text-muted)">{{ card.body }}</p>
  </NeutralContainer>
</template>
