/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import ViewContent from '@/components/layout/ViewContent.vue'

const props = defineProps<{
  title: string
  subtitle?: string
}>()

useHead(computed(() => ({
  meta: [
    {name: 'description', content: props.subtitle || `Hilfe-Center: ${props.title}`},
    {property: 'og:title', content: `${props.title} — Hilfe-Center — Ember`},
    {property: 'og:description', content: props.subtitle || `Hilfe-Center: ${props.title}`},
    {property: 'og:type', content: 'article'},
  ],
})))
</script>

<template>
  <ViewContent :title="title" :subtitle="subtitle">
    <div class="max-w-3xl mx-auto space-y-6">
      <div class="prose prose-sm dark:prose-invert max-w-none space-y-6">
        <slot/>
      </div>
    </div>
  </ViewContent>
</template>
