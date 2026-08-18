/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PitchHeading from './PitchHeading.vue'
import type {ColumnsSlide} from './pitchTypes'
import {accentText} from './pitchAccents'

defineProps<{
  slide: ColumnsSlide
}>()
</script>

<template>
  <div class="flex h-full flex-col gap-6">
    <PitchHeading
        :accent="slide.accent" :chip="slide.chip"
        :heading="slide.heading" :heading-accent="slide.headingAccent" :lead="slide.lead"/>

    <div class="grid flex-1 content-start gap-3 sm:grid-cols-2 xl:grid-cols-4">
      <NeutralContainer v-for="group in slide.groups" :key="group.title" class="h-full space-y-2">
        <p class="font-semibold" :class="accentText(slide.accent)">{{ group.title }}</p>
        <ul class="space-y-1.5">
          <li v-for="item in group.items" :key="item"
              class="text-sm leading-relaxed text-(--text-muted)">• {{ item }}</li>
        </ul>
      </NeutralContainer>
    </div>
  </div>
</template>
