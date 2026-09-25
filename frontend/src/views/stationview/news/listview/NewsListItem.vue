/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RowLink from '@/components/navigation/RowLink.vue'
import NewsListItemHeader from './NewsListItemHeader.vue'
import NewsListItemComments from './NewsListItemComments.vue'
import {useNewsRoutes} from '@/composables/useNewsRoutes'
import type {NewsEntry} from '@/api/news'
import type {MemberIdentity} from '@/api/types'
import NewsExcerpt from '../newsshared/NewsExcerpt.vue'

const props = defineProps<{
  kind: 'local' | 'federated' | 'system'
  id: number
  title: string
  contentHtml?: string
  author?: MemberIdentity | null
  authorName?: string
  publishedAt?: string
  restricted?: boolean
  publicBlog?: boolean
  stationName?: string
  stationUid?: string
  commentCount: number
  localEntry?: NewsEntry
  canEditNews: boolean
  commentsOpen: boolean
  setViewBadgeRef: (el: unknown, newsId: number) => void
  onRequestDelete: (entry: NewsEntry) => void
}>()

const emit = defineEmits<{
  toggleComments: []
}>()

const newsRoutes = useNewsRoutes()

const entryPage = computed(() => props.kind === 'federated'
    ? {name: 'federated-news-detail', params: {stationUid: props.stationUid, newsId: props.id}}
    : {name: newsRoutes.detail, params: {id: props.id}})
</script>

<template>
  <NeutralContainer class="space-y-3 cursor-pointer hover:ring-1 hover:ring-primary transition-all">
    <RowLink :to="entryPage">
      <div class="space-y-3">
        <NewsListItemHeader
          :kind="kind"
          :id="id"
          :title="title"
          :author="author"
          :author-name="authorName"
          :published-at="publishedAt"
          :restricted="restricted"
          :public-blog="publicBlog"
          :station-name="stationName"
          :local-entry="localEntry"
          :can-edit-news="canEditNews"
          :set-view-badge-ref="setViewBadgeRef"
          :on-request-delete="onRequestDelete"
        />
        <NewsExcerpt :content-html="contentHtml"/>
      </div>
    </RowLink>
    <NewsListItemComments
      :news-id="id"
      :station-uid="kind === 'federated' ? stationUid : undefined"
      :comment-count="commentCount"
      :open="commentsOpen"
      @toggle="emit('toggleComments')"
    />
  </NeutralContainer>
</template>
