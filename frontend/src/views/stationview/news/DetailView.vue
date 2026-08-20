/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, nextTick, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
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
import NewsViewBadge from './newsshared/NewsViewBadge.vue'
import AttachmentList from './newsshared/AttachmentList.vue'
import NewsBody from './newsshared/NewsBody.vue'
import NewsEntryHeader from './newsshared/NewsEntryHeader.vue'
import {internalContentContext} from '@/util/contentContext'
import type {NewsEntry} from '@/api/news'
import {news} from '@/api'
import {useSession} from '@/composables/useSession'
import {useConfirmAction} from '@/composables/useConfirmAction'
import {formatDateTime} from '@/util/format'
import ProseContent from '@/components/display/ProseContent.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {canManageNews, sessionInfo} = useSession()
const stationUid = computed(() => sessionInfo.value?.stationId ?? '')

const entry = ref<NewsEntry | null>(null)
const highlightCommentId = ref<number | null>(null)
interface ViewBadgeRef {
  refresh: () => Promise<void>
}

const viewBadge = ref<ViewBadgeRef | null>(null)
const recordedViewIds = new Set<number>()

const {loading, error, reload} = useAsyncLoader(async () => {
  const id = Number(route.params.id)
  entry.value = await news.getNews(id)
  if (entry.value && !recordedViewIds.has(id)) {
    recordedViewIds.add(id)
    news.recordNewsView(id).then(() => viewBadge.value?.refresh())
      .catch(() => recordedViewIds.delete(id))
  }
})

const {
  show: showDeleteModal,
  request: requestDelete,
  confirm: confirmDelete,
} = useConfirmAction<NewsEntry>({
  onConfirm: async e => {
    await news.deleteNews(e.id)
  },
  onSuccess: async () => {
    await router.push({name: 'news-list'})
  },
  error,
})

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

watch(() => route.params.id, reload)
watch(loading, (isLoading) => {
  if (!isLoading) scrollToComment()
})
</script>

<template>
  <ViewContent
      :title="t('pages.news-detail.title')"
      :subtitle="t('pages.news-detail.subtitle')"
  >
    <div class="space-y-4">
      <SecondaryButton :icon="['fas', 'arrow-left']" compact @click="router.push({name: 'news-list'})">
        {{ t('common.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer v-if="entry" class="space-y-3">
        <NewsEntryHeader :entry="entry" :can-manage="canManageNews()">
          <template #actions>
            <NewsViewBadge ref="viewBadge" :news-id="entry.id" :initial-count="entry.viewCount ?? 0" :news-title="entry.title"/>
            <EditButton @click="router.push({name: 'news-edit', params: {id: entry.id}})"/>
            <DeleteButton @click="requestDelete(entry)"/>
          </template>
        </NewsEntryHeader>

        <NewsBody
            :mode="entry.contentMode"
            :rows="entry.rows ?? []"
            :html="entry.contentHtml"
            :context="internalContentContext(stationUid, entry.title)"
        />

        <AttachmentList :attachments="entry.attachments ?? []" :station-uid="stationUid"/>

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
