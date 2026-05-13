/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'

defineProps<{
  title: string
}>()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SectionHeader>{{ title }}</SectionHeader>
    <div class="text-sm leading-relaxed space-y-2">
      <slot/>
    </div>
  </NeutralContainer>
</template>
