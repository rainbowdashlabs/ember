/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {PublicBlogEntry} from '@/api/news'
import ViewContent from '@/components/layout/ViewContent.vue'
import {formatDateLong} from '@/util/format'
import AttachmentList from '@/views/stationview/news/newsshared/AttachmentList.vue'
import NewsBody from '@/views/stationview/news/newsshared/NewsBody.vue'
import {publicContentContext} from '@/util/contentContext'
import {apiUrl} from '@/util/apiUrl'
import {plainTextExcerpt, socialMeta, stationLogoImage, useAbsoluteUrl} from '@/util/socialMeta'
import {usePublicStationAddress} from '@/composables/usePublicStationAddress'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {station, stationUid, stationAddress} = usePublicStationAddress()
const blogId = computed(() => Number(route.params.blogId))

const absoluteUrl = useAbsoluteUrl()

/**
 * Resolved here rather than inside the loader: the address comes from the runtime configuration,
 * and reading that needs the Nuxt instance, which a loader running on its own no longer has.
 */
const apiBase = apiUrl('')

/**
 * Fetched while the server renders rather than after mounting.
 *
 * <p>An entry's own headline and the opening of its body are what a link to it unfurls as, and a
 * body fetched once scripts have run is in no page a crawler or a chat client ever reads. It is the
 * entry itself that is worth sharing here, so it has to travel in the page the server sends.
 */
const {data: entry, error: loadError, status} = await useAsyncData(
    () => `public-blog-entry-${stationUid.value}-${blogId.value}`,
    () => $fetch<PublicBlogEntry>(`${apiBase}/public/station/${stationUid.value}/blog/${blogId.value}`),
    {watch: [stationUid, blogId]},
)

const loading = computed(() => status.value === 'pending')
const error = computed(() => (loadError.value ? t('common.error') : ''))

useHead(computed(() => {
  const e = entry.value
  if (!e) return {}
  const info = station.value
  const description = plainTextExcerpt(e.contentHtml)
      || (info ? t('publicStation.meta.blogEntry', {station: info.name}) : e.title)
  const image = absoluteUrl(stationLogoImage(info))
  return {
    title: e.title,
    meta: socialMeta({title: e.title, description, imageUrl: image, type: 'article'}),
    script: [
      {
        type: 'application/ld+json',
        innerHTML: JSON.stringify({
          '@context': 'https://schema.org',
          '@type': 'Article',
          headline: e.title,
          description,
          datePublished: e.publishedAt,
          ...(e.authorName ? {author: {'@type': 'Person', name: e.authorName}} : {}),
          ...(image ? {image} : {}),
          ...(info ? {publisher: {'@type': 'Organization', name: info.name}} : {}),
        }),
      },
    ],
  }
}))

function goBack() {
  router.push({name: 'public-blog', params: {stationUid: stationAddress.value}})
}
</script>

<template>
  <ViewContent :title="t('pages.public-blog-detail.title')">
  <div class="space-y-6">
    <SecondaryButton compact :icon="['fas', 'arrow-left']" @click="goBack">
      {{ t('publicStation.blogTitle') }}
    </SecondaryButton>

    <Spinner v-if="loading" size="lg"/>
    <FailureAlert :message="error"/>

    <template v-if="entry">
      <SectionHeader>{{ entry.title }}</SectionHeader>
      <div class="flex items-center gap-3 text-sm text-(--text-muted)">
        <span v-if="entry.authorName">{{ t('publicStation.blogBy') }} {{ entry.authorName }}</span>
        <span>{{ formatDateLong(entry.publishedAt) }}</span>
      </div>
      <NeutralContainer>
        <NewsBody
            :mode="entry.contentMode"
            :rows="entry.rows ?? []"
            :html="entry.contentHtml"
            :context="publicContentContext(stationUid, entry.title)"
        />

        <AttachmentList :attachments="entry.attachments ?? []" :station-uid="stationUid"/>
      </NeutralContainer>
    </template>
  </div>
  </ViewContent>
</template>
