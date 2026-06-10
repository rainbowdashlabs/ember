/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import type {PublicBlogEntry} from '@/api/types'
import {news} from '@/api'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const stationUid = computed(() => route.params.stationUid as string)

const loading = ref(true)
const error = ref('')
const entries = ref<PublicBlogEntry[]>([])

async function load() {
  loading.value = true
  error.value = ''
  try {
    entries.value = await news.listPublicBlog(stationUid.value)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('de-DE', {year: 'numeric', month: 'long', day: 'numeric'})
}

function navigateToEntry(id: number) {
  router.push({name: 'public-blog-detail', params: {stationUid: stationUid.value, blogId: id}})
}

function excerpt(html: string, maxLength = 200): string {
  const text = html.replace(/<[^>]*>/g, '')
  return text.length > maxLength ? text.substring(0, maxLength) + '…' : text
}

onMounted(load)
</script>

<template>
  <ViewContent>
  <div class="space-y-6">
    <SectionHeader>{{ t('publicStation.blogTitle') }}</SectionHeader>

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
