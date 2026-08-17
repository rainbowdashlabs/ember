/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {Comment} from '@/api/comments'
import type {MemberGroup} from '@/api/types'
import type {MemberCompletion} from '@/api/stationMembers'
import type {SpecialMention} from '@/components/comment/MentionInput.vue'
import {comments as commentsApi, stationMembers, memberGroups} from '@/api'
import CommentThread from './CommentThread.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'

const props = defineProps<{
  eventId: number
  /**
   * Optional ISO yyyy-MM-dd occurrence date. When provided, the section is scoped to that
   * single occurrence of a recurring event - list filters to comments stamped with this
   * date, and new comments are created with the same stamp. When omitted, the section
   * shows every comment on the event regardless of date.
   */
  eventDate?: string | null
}>()

const {t} = useI18n()

const commentsList = ref<Comment[]>([])
const members = ref<MemberCompletion[]>([])
const groups = ref<MemberGroup[]>([])
const loading = ref(true)
const error = ref('')

const specialMentions = computed<SpecialMention[]>(() => [
  {type: 'EVENT', entityId: props.eventId, label: t('comments.mentionEvent'), icon: ['fas', 'calendar-days']},
  {type: 'REGISTERED', entityId: props.eventId, label: t('comments.mentionRegistered'), icon: ['fas', 'user-check']},
  {type: 'DECLINED', entityId: props.eventId, label: t('comments.mentionDeclined'), icon: ['fas', 'user-slash']},
])

async function loadComments() {
  loading.value = true
  try {
    const [c, m, g] = await Promise.all([
      commentsApi.listEventComments(props.eventId, props.eventDate ?? undefined),
      stationMembers.listCompletions({type: 'EVENT', entityId: props.eventId}),
      memberGroups.listGroups(),
    ])
    commentsList.value = c
    members.value = m
    groups.value = g
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

async function createComment(parentId: number | null, content: string) {
  try {
    // Stamp new comments with the focused date so the thread stays scoped to this
    // occurrence on subsequent reloads. Whole-event views (no eventDate) keep
    // eventDate undefined → backend stores NULL.
    await commentsApi.createEventComment(props.eventId, {
      parentId,
      content,
      eventDate: props.eventDate ?? undefined,
    })
    commentsList.value = await commentsApi.listEventComments(props.eventId, props.eventDate ?? undefined)
  } catch { error.value = t('common.error') }
}

async function updateComment(commentId: number, content: string) {
  try {
    await commentsApi.updateComment(commentId, {content})
    commentsList.value = await commentsApi.listEventComments(props.eventId, props.eventDate ?? undefined)
  } catch { error.value = t('common.error') }
}

async function deleteComment(commentId: number) {
  try {
    await commentsApi.deleteComment(commentId)
    commentsList.value = await commentsApi.listEventComments(props.eventId, props.eventDate ?? undefined)
  } catch { error.value = t('common.error') }
}

onMounted(loadComments)
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('comments.title') }}</SubHeader>
    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Spinner v-if="loading" size="sm"/>

    <template v-if="!loading">
      <CommentThread
        :comments="commentsList"
        :members="members"
        :groups="groups"
        :special-mentions="specialMentions"
        @create="createComment"
        @update="updateComment"
        @delete="deleteComment"
      />
    </template>
  </div>
</template>
