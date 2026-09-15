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
      <!--
        Prose gives every heading and paragraph a margin of its own, which is right in running text
        and wrong as the first thing inside a box: there it lands on top of the box's own padding and
        the heading floats away from the top of its panel. Prose drops that margin for its own first
        child only, and an article is boxes all the way down, so the same is said here for every box
        in it rather than in each of the dozen that draw one.
      -->
      <ProseContent class="space-y-6 [&_*>:first-child]:mt-0 [&_*>:last-child]:mb-0">
        <slot/>
      </ProseContent>
    </div>
  </ViewContent>
</template>
