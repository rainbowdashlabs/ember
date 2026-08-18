/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PitchHeading from './PitchHeading.vue'
import PitchScreen from './PitchScreen.vue'
import type {SplitSlide} from './pitchTypes'
import {accentText} from './pitchAccents'

/**
 * The module overview: what it does on the left, a screen of it on the right. A screen that lays
 * its content out in a grid asks for `wide` - the application's grids follow the width of the
 * window, so half a slide squeezes them into unreadable columns.
 */
defineProps<{
  slide: SplitSlide
}>()
</script>

<template>
  <div class="flex h-full flex-col gap-6">
    <PitchHeading
        :accent="slide.accent" :chip="slide.chip" :counter="slide.counter"
        :heading="slide.heading" :heading-accent="slide.headingAccent" :lead="slide.lead"/>

    <div class="grid flex-1 content-start gap-8" :class="slide.wide ? '' : 'lg:grid-cols-2'">
      <ul class="grid gap-x-8 gap-y-3" :class="slide.wide ? 'sm:grid-cols-2 lg:grid-cols-3' : ''">
        <li v-for="bullet in slide.bullets" :key="bullet" class="flex gap-3 text-sm sm:text-base">
          <span class="font-bold" :class="accentText(slide.accent)">›</span>
          <span>{{ bullet }}</span>
        </li>
      </ul>
      <PitchScreen :screen="slide.screen"/>
    </div>
  </div>
</template>
