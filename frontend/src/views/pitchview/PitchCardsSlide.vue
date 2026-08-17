/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import PitchHeading from './PitchHeading.vue'
import PitchCard from './PitchCard.vue'
import PitchNote from './PitchNote.vue'
import PitchMetrics from './PitchMetrics.vue'
import type {CardsSlide} from './pitchTypes'

const props = defineProps<{
  slide: CardsSlide
}>()

const COLUMN_CLASSES: Record<number, string> = {
  2: 'sm:grid-cols-2',
  3: 'sm:grid-cols-2 lg:grid-cols-3',
  4: 'sm:grid-cols-2 lg:grid-cols-4',
}

const gridClass = computed(() => COLUMN_CLASSES[props.slide.columns ?? 3] ?? COLUMN_CLASSES[3])
</script>

<template>
  <div class="flex h-full flex-col gap-6">
    <PitchHeading
        :accent="slide.accent" :chip="slide.chip"
        :heading="slide.heading" :heading-accent="slide.headingAccent" :lead="slide.lead"/>

    <div class="grid flex-1 content-start gap-3" :class="gridClass">
      <PitchCard v-for="card in slide.cards" :key="card.title" :card="card"/>
    </div>

    <PitchMetrics v-if="slide.metrics" :metrics="slide.metrics"/>
    <PitchNote v-if="slide.note">{{ slide.note }}</PitchNote>
  </div>
</template>
