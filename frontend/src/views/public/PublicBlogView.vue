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
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {PublicBlogEntry} from '@/api/types'
import {news} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'

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

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('de-DE', {year: 'numeric', month: 'long', day: 'numeric'})
}

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
  <ViewContent>
  <div class="space-y-6">
    <div class="flex flex-wrap items-center justify-between gap-3">
      <SectionHeader>{{ t('publicStation.blogTitle') }}</SectionHeader>
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

    <Spinner v-if="loading" size="lg"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <EmptyState v-if="!loading && entries.length === 0">{{ t('publicStation.blogNoEntries') }}</EmptyState>

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
          <span>{{ formatDate(entry.publishedAt) }}</span>
        </div>
      </NeutralContainer>
    </div>
  </div>
  </ViewContent>
</template>
