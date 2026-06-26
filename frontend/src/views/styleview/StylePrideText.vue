/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'

import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrideText from '@/components/display/PrideText.vue'

const pridePreview = ref(true)
</script>

<template>
  <section class="space-y-4">
    <SectionHeader>Pride Text</SectionHeader>
    <div class="flex items-center gap-4 mb-2">
      <ToggleInput v-model="pridePreview"/>
      <span class="text-sm text-(--text-muted)">Aktiv</span>
    </div>
    <SubHeader>Text</SubHeader>
    <div class="flex flex-wrap items-baseline gap-6">
      <PrideText :active="pridePreview" class="text-4xl font-bold">Ember</PrideText>
      <PrideText :active="pridePreview" class="text-2xl font-bold">Ember</PrideText>
      <PrideText :active="pridePreview" class="text-lg font-bold">Ember</PrideText>
      <PrideText :active="pridePreview" class="text-sm font-semibold">Ember</PrideText>
    </div>
    <SubHeader>Banner</SubHeader>
    <div class="flex flex-wrap items-baseline gap-6">
      <PrideText :active="pridePreview" variant="banner" class="text-4xl font-bold">Ember</PrideText>
      <PrideText :active="pridePreview" variant="banner" class="text-2xl font-bold">Ember</PrideText>
      <PrideText :active="pridePreview" variant="banner" class="text-lg font-bold">Ember</PrideText>
      <PrideText :active="pridePreview" variant="banner" class="text-sm font-semibold">Ember</PrideText>
    </div>
    <PrideText :active="pridePreview" variant="banner" class="text-xl font-bold">Happy Pride Month!</PrideText>
  </section>
</template>
