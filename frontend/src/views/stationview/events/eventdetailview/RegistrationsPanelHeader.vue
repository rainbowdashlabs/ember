/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import SubHeader from '@/components/typography/SubHeader.vue'

/**
 * The line the sign-up panel opens with: what the list is, and the occasional things that can be
 * done to it as a whole. The actions sit here rather than under the names because they are about
 * the list rather than about any one person on it.
 */
defineProps<{
  title: string
}>()
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-2">
    <SubHeader>{{ title }}</SubHeader>
    <slot/>
  </div>
</template>
