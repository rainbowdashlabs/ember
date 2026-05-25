/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {Comment, StationMember} from '@/api/types'
import {comments as commentsApi, stationMembers} from '@/api'
import CommentThread from './CommentThread.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'

const props = defineProps<{
  eventId: number
}>()

const {t} = useI18n()

const commentsList = ref<Comment[]>([])
const members = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')
const newComment = ref('')
const posting = ref(false)

async function loadComments() {
  loading.value = true
  try {
    const [c, m] = await Promise.all([
      commentsApi.listEventComments(props.eventId),
      stationMembers.listMembers(),
    ])
    commentsList.value = c
    members.value = m
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

async function createComment(parentId: number | null, content: string) {
  try {
    await commentsApi.createEventComment(props.eventId, {parentId, content})
    commentsList.value = await commentsApi.listEventComments(props.eventId)
  } catch { error.value = t('common.error') }
}

async function updateComment(commentId: number, content: string) {
  try {
    await commentsApi.updateComment(commentId, {content})
    commentsList.value = await commentsApi.listEventComments(props.eventId)
  } catch { error.value = t('common.error') }
}

async function deleteComment(commentId: number) {
  try {
    await commentsApi.deleteComment(commentId)
    commentsList.value = await commentsApi.listEventComments(props.eventId)
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
      <!-- New comment form -->
      <div class="space-y-2">
        <TextAreaInput v-model="newComment" :rows="2" :placeholder="t('comments.placeholder')"/>
        <PrimaryButton :disabled="posting || !newComment.trim()" compact @click="postTopLevel">
          {{ t('comments.post') }}
        </PrimaryButton>
      </div>

      <!-- Thread -->
      <CommentThread
        :comments="commentsList"
        :members="members"
        @create="createComment"
        @update="updateComment"
        @delete="deleteComment"
      />
    </template>
  </div>
</template>
