/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import AccentIcon from '@/components/display/AccentIcon.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'
import IconButton from '@/components/button/IconButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
</script>

<template>
  <section class="space-y-4">
    <SubHeader>Icons</SubHeader>
    <p class="text-sm text-(--text-muted)">
      The two sets side by side. The prefix picks one: <code>fas</code> and <code>fab</code> are
      FontAwesome, <code>ph</code> is Phosphor, drawn at its fill weight so the two match.
    </p>

    <div class="flex flex-wrap gap-6 items-center">
      <span class="flex items-center gap-2 text-sm">
        <AppIcon :icon="['fas', 'fire-extinguisher']" class="h-5 w-5"/>
        FontAwesome
      </span>
      <span class="flex items-center gap-2 text-sm">
        <AppIcon :icon="['ph', 'fire-truck']" class="h-5 w-5"/>
        Phosphor
      </span>
      <span class="flex items-center gap-2 text-sm">
        <AppIcon :icon="['ph', 'siren']" class="h-5 w-5"/>
        Phosphor
      </span>
      <span class="flex items-center gap-2 text-sm">
        <AppIcon :icon="['ph', 'ladder']" class="h-5 w-5"/>
        Phosphor
      </span>
    </div>

    <p class="text-sm text-(--text-muted)">
      Every component that takes an icon takes either set, because they all draw it the same way.
    </p>

    <div class="flex flex-wrap gap-3 items-center">
      <PrimaryButton :icon="['ph', 'fire-truck']">Engine out</PrimaryButton>
      <SecondaryButton :icon="['fas', 'truck-medical']">Ambulance out</SecondaryButton>
      <IconButton :icon="['ph', 'siren']" label="Siren"/>
      <IconButton :icon="['fas', 'bell']" label="Bell"/>
    </div>

    <div class="flex flex-wrap gap-4 items-center text-sm">
      <span class="flex items-center gap-1"><AccentIcon :icon="['ph', 'pants']"/> Accent</span>
      <span class="flex items-center gap-1"><MutedIcon :icon="['ph', 'pants']"/> Muted</span>
      <span class="flex items-center gap-1"><AccentIcon :icon="['fas', 'toolbox']"/> Accent</span>
      <span class="flex items-center gap-1"><MutedIcon :icon="['fas', 'toolbox']"/> Muted</span>
    </div>

    <p class="text-sm text-(--text-muted)">
      What Phosphor was added for: the gear a station stocks that FontAwesome's free tier does not draw.
    </p>

    <div class="flex flex-wrap gap-4 items-center text-sm">
      <span class="flex items-center gap-1"><AppIcon :icon="['ph', 'pants']" class="h-5 w-5"/> Hose</span>
      <span class="flex items-center gap-1"><AppIcon :icon="['ph', 'boot']" class="h-5 w-5"/> Stiefel</span>
      <span class="flex items-center gap-1"><AppIcon :icon="['ph', 'sneaker']" class="h-5 w-5"/> Turnschuhe</span>
      <span class="flex items-center gap-1"><AppIcon :icon="['ph', 't-shirt']" class="h-5 w-5"/> T-Shirt</span>
      <span class="flex items-center gap-1"><AppIcon :icon="['ph', 'hoodie']" class="h-5 w-5"/> Pullover</span>
      <span class="flex items-center gap-1"><AppIcon :icon="['ph', 'baseball-cap']" class="h-5 w-5"/> Kappe</span>
      <span class="flex items-center gap-1"><AppIcon :icon="['ph', 'sock']" class="h-5 w-5"/> Socke</span>
      <span class="flex items-center gap-1"><AppIcon :icon="['ph', 'axe']" class="h-5 w-5"/> Axt</span>
      <span class="flex items-center gap-1"><AppIcon :icon="['ph', 'flashlight']" class="h-5 w-5"/> Handlampe</span>
      <span class="flex items-center gap-1"><AppIcon :icon="['ph', 'traffic-cone']" class="h-5 w-5"/> Pylone</span>
      <span class="flex items-center gap-1"><AppIcon :icon="['ph', 'backpack']" class="h-5 w-5"/> Rucksack</span>
      <span class="flex items-center gap-1"><AppIcon :icon="['ph', 'binoculars']" class="h-5 w-5"/> Fernglas</span>
    </div>
  </section>
</template>
