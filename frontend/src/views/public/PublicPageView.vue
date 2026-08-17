/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, inject, onMounted, ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import type {PublicStationInfo} from '@/api/discovery'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import {getPublicPage, publicPageImageUrl} from '@/api/publicPages'
import {CellContentType, type StationPage} from '@/api/pageManage'
import PublicPageRow from './publicpageview/PublicPageRow.vue'
import {useCanonical} from '~/composables/useCanonical'

const {t} = useI18n()
const route = useRoute()

const publicStation = inject<Ref<PublicStationInfo | null>>('publicStation')
const stationUid = computed(() => publicStation?.value?.stationUid ?? route.params.stationUid as string)
const slug = computed((): string => {
  const param = route.params.slug
  // Nuxt catch-all routes provide an array; vue-router provides a string
  return Array.isArray(param) ? param.join('/') : (param ?? '')
})

const loading = ref(true)
const error = ref('')
const page = ref<StationPage | null>(null)

onMounted(async () => {
  try {
    page.value = await getPublicPage(stationUid.value, slug.value)
  } catch {
    error.value = t('common.notFound')
  } finally {
    loading.value = false
  }
})

useCanonical(() => route.path)

function generateDescription(p: StationPage): string {
  if (p.metaDescription) return p.metaDescription
  for (const row of p.rows) {
    for (const cell of row.cells) {
      if (cell.contentType === CellContentType.MARKDOWN && cell.content) {
        const plain = cell.content.replace(/[#*_~`>\[\]()!|\\-]/g, '').replace(/\s+/g, ' ').trim()
        if (plain.length > 0) return plain.length > 160 ? plain.slice(0, 157) + '...' : plain
      }
    }
  }
  return p.title
}

useHead(computed(() => {
  if (!page.value) return {}
  const p = page.value
  const desc = generateDescription(p)
  const ogImage = p.ogImageHash ? publicPageImageUrl(stationUid.value, p.ogImageHash) : undefined
  return {
    title: p.title,
    meta: [
      {name: 'description', content: desc},
      {property: 'og:title', content: `${p.title} - Ember`},
      {property: 'og:description', content: desc},
      {property: 'og:type', content: 'website'},
      ...(ogImage ? [
        {property: 'og:image', content: ogImage},
        {name: 'twitter:image', content: ogImage},
      ] : []),
      {name: 'twitter:card', content: ogImage ? 'summary_large_image' : 'summary'},
      {name: 'twitter:title', content: `${p.title} - Ember`},
      {name: 'twitter:description', content: desc},
    ],
  }
}))

</script>

<template>
  <Spinner v-if="loading" size="lg" class="mt-16"/>
  <Alert v-else-if="error" variant="error" class="m-4">{{ error }}</Alert>
  <ViewContent v-else-if="page" :title="page.title">
    <div class="space-y-0 max-w-5xl mx-auto">
      <PublicPageRow
          v-for="row in page.rows"
          :key="row.id"
          :row="row"
          :station-uid="stationUid"
          :page-title="page.title"
      />
    </div>
  </ViewContent>
</template>
