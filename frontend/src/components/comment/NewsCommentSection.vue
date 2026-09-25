/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {Comment} from '@/api/comments'
import type {MemberGroup} from '@/api/types'
import type {MemberCompletion} from '@/api/stationMembers'
import {news, stationMembers, memberGroups} from '@/api'
import {useCommentHighlight} from '@/composables/useCommentHighlight'
import {describeFailure, saying, type Failure} from '@/util/failure'
import CommentThread from './CommentThread.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'

const props = defineProps<{
  newsId: number
  stationUid?: string
}>()

const {t} = useI18n()
const {highlightId, revealComment} = useCommentHighlight()

const commentsList = ref<Comment[]>([])
const members = ref<MemberCompletion[]>([])
const groups = ref<MemberGroup[]>([])
const loading = ref(true)
const failure = ref<Failure | null>(null)
async function loadComments() {
  loading.value = true
  try {
    const rawComments = props.stationUid
      ? await news.listFederatedNewsComments(props.stationUid, props.newsId)
      : await news.listComments(props.newsId)
    // Adapt NewsComment to generic Comment interface. Federated comments arrive with
    // author.name === null because the partner station's member isn't known locally;
    // backfill from authorName so MemberName/UserAvatar have something to render.
    commentsList.value = rawComments.map(c => ({
      id: c.id,
      parentId: c.parentId,
      author: c.author
        ? {...c.author, name: c.author.name || c.authorName || null}
        : null,
      authorName: c.authorName,
      content: c.content,
      deleted: c.deleted,
      createdAt: c.createdAt,
      updatedAt: c.updatedAt ?? null,
    }))
    // Mention suggestions: only meaningful for local news. For federated news the
    // local station's members/groups don't apply, and a local NEWS entityId lookup
    // would 404 on the completions endpoint and tank the whole load.
    if (props.stationUid) {
      members.value = []
      groups.value = []
    } else {
      const [m, g] = await Promise.all([
        stationMembers.listCompletions({type: 'NEWS', entityId: props.newsId}),
        memberGroups.listGroups(),
      ])
      members.value = m
      groups.value = g
    }
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
  await loadComments()
  const stale = failure.value
  if (stale) failure.value = saying(stale, t('failure.staleAfterAction'))
}

function createComment(parentId: number | null, content: string) {
  return act(() => (props.stationUid
      ? news.createFederatedNewsComment(props.stationUid, props.newsId, {parentId, content})
      : news.createComment(props.newsId, {parentId, content})))
}

function updateComment(commentId: number, content: string) {
  return act(() => (props.stationUid
      ? news.updateFederatedNewsComment(props.stationUid, commentId, {content})
      : news.updateComment(commentId, {content})))
}

function deleteComment(commentId: number) {
  return act(() => (props.stationUid
      ? news.deleteFederatedNewsComment(props.stationUid, commentId)
      : news.deleteComment(commentId)))
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
        @create="createComment"
        @update="updateComment"
        @delete="deleteComment"
      />
    </template>
  </div>
</template>
