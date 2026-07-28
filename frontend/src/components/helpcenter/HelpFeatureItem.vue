/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import AccentIcon from '@/components/display/AccentIcon.vue'
import MutedText from '@/components/typography/MutedText.vue'

/**
 * Icon + title + description card used by the help center to introduce a capability or a
 * requirement. One card per item, listed in a grid or a stack by the article.
 */
defineProps<{
  icon: string[]
  title: string
  description: string
}>()
</script>

<template>
  <NeutralContainer class="flex items-start gap-3 p-3">
    <AccentIcon :icon="icon"/>
    <div>
      <p class="font-semibold text-sm">{{ title }}</p>
      <MutedText tag="p">{{ description }}</MutedText>
    </div>
  </NeutralContainer>
</template>
