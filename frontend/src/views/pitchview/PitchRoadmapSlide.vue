/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PitchHeading from './PitchHeading.vue'
import PitchNote from './PitchNote.vue'
import type {RoadmapSlide} from './pitchTypes'
import {accentBorder, accentText} from './pitchAccents'

defineProps<{
  slide: RoadmapSlide
}>()
</script>

<template>
  <div class="flex h-full flex-col gap-6">
    <PitchHeading
        :accent="slide.accent" :chip="slide.chip"
        :heading="slide.heading" :heading-accent="slide.headingAccent" :lead="slide.lead"/>

    <div class="grid flex-1 content-start gap-3 lg:grid-cols-3">
      <div v-for="column in slide.columns" :key="column.label"
           class="h-full space-y-3 rounded-theme border-l-4 bg-(--bg) px-4 py-4"
           :class="accentBorder(column.accent)">
        <p class="text-xs font-bold tracking-wide" :class="accentText(column.accent)">{{ column.label }}</p>
        <ul v-if="column.items.length" class="space-y-1.5">
          <li v-for="item in column.items" :key="item" class="text-sm">{{ item }}</li>
        </ul>
        <p v-if="column.note" class="text-sm leading-relaxed text-(--text-muted)">{{ column.note }}</p>
      </div>
    </div>

    <PitchNote v-if="slide.note">{{ slide.note }}</PitchNote>
  </div>
</template>
