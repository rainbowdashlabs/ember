/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

import NewsCommentSection from '@/components/comment/NewsCommentSection.vue'
import type { NewsEntry, MemberIdentity } from '@/api/types'
import type { FederatedNewsItem } from '@/api/news'
import { news } from '@/api'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const router = useRouter()
import { StationPermission } from '@/api/types'
const { hasPermission } = useSession()
const canEditNews = computed(() => hasPermission(StationPermission.NEWS_EDIT))

const PAGE_SIZE = 20

const entries = ref<NewsEntry[]>([])
const loading = ref(true)
const loadingMore = ref(false)
const hasMore = ref(true)
const error = ref('')
const showDeleteModal = ref(false)
const deleteTarget = ref<NewsEntry | null>(null)

// Partner news
const federatedNews = ref<FederatedNewsItem[]>([])

// Comments
const commentsOpenId = ref<string | null>(null)

interface UnifiedNewsItem {
  kind: 'local' | 'federated'
  id: number
  title: string
  contentHtml?: string
  authorName?: string
  author?: MemberIdentity | null
  publishedAt?: string
  commentCount: number
  restricted?: boolean
  publicBlog?: boolean
  // federated only
  stationName?: string
  stationUid?: string
  // local only
  localEntry?: NewsEntry
}

const allNews = computed<UnifiedNewsItem[]>(() => {
  const local: UnifiedNewsItem[] = entries.value.map(e => ({
    kind: 'local',
    id: e.id,
    title: e.title,
    contentHtml: e.contentHtml,
    authorName: e.authorName,
    author: e.author,
    publishedAt: e.publishedAt,
    commentCount: e.commentCount,
    restricted: e.restricted,
    publicBlog: e.publicBlog,
    localEntry: e,
  }))
  const federated: UnifiedNewsItem[] = federatedNews.value.map(fn => ({
    kind: 'federated',
    id: fn.id,
    title: fn.title,
    contentHtml: fn.contentHtml,
    authorName: fn.authorName,
    publishedAt: fn.publishedAt,
    commentCount: fn.commentCount,
    stationName: fn.stationName,
    stationUid: fn.stationId,
  }))
  return [...local, ...federated].sort((a, b) => {
    const da = a.publishedAt ? new Date(a.publishedAt).getTime() : 0
    const db = b.publishedAt ? new Date(b.publishedAt).getTime() : 0
    return db - da
  })
})

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [batch, fed] = await Promise.all([
      news.listNews(0, PAGE_SIZE),
      news.listFederatedNews().catch(() => [] as FederatedNewsItem[]),
    ])
    entries.value = batch
    hasMore.value = batch.length >= PAGE_SIZE
    federatedNews.value = fed
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const batch = await news.listNews(entries.value.length, PAGE_SIZE)
    entries.value = [...entries.value, ...batch]
    hasMore.value = batch.length >= PAGE_SIZE
  } catch {
    error.value = t('common.error')
  } finally {
    loadingMore.value = false
  }
}

function onScroll() {
  const scrollBottom = window.innerHeight + window.scrollY
  const docHeight = document.documentElement.scrollHeight
  if (docHeight - scrollBottom < 300) {
    loadMore()
  }
}

function itemKey(item: UnifiedNewsItem): string {
  return `${item.kind}-${item.stationUid ?? 'local'}-${item.id}`
}

function toggleComments(item: UnifiedNewsItem) {
  const key = itemKey(item)
  commentsOpenId.value = commentsOpenId.value === key ? null : key
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function requestDelete(entry: NewsEntry) {
  deleteTarget.value = entry
  showDeleteModal.value = true
}

async function confirmDelete() {
  if (!deleteTarget.value) return
  try {
    await news.deleteNews(deleteTarget.value.id)
    showDeleteModal.value = false
    deleteTarget.value = null
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

onMounted(() => {
  loadData()
  window.addEventListener('scroll', onScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
})
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('news.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']" v-if="canEditNews" @click="router.push({ name: 'news-create' })">
          {{ t('news.create') }}
        </PrimaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <EmptyState v-if="!loading && allNews.length === 0">{{ t('news.empty') }}</EmptyState>

      <div class="space-y-4">
        <NeutralContainer
          v-for="item in allNews"
          :key="itemKey(item)"
          class="space-y-3"
          :class="{ 'cursor-pointer hover:ring-1 hover:ring-primary transition-all': item.kind === 'federated' }"
          @click="item.kind === 'federated' ? router.push({ name: 'federated-news-detail', params: { stationUid: item.stationUid, newsId: item.id } }) : undefined"
        >
          <!-- Header -->
          <div class="flex items-start justify-between gap-3">
            <div class="flex items-center gap-2">
              <UserAvatar v-if="item.author || item.authorName" :identity="item.author" :name="item.author?.name ?? item.authorName" size="md"/>
              <div>
                <SubHeader class="flex items-center gap-1">
                  <router-link v-if="item.kind === 'local'" :to="{name: 'news-detail', params: {id: item.id}}" class="hover:text-primary hover:underline">{{ item.title }}</router-link>
                  <span v-else>{{ item.title }}</span>
                  <font-awesome-icon v-if="item.restricted" :icon="['fas', 'lock']" class="ml-1 h-3 w-3 text-[var(--text-muted)]"/>
                  <SuccessBadge v-if="item.publicBlog" class="ml-1">{{ t('news.publicBlogBadge') }}</SuccessBadge>
                  <StationBadge v-if="item.kind === 'federated'" :station-name="item.stationName!" class="ml-1"/>
                </SubHeader>
                <p class="text-xs text-(--text-muted)">
                  <template v-if="item.author?.name || item.authorName">{{ item.author?.name ?? item.authorName }} &middot; </template>
                  {{ formatDate(item.publishedAt) }}
                </p>
              </div>
            </div>
            <div v-if="item.kind === 'local' && canEditNews" class="flex items-center gap-1 shrink-0">
              <EditButton @click.stop="router.push({ name: 'news-edit', params: { id: item.id } })" />
              <DeleteButton @click.stop="requestDelete(item.localEntry!)" />
            </div>
          </div>

          <!-- Content (local only — federated shows on detail page) -->
          <div v-if="item.contentHtml" class="prose prose-sm dark:prose-invert max-w-none" v-html="item.contentHtml" />

          <!-- Comments toggle -->
          <div class="pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent" @click.stop>
            <button
              type="button"
              class="text-sm text-(--text-muted) hover:text-primary transition-colors flex items-center gap-1.5"
              @click="toggleComments(item)"
            >
              <font-awesome-icon :icon="['fas', 'comment']" class="h-3.5 w-3.5" />
              <template v-if="item.commentCount > 0">
                {{ t('news.commentsCount', { count: item.commentCount }) }}
              </template>
              <template v-else>
                {{ t('news.addComment') }}
              </template>
              <font-awesome-icon
                :icon="['fas', commentsOpenId === itemKey(item) ? 'chevron-up' : 'chevron-down']"
                class="h-2.5 w-2.5 ml-0.5"
              />
            </button>

            <!-- Comments section (toggled) -->
            <div v-if="commentsOpenId === itemKey(item)" class="mt-3">
              <NewsCommentSection
                :news-id="item.id"
                :station-uid="item.kind === 'federated' ? item.stationUid : undefined"
              />
            </div>
          </div>
        </NeutralContainer>
      </div>

      <Spinner v-if="loadingMore" size="md" />

      <Modal v-model="showDeleteModal">
        <div class="space-y-4">
          <p>{{ t('news.deleteConfirm', { title: deleteTarget?.title }) }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showDeleteModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton @click="confirmDelete">{{ t('common.delete') }}</ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
