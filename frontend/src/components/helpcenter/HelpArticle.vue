/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import PageHeader from '@/components/typography/PageHeader.vue'

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
  <ViewContent>
    <div class="max-w-3xl mx-auto space-y-6">
      <div>
        <PageHeader class="text-2xl font-bold">{{ title }}</PageHeader>
        <p v-if="subtitle" class="text-[var(--text-muted)] mt-1">{{ subtitle }}</p>
      </div>
      <div class="prose prose-sm dark:prose-invert max-w-none space-y-6">
        <slot/>
      </div>
    </div>
  </ViewContent>
</template>
