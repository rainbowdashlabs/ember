/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {nextTick, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import NewsCommentSection from '@/components/comment/NewsCommentSection.vue'
import type {NewsEntry} from '@/api/types'
import {news} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {canManageNews} = useSession()

const entry = ref<NewsEntry | null>(null)
const loading = ref(true)
const error = ref('')
const showDeleteModal = ref(false)
const highlightCommentId = ref<number | null>(null)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const id = Number(route.params.id)
    entry.value = await news.getNews(id)
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function formatDate(dateStr?: string): string {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleDateString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

async function confirmDelete() {
  if (!entry.value) return
  try {
    await news.deleteNews(entry.value.id)
    showDeleteModal.value = false
    await router.push({name: 'news-list'})
  } catch {
    error.value = t('common.error')
  }
}

function scrollToComment() {
  const commentId = route.query.comment
  if (!commentId) return
  highlightCommentId.value = Number(commentId)
  nextTick(() => {
    setTimeout(() => {
      const el = document.getElementById(`comment-${commentId}`)
      if (el) {
        el.scrollIntoView({behavior: 'smooth', block: 'center'})
      }
    }, 500)
  })
}

onMounted(loadData)
watch(() => route.params.id, loadData)
watch(loading, (isLoading) => {
  if (!isLoading) scrollToComment()
})
</script>

<template>
  <ViewContent>
    <div class="space-y-4">
      <SecondaryButton :icon="['fas', 'arrow-left']" compact @click="router.push({name: 'news-list'})">
        {{ t('common.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer v-if="entry" class="space-y-3">
        <div class="flex items-start justify-between gap-3">
          <div class="flex items-center gap-2">
            <UserAvatar :member-id="entry.authorId" :name="entry.authorName" size="md"/>
            <div>
              <SubHeader class="flex items-center gap-1">
                {{ entry.title }}
                <font-awesome-icon v-if="entry.restricted" :icon="['fas', 'lock']"
                                   class="ml-1 h-3 w-3 text-[var(--text-muted)]"/>
              </SubHeader>
              <p class="text-xs text-(--text-muted)">
                {{ entry.authorName }} &middot; {{ formatDate(entry.publishedAt) }}
              </p>
            </div>
          </div>
          <div v-if="canManageNews()" class="flex items-center gap-1 shrink-0">
            <EditButton @click="router.push({name: 'news-edit', params: {id: entry.id}})"/>
            <DeleteButton @click="showDeleteModal = true"/>
          </div>
        </div>

        <div class="prose prose-sm dark:prose-invert max-w-none" v-html="entry.contentHtml"/>

        <div class="pt-3 border-t border-bg-light-accent dark:border-bg-dark-accent">
          <NewsCommentSection :news-id="entry.id" :highlight-comment-id="highlightCommentId"/>
        </div>
      </NeutralContainer>

      <Modal v-model="showDeleteModal">
        <template #title>{{ t('news.deleteConfirmTitle') }}</template>
        <p class="mb-4">{{ t('news.deleteConfirmMessage') }}</p>
        <div class="flex gap-2 justify-end">
          <SecondaryButton @click="showDeleteModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <ErrorButton @click="confirmDelete">{{ t('common.delete') }}</ErrorButton>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
