/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StartTile from '@/components/start/StartTile.vue'
import StartTileSection from '@/components/start/StartTileSection.vue'
import StyleContainersGrid from '@/views/styleview/StyleContainersGrid.vue'
</script>

<template>
  <section class="space-y-4">
    <SectionHeader>Containers</SectionHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <StyleContainersGrid/>
    </div>
    <StartTileSection title="Start tiles">
      <StartTile title="From a template" button-label="Create" hint="Brings its steps along."/>
      <StartTile title="Empty" button-label="Create" hint="Everything is written by hand."/>
    </StartTileSection>
  </section>
</template>
