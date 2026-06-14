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
import NewsViewBadge from '@/components/news/NewsViewBadge.vue'
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

// View tracking — record a view once the news entry has been meaningfully visible for a moment.
// Only fires for local news entries; federated views are tracked by the originating station.
// The trigger is "either half of the card is visible OR the card covers at least half of the
// viewport" — the second branch is what makes long articles (taller than the screen) countable.
const recordedViews = ref<Set<number>>(new Set())
const newsItemRefs = ref<Map<number, HTMLElement>>(new Map())
const VIEW_OBSERVE_THRESHOLDS = [0, 0.25, 0.5, 0.75, 1.0]
const VIEW_VISIBLE_RATIO = 0.5          // 50% of the card visible
const VIEW_VIEWPORT_COVER = 0.5         // OR the visible portion covers 50% of the viewport
const VIEW_DWELL_MS = 800               // must stay visible this long before counting as "seen"
const pendingDwell = new Map<number, number>()
let intersectionObserver: IntersectionObserver | null = null

function isMeaningfullyVisible(obs: IntersectionObserverEntry): boolean {
  if (!obs.isIntersecting) return false
  if (obs.intersectionRatio >= VIEW_VISIBLE_RATIO) return true
  const viewportHeight = window.innerHeight || document.documentElement.clientHeight || 1
  return obs.intersectionRect.height >= VIEW_VIEWPORT_COVER * viewportHeight
}

function setNewsItemRef(refObj: unknown, newsId: number) {
  // The v-for target is a Vue component (NeutralContainer), so the function ref
  // receives the component instance proxy rather than a DOM element. Peel down to
  // the underlying root element via `$el` (single-root component).
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
  // Guard against firing while the initial load is still in flight — otherwise the
  // offset is stale (zero) and we'd re-fetch the same first page, producing duplicate
  // entries and triggering Vue's "Duplicate keys" warning.
  if (loading.value || loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  try {
    const batch = await news.listNews(entries.value.length, PAGE_SIZE)
    // Defensive dedupe in case a stale request still returns overlapping ids.
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

  // Wire an IntersectionObserver to detect when a news entry is fully visible.
  // Each entry gets a short dwell timer so a quick scroll-past doesn't count as a view.
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
              // Badge owns its own count; nudge it to re-fetch now that the
              // server has the just-recorded view.
              viewBadgeRefs.get(id)?.refresh()
            }).catch(() => {
              // Best-effort — let the next page reload re-attempt. Server-side
              // INSERT … ON CONFLICT DO NOTHING means duplicate calls are a no-op.
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

// As the list grows (initial load + loadMore), re-observe any newly-rendered local
// news entries. New refs are also auto-observed in setNewsItemRef when they mount.
watch(() => entries.value.length, async () => {
  await nextTick()
  for (const [id, el] of newsItemRefs.value) {
    if (recordedViews.value.has(id)) continue
    intersectionObserver?.observe(el)
  }
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
          :ref="(el: unknown) => item.kind === 'local' ? setNewsItemRef(el, item.id) : null"
          :data-news-id="item.kind === 'local' ? item.id : undefined"
          class="space-y-3 cursor-pointer hover:ring-1 hover:ring-primary transition-all"
          @click="openItem(item)"
        >
          <!-- Header -->
          <div class="flex items-start justify-between gap-3">
            <div class="flex items-center gap-2">
              <UserAvatar v-if="item.author || item.authorName" :identity="item.author" :name="item.author?.name ?? item.authorName" size="md"/>
              <div>
                <SubHeader class="flex items-center gap-1">
                  <span>{{ item.title }}</span>
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
              <NewsViewBadge
                :ref="(el: unknown) => setViewBadgeRef(el, item.id)"
                :news-id="item.id"
                :initial-count="item.localEntry?.viewCount ?? 0"
                :news-title="item.title"
              />
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
