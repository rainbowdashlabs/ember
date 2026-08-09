/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import NewsCommentSection from '@/components/comment/NewsCommentSection.vue'
import type { FederatedNewsDetail } from '@/api/news'
import { news } from '@/api'
import { useConfigPanel } from '@/composables/useConfigPanel'
import { formatDateTime } from '@/util/format'
import ProseContent from '@/components/display/ProseContent.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const stationUid = ref(route.params.stationUid as string)
const newsId = ref(Number(route.params.newsId))

const { config: entry, loading, error, reload: loadData } = useConfigPanel<FederatedNewsDetail | null>({
  initial: null,
  fetch: () => news.getFederatedNews(stationUid.value, newsId.value),
})

watch(() => [route.params.stationUid, route.params.newsId], () => {
  stationUid.value = route.params.stationUid as string
  newsId.value = Number(route.params.newsId)
  loadData()
})
</script>

<template>
  <ViewContent
      :title="t('pages.federated-news-detail.title')"
      :subtitle="t('pages.federated-news-detail.subtitle')"
  >
    <div class="space-y-4">
      <SecondaryButton :icon="['fas', 'arrow-left']" compact @click="router.push({ name: 'news-list' })">
        {{ t('common.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer v-if="entry" class="space-y-3">
        <div>
          <SubHeader>{{ entry.title }}</SubHeader>
          <p class="text-xs text-(--text-muted) flex items-center gap-1.5 mt-1">
            <StationBadge :station-name="t('news.partnerNews')" />
            <span>{{ entry.authorName }} &middot; {{ formatDateTime(entry.publishedAt) }}</span>
          </p>
        </div>

        <ProseContent v-html="entry.contentHtml"/>

        <div class="pt-3 border-t border-bg-light-accent dark:border-bg-dark-accent">
          <NewsCommentSection :news-id="newsId" :station-uid="stationUid" />
        </div>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
