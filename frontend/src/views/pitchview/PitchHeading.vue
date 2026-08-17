/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PitchChip from './PitchChip.vue'
import type {Accent} from './pitchTypes'
import {accentText} from './pitchAccents'

defineProps<{
  accent: Accent
  heading: string
  headingAccent?: string
  chip?: string
  counter?: string
  lead?: string
}>()
</script>

<template>
  <div class="space-y-3">
    <div v-if="chip || counter" class="flex flex-wrap items-center gap-2">
      <PitchChip v-if="counter" accent="secondary">{{ counter }}</PitchChip>
      <PitchChip v-if="chip" :accent="accent">{{ chip }}</PitchChip>
    </div>
    <SectionHeader class="text-3xl! font-extrabold! leading-tight sm:text-4xl! xl:text-5xl!">
      {{ heading }}
      <span v-if="headingAccent" :class="accentText(accent)">{{ headingAccent }}</span>
    </SectionHeader>
    <p v-if="lead" class="max-w-3xl text-base leading-relaxed text-(--text-muted) sm:text-lg">{{ lead }}</p>
  </div>
</template>
