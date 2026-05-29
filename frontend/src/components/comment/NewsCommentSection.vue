/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {Comment} from '@/api/types'
import type {MemberCompletion} from '@/api/stationMembers'
import {news, stationMembers} from '@/api'
import CommentThread from './CommentThread.vue'
import MentionInput from './MentionInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'

const props = defineProps<{
  newsId: number
  highlightCommentId?: number | null
  stationUid?: string
}>()

const {t} = useI18n()

const commentsList = ref<Comment[]>([])
const members = ref<MemberCompletion[]>([])
const loading = ref(true)
const error = ref('')
const newComment = ref('')
const posting = ref(false)

async function loadComments() {
  loading.value = true
  try {
    const rawComments = props.stationUid
      ? await news.listFederatedNewsComments(props.stationUid, props.newsId)
      : await news.listComments(props.newsId)
    const m = await stationMembers.listCompletions()
    // Adapt NewsComment to generic Comment interface
    commentsList.value = rawComments.map(c => ({
      id: c.id,
      parentId: c.parentId,
      authorId: c.authorId,
      authorName: c.authorName,
      content: c.content,
      deleted: c.deleted,
      createdAt: c.createdAt,
      updatedAt: c.updatedAt ?? null,
      federatedAuthor: c.federatedAuthor ?? null,
    }))
    members.value = m
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

async function createComment(parentId: number | null, content: string) {
  try {
    if (props.stationUid) {
      await news.createFederatedNewsComment(props.stationUid, props.newsId, {parentId, content})
    } else {
      await news.createComment(props.newsId, {parentId, content})
    }
    await loadComments()
  } catch { error.value = t('common.error') }
}

async function updateComment(commentId: number, content: string) {
  try {
    if (props.stationUid) {
      await news.updateFederatedNewsComment(props.stationUid, commentId, {content})
    } else {
      await news.updateComment(commentId, {content})
    }
    await loadComments()
  } catch { error.value = t('common.error') }
}

async function deleteComment(commentId: number) {
  try {
    if (props.stationUid) {
      await news.deleteFederatedNewsComment(props.stationUid, commentId)
    } else {
      await news.deleteComment(commentId)
    }
    await loadComments()
  } catch { error.value = t('common.error') }
}

async function postTopLevel() {
  if (!newComment.value.trim()) return
  posting.value = true
  await createComment(null, newComment.value.trim())
  newComment.value = ''
  posting.value = false
}

onMounted(loadComments)
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('comments.title') }}</SubHeader>
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Spinner v-if="loading" size="sm"/>

    <template v-if="!loading">
      <div class="space-y-2">
        <MentionInput v-model="newComment" :members="members" :placeholder="t('comments.placeholder')"/>
        <PrimaryButton :disabled="posting || !newComment.trim()" compact @click="postTopLevel">
          {{ t('comments.post') }}
        </PrimaryButton>
      </div>

      <CommentThread
        :comments="commentsList"
        :members="members"
        :highlight-id="highlightCommentId"
        @create="createComment"
        @update="updateComment"
        @delete="deleteComment"
      />
    </template>
  </div>
</template>
