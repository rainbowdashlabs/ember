/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import NewsList, { type UnifiedNewsItem } from './listview/NewsList.vue'
import type {FederatedNewsItem, NewsEntry} from '@/api/news'
import { news } from '@/api'
import { useSession } from '@/composables/useSession'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { useAsyncLoader } from '@/composables/useAsyncLoader'

const { t } = useI18n()
const router = useRouter()
import { StationPermission } from '@/api/types'
const { hasPermission } = useSession()
const canEditNews = computed(() => hasPermission(StationPermission.NEWS_EDIT))

const PAGE_SIZE = 20

const entries = ref<NewsEntry[]>([])
const loadingMore = ref(false)
const hasMore = ref(true)

const federatedNews = ref<FederatedNewsItem[]>([])

const { loading, error, reload } = useAsyncLoader(async () => {
  const [batch, fed] = await Promise.all([
    news.listNews(0, PAGE_SIZE),
    news.listFederatedNews().catch(() => [] as FederatedNewsItem[]),
  ])
  entries.value = batch
  hasMore.value = batch.length >= PAGE_SIZE
  federatedNews.value = fed
})

const {
  show: showDeleteModal,
  target: deleteTarget,
  requestDelete,
  confirm: confirmDelete,
} = useConfirmDelete<NewsEntry>({
  onDelete: e => news.deleteNews(e.id),
  onSuccess: () => reload(),
  error,
})

const commentsOpenId = ref<string | null>(null)

const recordedViews = ref<Set<number>>(new Set())
const newsItemRefs = ref<Map<number, HTMLElement>>(new Map())
const VIEW_OBSERVE_THRESHOLDS = [0, 0.25, 0.5, 0.75, 1.0]
const VIEW_VISIBLE_RATIO = 0.5
const VIEW_VIEWPORT_COVER = 0.5
const VIEW_DWELL_MS = 800
const pendingDwell = new Map<number, number>()
let intersectionObserver: IntersectionObserver | null = null

function isMeaningfullyVisible(obs: IntersectionObserverEntry): boolean {
  if (!obs.isIntersecting) return false
  if (obs.intersectionRatio >= VIEW_VISIBLE_RATIO) return true
  const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 1
  return obs.intersectionRect.height >= VIEW_VIEWPORT_COVER * viewportHeight
}

function setNewsItemRef(refObj: unknown, newsId: number) {
  let el: HTMLElement | null = null
  if (refObj instanceof HTMLElement) {
    el = refObj
  } else if (refObj && typeof refObj === 'object' && '$el' in refObj) {
    const candidate = (refObj as {$el: unknown}).$el
    if (candidate instanceof HTMLElement) el = candidate
  }
  if (el) {
    newsItemRefs.value.set(newsId, el)
    intersectionObserver?.observe(el)
  } else {
    const existing = newsItemRefs.value.get(newsId)
    if (existing) intersectionObserver?.unobserve(existing)
    newsItemRefs.value.delete(newsId)
  }
}

type BadgeHandle = {refresh: () => Promise<void>}
const viewBadgeRefs = new Map<number, BadgeHandle>()

function setViewBadgeRef(el: unknown, newsId: number) {
  if (el && typeof (el as BadgeHandle).refresh === 'function') {
    viewBadgeRefs.set(newsId, el as BadgeHandle)
  } else {
    viewBadgeRefs.delete(newsId)
  }
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

async function loadMore() {
  if (loading.value || loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const batch = await news.listNews(entries.value.length, PAGE_SIZE)
    const known = new Set(entries.value.map(e => e.id))
    const fresh = batch.filter(e => !known.has(e.id))
    entries.value = [...entries.value, ...fresh]
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

function openItem(item: UnifiedNewsItem) {
  if (item.kind === 'federated') {
    router.push({name: 'federated-news-detail', params: {stationUid: item.stationUid, newsId: item.id}})
  } else {
    router.push({name: 'news-detail', params: {id: item.id}})
  }
}

function toggleComments(item: UnifiedNewsItem) {
  const key = itemKey(item)
  commentsOpenId.value = commentsOpenId.value === key ? null : key
}

onMounted(() => {
  window.addEventListener('scroll', onScroll)

  intersectionObserver = new IntersectionObserver((observations) => {
    for (const obs of observations) {
      const idAttr = (obs.target as HTMLElement).dataset.newsId
      if (!idAttr) continue
      const id = Number(idAttr)
      if (recordedViews.value.has(id)) continue
      if (isMeaningfullyVisible(obs)) {
        if (!pendingDwell.has(id)) {
          const handle = window.setTimeout(() => {
            pendingDwell.delete(id)
            if (recordedViews.value.has(id)) return
            recordedViews.value.add(id)
            news.recordNewsView(id).then(() => {
              viewBadgeRefs.get(id)?.refresh()
            }).catch(() => {
              recordedViews.value.delete(id)
            })
          }, VIEW_DWELL_MS)
          pendingDwell.set(id, handle)
        }
      } else if (pendingDwell.has(id)) {
        window.clearTimeout(pendingDwell.get(id)!)
        pendingDwell.delete(id)
      }
    }
  }, {threshold: VIEW_OBSERVE_THRESHOLDS})
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  intersectionObserver?.disconnect()
  intersectionObserver = null
  for (const handle of pendingDwell.values()) window.clearTimeout(handle)
  pendingDwell.clear()
})

watch(() => entries.value.length, async () => {
  await nextTick()
  for (const [id, el] of newsItemRefs.value) {
    if (recordedViews.value.has(id)) continue
    intersectionObserver?.observe(el)
  }
})
</script>

<template>
  <ViewContent
      :title="t('pages.news-list.title')"
      :subtitle="t('pages.news-list.subtitle')"
  >
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <PrimaryButton :icon="['fas', 'plus']" v-if="canEditNews" @click="router.push({ name: 'news-create' })">
          {{ t('news.create') }}
        </PrimaryButton>
      </div>

      <AsyncSection
        :empty="allNews.length === 0"
        :empty-message="t('news.empty')"
        :error="error"
        :loading="loading"
      >
        <NewsList
          :items="allNews"
          :can-edit-news="canEditNews"
          :comments-open-key="commentsOpenId"
          :item-key="itemKey"
          :set-news-item-ref="setNewsItemRef"
          :set-view-badge-ref="setViewBadgeRef"
          :on-open="openItem"
          :on-toggle-comments="toggleComments"
          :on-request-delete="requestDelete"
        />

        <div v-if="loadingMore" class="flex justify-center py-4">
          <Spinner size="md" />
        </div>
      </AsyncSection>

      <ConfirmDeleteModal
        v-model="showDeleteModal"
        :message="t('news.deleteConfirm', { title: deleteTarget?.title })"
        @confirm="confirmDelete"
      />
    </div>
  </ViewContent>
</template>
