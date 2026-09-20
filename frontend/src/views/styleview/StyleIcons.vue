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
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
</script>

<template>
  <section class="space-y-4">
    <SubHeader>Icons</SubHeader>
    <p class="text-sm text-(--text-muted)">
      Beide Sätze nebeneinander. Das Präfix entscheidet: <code>fas</code> und <code>fab</code> sind
      FontAwesome, <code>ph</code> ist Phosphor, gezeichnet im Gewicht <code>fill</code>, damit beide
      zusammenpassen.
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
      Jede Komponente mit einem Icon nimmt beide Sätze, weil alle es auf demselben Weg zeichnen.
    </p>

    <ButtonRow>
      <PrimaryButton :icon="['ph', 'fire-truck']">Fahrzeug raus</PrimaryButton>
      <SecondaryButton :icon="['fas', 'truck-medical']">Rettungswagen raus</SecondaryButton>
    </ButtonRow>

    <div class="flex flex-wrap gap-3 items-center">
      <IconButton :icon="['ph', 'siren']" label="Blaulicht"/>
      <IconButton :icon="['fas', 'bell']" label="Glocke"/>
    </div>

    <div class="flex flex-wrap gap-4 items-center text-sm">
      <span class="flex items-center gap-1"><AccentIcon :icon="['ph', 'pants']"/> Accent</span>
      <span class="flex items-center gap-1"><MutedIcon :icon="['ph', 'pants']"/> Muted</span>
      <span class="flex items-center gap-1"><AccentIcon :icon="['fas', 'toolbox']"/> Accent</span>
      <span class="flex items-center gap-1"><MutedIcon :icon="['fas', 'toolbox']"/> Muted</span>
    </div>

    <p class="text-sm text-(--text-muted)">
      Wofür Phosphor dazugekommen ist: die Ausrüstung einer Wache, die FontAwesome frei nicht zeichnet.
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
