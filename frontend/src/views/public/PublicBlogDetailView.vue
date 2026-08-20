/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import type {PublicBlogEntry} from '@/api/news'
import ViewContent from '@/components/layout/ViewContent.vue'
import {news} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {formatDateLong} from '@/util/format'
import AttachmentList from '@/views/stationview/news/newsshared/AttachmentList.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const stationUid = computed(() => route.params.stationUid as string)
const blogId = computed(() => Number(route.params.blogId))

const entry = ref<PublicBlogEntry | null>(null)

const {loading, error} = useAsyncLoader(async () => {
  entry.value = await news.getPublicBlogEntry(stationUid.value, blogId.value)
})

function goBack() {
  router.push({name: 'public-blog', params: {stationUid: stationUid.value}})
}
</script>

<template>
  <ViewContent :title="t('pages.public-blog-detail.title')">
  <div class="space-y-6">
    <SecondaryButton compact :icon="['fas', 'arrow-left']" @click="goBack">
      {{ t('publicStation.blogTitle') }}
    </SecondaryButton>

    <Spinner v-if="loading" size="lg"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <template v-if="entry">
      <SectionHeader>{{ entry.title }}</SectionHeader>
      <div class="flex items-center gap-3 text-sm text-(--text-muted)">
        <span v-if="entry.authorName">{{ t('publicStation.blogBy') }} {{ entry.authorName }}</span>
        <span>{{ formatDateLong(entry.publishedAt) }}</span>
      </div>
      <NeutralContainer>
        <div class="prose dark:prose-invert max-w-none" v-html="entry.contentHtml"/>

        <AttachmentList :attachments="entry.attachments ?? []" :station-uid="stationUid"/>
      </NeutralContainer>
    </template>
  </div>
  </ViewContent>
</template>
