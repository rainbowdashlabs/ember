/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import CommentThread from './CommentThread.vue'
import type { NewsComment, NewsEntry } from '@/api/types'
import { news } from '@/api'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const router = useRouter()
const { canManageNews, sessionInfo } = useSession()

const PAGE_SIZE = 20

const entries = ref<NewsEntry[]>([])
const loading = ref(true)
const loadingMore = ref(false)
const hasMore = ref(true)
const error = ref('')
const showDeleteModal = ref(false)
const deleteTarget = ref<NewsEntry | null>(null)

// Comments
const commentsOpenId = ref<number | null>(null)
const commentsMap = ref<Map<number, NewsComment[]>>(new Map())
const commentsLoading = ref<Set<number>>(new Set())
const newCommentText = ref('')

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const batch = await news.listNews(0, PAGE_SIZE)
    entries.value = batch
    hasMore.value = batch.length >= PAGE_SIZE
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

async function toggleComments(entry: NewsEntry) {
  if (commentsOpenId.value === entry.id) {
    commentsOpenId.value = null
    return
  }
  commentsOpenId.value = entry.id
  newCommentText.value = ''
  await loadComments(entry.id)
}

async function loadComments(newsId: number) {
  commentsLoading.value = new Set([...commentsLoading.value, newsId])
  try {
    const comments = await news.listComments(newsId)
    commentsMap.value = new Map([...commentsMap.value, [newsId, comments]])
  } catch { /* ignore */ }
  const s = new Set(commentsLoading.value)
  s.delete(newsId)
  commentsLoading.value = s
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

async function submitComment(newsId: number) {
  if (!newCommentText.value.trim()) return
  try {
    await news.createComment(newsId, { content: newCommentText.value.trim() })
    newCommentText.value = ''
    await loadComments(newsId)
    // Update comment count locally
    const entry = entries.value.find(e => e.id === newsId)
    if (entry) entry.commentCount = (commentsMap.value.get(newsId) ?? []).length
  } catch {
    error.value = t('common.error')
  }
}

async function handleReply(newsId: number, parentId: number, content: string) {
  try {
    await news.createComment(newsId, { parentId, content })
    await loadComments(newsId)
    const entry = entries.value.find(e => e.id === newsId)
    if (entry) entry.commentCount = (commentsMap.value.get(newsId) ?? []).length
  } catch {
    error.value = t('common.error')
  }
}

async function handleEditComment(newsId: number, commentId: number, content: string) {
  try {
    await news.updateComment(commentId, { content })
    await loadComments(newsId)
  } catch { /* ignore */ }
}

async function handleDeleteComment(newsId: number, commentId: number) {
  try {
    await news.deleteComment(commentId)
    await loadComments(newsId)
    const entry = entries.value.find(e => e.id === newsId)
    if (entry) entry.commentCount = (commentsMap.value.get(newsId) ?? []).length
  } catch {
    error.value = t('common.error')
  }
}

function getTopLevelComments(newsId: number): NewsComment[] {
  return (commentsMap.value.get(newsId) ?? []).filter(c => c.parentId === null)
}

function getAllComments(newsId: number): NewsComment[] {
  return commentsMap.value.get(newsId) ?? []
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
        <PrimaryButton v-if="canManageNews()" @click="router.push({ name: 'news-create' })">
          <font-awesome-icon :icon="['fas', 'plus']" class="mr-2" />
          {{ t('news.create') }}
        </PrimaryButton>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <div v-if="!loading && entries.length === 0" class="text-center text-(--text-muted) py-8">
        {{ t('news.empty') }}
      </div>

      <div class="space-y-4">
        <NeutralContainer v-for="entry in entries" :key="entry.id" class="space-y-3">
          <!-- Header -->
          <div class="flex items-start justify-between gap-3">
            <div class="flex items-center gap-2">
              <UserAvatar :member-id="entry.authorId" :name="entry.authorName" size="md"/>
              <div>
                <h3 class="font-semibold text-lg flex items-center gap-1">{{ entry.title }}<font-awesome-icon v-if="entry.restricted" :icon="['fas', 'lock']" class="ml-1 h-3 w-3 text-[var(--text-muted)]"/></h3>
                <p class="text-xs text-(--text-muted)">
                  {{ entry.authorName }} &middot; {{ formatDate(entry.publishedAt) }}
                </p>
              </div>
            </div>
            <div v-if="canManageNews()" class="flex items-center gap-1 shrink-0">
              <EditButton @click="router.push({ name: 'news-edit', params: { id: entry.id } })" />
              <DeleteButton @click="requestDelete(entry)" />
            </div>
          </div>

          <!-- Content always visible -->
          <div class="prose prose-sm dark:prose-invert max-w-none" v-html="entry.contentHtml" />

          <!-- Comments toggle -->
          <div class="pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
            <button
              type="button"
              class="text-sm text-(--text-muted) hover:text-primary transition-colors flex items-center gap-1.5"
              @click="toggleComments(entry)"
            >
              <font-awesome-icon :icon="['fas', 'comment']" class="h-3.5 w-3.5" />
              <template v-if="entry.commentCount > 0">
                {{ t('news.commentsCount', { count: entry.commentCount }) }}
              </template>
              <template v-else>
                {{ t('news.addComment') }}
              </template>
              <font-awesome-icon
                :icon="['fas', commentsOpenId === entry.id ? 'chevron-up' : 'chevron-down']"
                class="h-2.5 w-2.5 ml-0.5"
              />
            </button>

            <!-- Comments section (toggled) -->
            <div v-if="commentsOpenId === entry.id" class="mt-3 space-y-3">
              <Spinner v-if="commentsLoading.has(entry.id)" size="sm" />

              <template v-if="!commentsLoading.has(entry.id)">
                <div v-if="getTopLevelComments(entry.id).length === 0" class="text-sm text-(--text-muted)">
                  {{ t('news.noComments') }}
                </div>

                <CommentThread
                  v-for="comment in getTopLevelComments(entry.id)"
                  :key="comment.id"
                  :comment="comment"
                  :all-comments="getAllComments(entry.id)"
                  :current-member-id="sessionInfo?.member?.id ?? 0"
                  :can-moderate="canManageNews()"
                  :depth="0"
                  @reply="(parentId: number, content: string) => handleReply(entry.id, parentId, content)"
                  @edit="(commentId: number, content: string) => handleEditComment(entry.id, commentId, content)"
                  @delete="(commentId: number) => handleDeleteComment(entry.id, commentId)"
                />

                <!-- New top-level comment -->
                <div class="space-y-2 pt-2">
                  <TextAreaInput v-model="newCommentText" :placeholder="t('news.commentPlaceholder')" :rows="2" />
                  <PrimaryButton :disabled="!newCommentText.trim()" @click="submitComment(entry.id)">
                    {{ t('news.submitComment') }}
                  </PrimaryButton>
                </div>
              </template>
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
