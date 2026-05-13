/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import ViewContent from '@/components/layout/ViewContent.vue'

defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<template>
  <ViewContent>
    <div class="max-w-3xl mx-auto space-y-6">
      <div>
        <h1 class="text-2xl font-bold">{{ title }}</h1>
        <p v-if="subtitle" class="text-[var(--text-muted)] mt-1">{{ subtitle }}</p>
      </div>
      <div class="prose prose-sm dark:prose-invert max-w-none space-y-6">
        <slot/>
      </div>
    </div>
  </ViewContent>
</template>
