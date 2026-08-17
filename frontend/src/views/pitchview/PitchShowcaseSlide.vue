/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PitchHeading from './PitchHeading.vue'
import PitchScreen from './PitchScreen.vue'
import PitchNote from './PitchNote.vue'
import type {ShowcaseSlide} from './pitchTypes'
import {accentText} from './pitchAccents'

defineProps<{
  slide: ShowcaseSlide
}>()
</script>

<template>
  <div class="flex h-full flex-col gap-6">
    <PitchHeading
        :accent="slide.accent" :chip="slide.chip"
        :heading="slide.heading" :heading-accent="slide.headingAccent" :lead="slide.lead"/>

    <div class="grid flex-1 content-start gap-4"
         :class="slide.screens.length > 1 ? 'lg:grid-cols-2' : ''">
      <PitchScreen v-for="(screen, index) in slide.screens" :key="index" :screen="screen"/>
    </div>

    <ul class="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
      <li v-for="point in slide.points" :key="point" class="flex gap-2 text-sm">
        <span class="font-bold" :class="accentText(slide.accent)">✦</span>
        <span class="text-(--text-muted)">{{ point }}</span>
      </li>
    </ul>

    <PitchNote v-if="slide.note">{{ slide.note }}</PitchNote>
  </div>
</template>
