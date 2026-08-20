/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import ProseContent from '@/components/display/ProseContent.vue'
import ContentBlocks from '@/components/content/ContentBlocks.vue'
import {ContentMode, type ContentModeName} from '@/api/news'
import type {PageRow} from '@/api/pageManage'
import type {ContentRenderContext} from '@/util/contentContext'

/**
 * The body of a news entry, however it was written.
 *
 * A plain entry is the stored text; a rich one is its blocks. The stored text of a rich entry is a
 * projection of those blocks and is what everything else reads, so this is the one place that has
 * to know the difference.
 */
defineProps<{
  mode: ContentModeName
  rows: PageRow[]
  html: string
  context: ContentRenderContext
}>()
</script>

<template>
  <ContentBlocks v-if="mode === ContentMode.RICH" :rows="rows" :context="context"/>
  <ProseContent v-else v-html="html"/>
</template>
