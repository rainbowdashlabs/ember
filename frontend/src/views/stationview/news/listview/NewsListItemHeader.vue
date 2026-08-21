/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import SystemBadge from '@/components/badge/SystemBadge.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import NewsViewBadge from '../newsshared/NewsViewBadge.vue'
import type {NewsEntry} from '@/api/news'
import type {MemberIdentity} from '@/api/types'
import {formatDateTime} from '@/util/format'

interface BadgeHandle {refresh: () => Promise<void>}

const props = defineProps<{
  kind: 'local' | 'federated' | 'system'
  id: number
  title: string
  author?: MemberIdentity | null
  authorName?: string
  publishedAt?: string
  restricted?: boolean
  publicBlog?: boolean
  stationName?: string
  localEntry?: NewsEntry
  canEditNews: boolean
  setViewBadgeRef: (el: unknown, newsId: number) => void
  onRequestDelete: (entry: NewsEntry) => void
}>()

const {t} = useI18n()
const router = useRouter()

function viewBadgeRef(el: unknown) {
  props.setViewBadgeRef(el, props.id)
}
</script>

<template>
  <div class="flex items-start justify-between gap-3">
    <div class="flex items-center gap-2">
      <UserAvatar v-if="author || authorName" :identity="author" :name="author?.name ?? authorName" size="md"/>
      <div>
        <SubHeader class="flex items-center gap-1">
          <span>{{ title }}</span>
          <font-awesome-icon v-if="restricted" :icon="['fas', 'lock']" class="ml-1 h-3 w-3 text-[var(--text-muted)]"/>
          <SuccessBadge v-if="publicBlog" class="ml-1">{{ t('news.publicBlogBadge') }}</SuccessBadge>
          <StationBadge v-if="kind === 'federated'" :station-name="stationName!" class="ml-1"/>
          <SystemBadge v-else-if="kind === 'system'" class="ml-1"/>
        </SubHeader>
        <p class="text-xs text-(--text-muted)">
          <template v-if="author?.name || authorName">{{ author?.name ?? authorName }} &middot; </template>
          {{ formatDateTime(publishedAt) }}
        </p>
      </div>
    </div>
    <div v-if="kind === 'local' && canEditNews" class="flex items-center gap-1 shrink-0">
      <NewsViewBadge
        :ref="viewBadgeRef"
        :news-id="id"
        :initial-count="localEntry?.viewCount ?? 0"
        :news-title="title"
      />
      <EditButton @click.stop="router.push({ name: 'news-edit', params: { id: id } })"/>
      <DeleteButton @click.stop="onRequestDelete(localEntry!)"/>
    </div>
  </div>
</template>
