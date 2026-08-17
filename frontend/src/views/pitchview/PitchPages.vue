/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import PageRow from '@/views/stationview/pages/pageslistview/PageRow.vue'
import PublicPageRow from '@/views/public/publicpageview/PublicPageRow.vue'
import type {PitchPages} from './pitchTypes'

/**
 * Either the page tree of the station, or the public page itself - both drawn by the components
 * the application uses for them.
 */
defineProps<{
  pages: PitchPages
  section: 'tree' | 'page'
}>()
</script>

<template>
  <template v-if="section === 'tree'">
    <PageRow v-for="entry in pages.tree" :key="entry.page.id"
             :page="entry.page" :depth="entry.depth" :can-edit="true" :can-manage="true"
             :landing-page-id="pages.landingPageId"/>
  </template>

  <div v-else class="space-y-0">
    <PublicPageRow v-for="row in pages.rows" :key="row.id"
                   :row="row" station-uid="wache" page-title="Startseite"/>
  </div>
</template>
