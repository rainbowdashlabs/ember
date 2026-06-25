/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StyleContainersGrid from '@/views/styleview/StyleContainersGrid.vue'
</script>

<template>
  <section class="space-y-4">
    <SectionHeader>Containers</SectionHeader>
    <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
      <StyleContainersGrid/>
    </div>
  </section>
</template>
