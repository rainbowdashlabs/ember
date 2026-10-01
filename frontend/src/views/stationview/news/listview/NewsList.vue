/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import NewsListItem from './NewsListItem.vue'
import type {MemberIdentity, NewsResponse} from '@/api/generated/schema'

export interface UnifiedNewsItem {
  kind: 'local' | 'federated' | 'system'
  id: number
  title: string
  contentHtml?: string
  authorName?: string
  author?: MemberIdentity | null
  publishedAt?: string | null
  commentCount: number
  restricted?: boolean
  publicBlog?: boolean
  stationName?: string
  stationUid?: string
  localEntry?: NewsResponse
}

defineProps<{
  items: UnifiedNewsItem[]
  canEditNews: boolean
  commentsOpenKey: string | null
  itemKey: (item: UnifiedNewsItem) => string
  setNewsItemRef: (el: unknown, newsId: number) => void
  setViewBadgeRef: (el: unknown, newsId: number) => void
  onToggleComments: (item: UnifiedNewsItem) => void
  onRequestDelete: (entry: NewsResponse) => void
}>()
</script>

<template>
  <div class="space-y-4">
    <NewsListItem
      v-for="item in items"
      :id="item.id"
      :key="itemKey(item)"
      :ref="(el: unknown) => item.kind === 'local' ? setNewsItemRef(el, item.id) : null"
      :data-news-id="item.kind === 'local' ? item.id : undefined"
      :kind="item.kind"
      :title="item.title"
      :content-html="item.contentHtml"
      :author="item.author"
      :author-name="item.authorName"
      :published-at="item.publishedAt"
      :restricted="item.restricted"
      :public-blog="item.publicBlog"
      :station-name="item.stationName"
      :station-uid="item.stationUid"
      :comment-count="item.commentCount"
      :local-entry="item.localEntry"
      :can-edit-news="canEditNews"
      :comments-open="commentsOpenKey === itemKey(item)"
      :set-view-badge-ref="setViewBadgeRef"
      :on-request-delete="onRequestDelete"
      @toggle-comments="onToggleComments(item)"
    />
  </div>
</template>
