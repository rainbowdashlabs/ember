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
import {useCommentHighlight} from '@/composables/useCommentHighlight'
import {describeFailure, type Failure} from '@/util/failure'
import CommentThread from './CommentThread.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'

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
const {highlightId, revealComment} = useCommentHighlight()

const commentsList = ref<Comment[]>([])
const members = ref<MemberCompletion[]>([])
const groups = ref<MemberGroup[]>([])
const loading = ref(true)
const failure = ref<Failure | null>(null)

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
      stationMembers.listCompletions({type: 'EVENT_VIEW', entityId: props.eventId}),
      memberGroups.listGroups(),
    ])
    commentsList.value = c
    members.value = m
    groups.value = g
  } catch (e) { failure.value = describeFailure(e, t) }
  finally { loading.value = false }
}

/**
 * Changing the thread and then fetching it again, which are two things and not one.
 *
 * <p>They shared an attempt, so a comment that really was posted, followed by a thread that failed to
 * come back, read as a comment that had not been posted. The reader wrote it again and the thread
 * then held it twice. A thread that is merely out of date says so and asks for nothing.
 */
async function act(change: () => Promise<unknown>) {
  failure.value = null
  try {
    await change()
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  try {
    commentsList.value = await commentsApi.listEventComments(props.eventId, props.eventDate ?? undefined)
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
  }
}

/**
 * A new comment carries the date being looked at, so the thread stays scoped to this one occurrence
 * when it is fetched again. A view of the whole event names no date and the comment is stored without
 * one.
 */
function createComment(parentId: number | null, content: string) {
  return act(() => commentsApi.createEventComment(props.eventId, {
    parentId,
    content,
    eventDate: props.eventDate ?? undefined,
  }))
}

function updateComment(commentId: number, content: string) {
  return act(() => commentsApi.updateComment(commentId, {content}))
}

function deleteComment(commentId: number) {
  return act(() => commentsApi.deleteComment(commentId))
}

onMounted(async () => {
  await loadComments()
  await revealComment()
})
</script>

<template>
  <div class="space-y-4">
    <SubHeader>{{ t('comments.title') }}</SubHeader>
    <FailureAlert :failure="failure"/>
    <Spinner v-if="loading" size="sm"/>

    <template v-if="!loading">
      <CommentThread
        :comments="commentsList"
        :members="members"
        :groups="groups"
        :highlight-id="highlightId"
        :special-mentions="specialMentions"
        @create="createComment"
        @update="updateComment"
        @delete="deleteComment"
      />
    </template>
  </div>
</template>
