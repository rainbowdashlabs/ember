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
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {PublicBlogEntry} from '@/api/types'
import ViewContent from '@/components/layout/ViewContent.vue'
import {news} from '@/api'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const stationUid = computed(() => route.params.stationUid as string)
const blogId = computed(() => Number(route.params.blogId))

const loading = ref(true)
const error = ref('')
const entry = ref<PublicBlogEntry | null>(null)

async function load() {
  loading.value = true
  error.value = ''
  try {
    entry.value = await news.getPublicBlogEntry(stationUid.value, blogId.value)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('de-DE', {year: 'numeric', month: 'long', day: 'numeric'})
}

function goBack() {
  router.push({name: 'public-blog', params: {stationUid: stationUid.value}})
}

onMounted(load)
</script>

<template>
  <ViewContent>
  <div class="space-y-6">
    <SecondaryButton size="sm" :icon="['fas', 'arrow-left']" @click="goBack">
      {{ t('publicStation.blogTitle') }}
    </SecondaryButton>

    <Spinner v-if="loading" size="lg"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <template v-if="entry">
      <SectionHeader>{{ entry.title }}</SectionHeader>
      <div class="flex items-center gap-3 text-sm text-(--text-muted)">
        <span v-if="entry.authorName">{{ t('publicStation.blogBy') }} {{ entry.authorName }}</span>
        <span>{{ formatDate(entry.publishedAt) }}</span>
      </div>
      <NeutralContainer>
        <div class="prose dark:prose-invert max-w-none" v-html="entry.contentHtml"/>
      </NeutralContainer>
    </template>
  </div>
  </ViewContent>
</template>
