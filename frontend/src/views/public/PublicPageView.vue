/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute} from 'vue-router'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import {publicPageImageUrl} from '@/api/publicPages'
import {CellContentType, type StationPage} from '@/api/pageManage'
import ContentRow from '@/components/content/ContentRow.vue'
import {publicContentContext} from '@/util/contentContext'
import {apiUrl} from '@/util/apiUrl'
import {socialMeta, stationLogoImage, useAbsoluteUrl} from '@/util/socialMeta'
import {useCanonical} from '~/composables/useCanonical'
import {usePublicStationAddress} from '@/composables/usePublicStationAddress'

const {t} = useI18n()
const route = useRoute()

const {station, stationUid, canonicalPath} = usePublicStationAddress()
const slug = computed((): string => {
  const param = route.params.slug
  // Nuxt catch-all routes provide an array; vue-router provides a string
  return Array.isArray(param) ? param.join('/') : (param ?? '')
})

const absoluteUrl = useAbsoluteUrl()

/**
 * Resolved here rather than inside the loader: the address comes from the runtime configuration,
 * and reading that needs the Nuxt instance, which a loader running on its own no longer has.
 */
const apiBase = apiUrl('')

/**
 * Fetched while the server renders rather than after mounting.
 *
 * <p>This is the page a station's own address opens on and the one its links point at, and its
 * title, the description it was given and the picture chosen for it are the whole of what a link to
 * it shows. All of it was fetched once the browser had taken over, so the page the server sent
 * carried the station's card and an empty body, whoever asked for it.
 */
const {data: page, error: loadError, status} = await useAsyncData(
    () => `public-page-${stationUid.value}-${slug.value}`,
    () => $fetch<StationPage>(`${apiBase}/public/pages/${stationUid.value}/page/${slug.value}`),
    {watch: [stationUid, slug]},
)

const loading = computed(() => status.value === 'pending')
const error = computed(() => (loadError.value ? t('common.notFound') : ''))

useCanonical(() => canonicalPath.value)

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
  const ogImage = absoluteUrl(p.ogImageHash
      ? publicPageImageUrl(stationUid.value, p.ogImageHash)
      : stationLogoImage(station.value))
  return {
    title: p.title,
    meta: socialMeta({title: p.title, description: desc, imageUrl: ogImage}),
  }
}))

</script>

<template>
  <Spinner v-if="loading" size="lg" class="mt-16"/>
  <Alert v-else-if="error" variant="error" class="m-4">{{ error }}</Alert>
  <ViewContent v-else-if="page" :title="page.title">
    <div class="space-y-0 max-w-5xl mx-auto">
      <ContentRow
          v-for="row in page.rows"
          :key="row.id"
          :row="row"
          :context="publicContentContext(stationUid, page.title)"
      />
    </div>
  </ViewContent>
</template>
