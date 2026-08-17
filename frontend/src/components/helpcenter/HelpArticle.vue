/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import ProseContent from '@/components/display/ProseContent.vue'

const props = defineProps<{
  title: string
  subtitle?: string
}>()

useHead(computed(() => ({
  meta: [
    {name: 'description', content: props.subtitle || `Hilfe-Center: ${props.title}`},
    {property: 'og:title', content: `${props.title} - Hilfe-Center - Ember`},
    {property: 'og:description', content: props.subtitle || `Hilfe-Center: ${props.title}`},
    {property: 'og:type', content: 'article'},
  ],
})))
</script>

<template>
  <ViewContent :title="title" :subtitle="subtitle">
    <div class="max-w-3xl mx-auto space-y-6">
      <ProseContent class="space-y-6">
        <slot/>
      </ProseContent>
    </div>
  </ViewContent>
</template>
