/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MutedText from '@/components/typography/MutedText.vue'

/**
 * One part of a member card under its small heading: the guardians, the tags, and so on.
 */
defineProps<{
  title: string
}>()
</script>

<template>
  <div class="flex flex-col gap-1">
    <MutedText class="font-semibold uppercase tracking-wide">{{ title }}</MutedText>
    <div class="flex flex-wrap items-center gap-1.5">
      <slot/>
    </div>
  </div>
</template>
