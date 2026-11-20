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
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FormSection from '@/components/container/FormSection.vue'
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
    <NeutralContainer sheet>
      <p>Form essentials stand open.</p>
      <FormSection title="Reminders" summary="1, 7 days before">
        <p>A folded section keeps its content in the page while it is closed.</p>
      </FormSection>
    </NeutralContainer>
  </section>
</template>
