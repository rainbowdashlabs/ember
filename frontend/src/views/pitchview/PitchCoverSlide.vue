/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import LayeredEmberLogo from '@/components/display/LayeredEmberLogo.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import {emberLogo} from '@/composables/useEmberLogo'
import PitchCard from './PitchCard.vue'
import PitchChip from './PitchChip.vue'
import type {CoverSlide} from './pitchTypes'
import {accentText} from './pitchAccents'

defineProps<{
  slide: CoverSlide
}>()

const logo = emberLogo()
</script>

<template>
  <div class="flex h-full flex-col justify-center gap-6">
    <LayeredEmberLogo
        :layers="logo.layers" :active-layers="logo.activeLayers" :auto-blink="true"
        :pixel-size="512" size="h-20 w-20 sm:h-24 sm:w-24"/>

    <PageHeader class="text-4xl! font-black! leading-tight sm:text-5xl! xl:text-6xl!">
      {{ slide.heading }}<br>
      <span :class="accentText(slide.accent)">{{ slide.headingAccent }}</span>
    </PageHeader>

    <p class="max-w-4xl text-base leading-relaxed text-(--text-muted) sm:text-lg">{{ slide.lead }}</p>

    <div v-if="slide.pills.length" class="flex flex-wrap gap-2">
      <PitchChip v-for="pill in slide.pills" :key="pill" :accent="slide.accent">{{ pill }}</PitchChip>
    </div>

    <div v-if="slide.cards" class="grid gap-3 sm:grid-cols-3">
      <PitchCard v-for="card in slide.cards" :key="card.title" :card="card"/>
    </div>
  </div>
</template>
