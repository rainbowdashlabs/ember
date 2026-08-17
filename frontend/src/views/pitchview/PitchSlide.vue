/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PitchCoverSlide from './PitchCoverSlide.vue'
import PitchCardsSlide from './PitchCardsSlide.vue'
import PitchSplitSlide from './PitchSplitSlide.vue'
import PitchShowcaseSlide from './PitchShowcaseSlide.vue'
import PitchColumnsSlide from './PitchColumnsSlide.vue'
import PitchRoadmapSlide from './PitchRoadmapSlide.vue'
import type {PitchSlideData} from './pitchTypes'

defineProps<{
  slide: PitchSlideData
  position: string
}>()
</script>

<template>
  <section class="relative flex h-dvh flex-col overflow-y-auto px-5 pb-12 pt-8 sm:px-10 sm:pt-12"
           :class="slide.tone === 'accent' ? 'bg-(--bg-accent)' : 'bg-(--bg)'">
    <div class="mx-auto flex w-full max-w-7xl flex-1 flex-col justify-center">
      <PitchCoverSlide v-if="slide.kind === 'cover'" :slide="slide"/>
      <PitchCardsSlide v-else-if="slide.kind === 'cards'" :slide="slide"/>
      <PitchSplitSlide v-else-if="slide.kind === 'split'" :slide="slide"/>
      <PitchShowcaseSlide v-else-if="slide.kind === 'showcase'" :slide="slide"/>
      <PitchColumnsSlide v-else-if="slide.kind === 'columns'" :slide="slide"/>
      <PitchRoadmapSlide v-else :slide="slide"/>
    </div>

    <p class="mx-auto w-full max-w-7xl pt-6 text-left text-xs text-(--text-muted)">
      Ember · Open-Source-Verwaltung für Gruppen und Abteilungen · {{ position }}
    </p>
    <div class="absolute inset-x-0 bottom-0 h-1.5 bg-linear-to-r from-primary to-primary-accent"/>
  </section>
</template>
