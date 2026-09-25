/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import type {PublicBlogEntry} from '@/api/news'
import {news} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {formatDateLong} from '@/util/format'
import {plainTextExcerpt, socialMeta, stationLogoImage, titleWithStation, useAbsoluteUrl} from '@/util/socialMeta'
import {usePublicStationAddress} from '@/composables/usePublicStationAddress'

const {t} = useI18n()
const {station, stationUid, stationAddress} = usePublicStationAddress()
const rssUrl = computed(() => `/api/v1/public/station/${stationUid.value}/blog.rss`)
const atomUrl = computed(() => `/api/v1/public/station/${stationUid.value}/blog.atom`)
const headTitle = computed(() => titleWithStation(t('publicStation.blogTitle'), station.value?.name))
const absoluteUrl = useAbsoluteUrl()

useHead(computed(() => {
    const info = station.value
    return {
        title: headTitle.value,
        meta: info
            ? socialMeta({
                title: headTitle.value,
                description: t('publicStation.meta.blog', {station: info.name}),
                imageUrl: absoluteUrl(stationLogoImage(info)),
            })
            : [],
        link: [
            {rel: 'alternate', type: 'application/rss+xml', href: rssUrl.value, title: 'RSS'},
            {rel: 'alternate', type: 'application/atom+xml', href: atomUrl.value, title: 'Atom'},
        ],
    }
}))

const entries = ref<PublicBlogEntry[]>([])

const {loading, error} = useAsyncLoader(async () => {
  entries.value = await news.listPublicBlog(stationUid.value)
})

/** The entry the card stands for. */
function entryPage(id: number) {
  return {name: 'public-blog-detail', params: {stationUid: stationAddress.value, blogId: id}}
}

function openFeed(url: string) {
  window.open(url, '_blank', 'noopener')
}
</script>

<template>
  <ViewContent :title="t('pages.public-blog.title')" :subtitle="t('pages.public-blog.subtitle')">
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-end gap-3">
      <ButtonRow pair>
        <SecondaryButton @click="openFeed(rssUrl)">
          <font-awesome-icon :icon="['fas', 'rss']" class="mr-1"/>
          RSS
        </SecondaryButton>
        <SecondaryButton @click="openFeed(atomUrl)">
          <font-awesome-icon :icon="['fas', 'rss']" class="mr-1"/>
          Atom
        </SecondaryButton>
      </ButtonRow>
    </div>

    <AsyncSection
        :empty="entries.length === 0"
        :empty-message="t('publicStation.blogNoEntries')"
        :error="error"
        :loading="loading"
    >
      <div class="space-y-4">
        <RowLink v-for="entry in entries" :key="entry.id" :to="entryPage(entry.id)">
          <NeutralContainer class="cursor-pointer hover:ring-2 hover:ring-primary/40 transition-all">
            <SubHeader class="mb-2">{{ entry.title }}</SubHeader>
            <p class="text-sm text-(--text-muted) line-clamp-3">{{ plainTextExcerpt(entry.contentHtml) }}</p>
            <div class="mt-3 flex items-center gap-3 text-xs text-(--text-muted)">
              <span v-if="entry.authorName">{{ t('publicStation.blogBy') }} {{ entry.authorName }}</span>
              <span>{{ formatDateLong(entry.publishedAt) }}</span>
            </div>
          </NeutralContainer>
        </RowLink>
      </div>
    </AsyncSection>
  </div>
  </ViewContent>
</template>
