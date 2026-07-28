/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed, type ComputedRef} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {PublicBlogEntry} from '@/api/news'
import {news} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {formatDateLong} from '@/util/format'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const stationUid = computed(() => route.params.stationUid as string)
const rssUrl = computed(() => `/api/v1/public/station/${stationUid.value}/blog.rss`)
const atomUrl = computed(() => `/api/v1/public/station/${stationUid.value}/blog.atom`)
useHead({
    link: () => [
        {rel: 'alternate', type: 'application/rss+xml', href: rssUrl.value, title: 'RSS'},
        {rel: 'alternate', type: 'application/atom+xml', href: atomUrl.value, title: 'Atom'},
    ],
})

const entries = ref<PublicBlogEntry[]>([])

const {loading, error} = useAsyncLoader(async () => {
  entries.value = await news.listPublicBlog(stationUid.value)
})

function navigateToEntry(id: number) {
  router.push({name: 'public-blog-detail', params: {stationUid: stationUid.value, blogId: id}})
}

function openFeed(url: ComputedRef<string>) {
  window.open(url.value, '_blank', 'noopener')
}

function excerpt(html: string, maxLength = 200): string {
  const text = html.replace(/<[^>]*>/g, '')
  return text.length > maxLength ? text.substring(0, maxLength) + '…' : text
}
</script>

<template>
  <ViewContent :title="t('pages.public-blog.title')" :subtitle="t('pages.public-blog.subtitle')">
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-end gap-3">
      <div class="flex items-center gap-2">
        <SecondaryButton @click="openFeed(rssUrl)">
          <font-awesome-icon :icon="['fas', 'rss']" class="mr-1"/>
          RSS
        </SecondaryButton>
        <SecondaryButton @click="openFeed(atomUrl)">
          <font-awesome-icon :icon="['fas', 'rss']" class="mr-1"/>
          Atom
        </SecondaryButton>
      </div>
    </div>

    <AsyncSection
        :empty="entries.length === 0"
        :empty-message="t('publicStation.blogNoEntries')"
        :error="error"
        :loading="loading"
    >
      <div class="space-y-4">
        <NeutralContainer
            v-for="entry in entries"
            :key="entry.id"
            class="cursor-pointer hover:ring-2 hover:ring-primary/40 transition-all"
            @click="navigateToEntry(entry.id)"
        >
          <SubHeader class="mb-2">{{ entry.title }}</SubHeader>
          <p class="text-sm text-(--text-muted) line-clamp-3">{{ excerpt(entry.contentHtml) }}</p>
          <div class="mt-3 flex items-center gap-3 text-xs text-(--text-muted)">
            <span v-if="entry.authorName">{{ t('publicStation.blogBy') }} {{ entry.authorName }}</span>
            <span>{{ formatDateLong(entry.publishedAt) }}</span>
          </div>
        </NeutralContainer>
      </div>
    </AsyncSection>
  </div>
  </ViewContent>
</template>
