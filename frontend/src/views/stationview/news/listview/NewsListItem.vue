/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import NewsListItemHeader from './NewsListItemHeader.vue'
import NewsListItemComments from './NewsListItemComments.vue'
import type {NewsEntry} from '@/api/news'
import type {MemberIdentity} from '@/api/types'
import ProseContent from '@/components/display/ProseContent.vue'

defineProps<{
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
  open: []
  toggleComments: []
}>()
</script>

<template>
  <NeutralContainer
    class="space-y-3 cursor-pointer hover:ring-1 hover:ring-primary transition-all"
    @click="emit('open')"
  >
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
    <ProseContent v-if="contentHtml" v-html="contentHtml"/>
    <NewsListItemComments
      :news-id="id"
      :station-uid="kind === 'federated' ? stationUid : undefined"
      :comment-count="commentCount"
      :open="commentsOpen"
      @toggle="emit('toggleComments')"
    />
  </NeutralContainer>
</template>
