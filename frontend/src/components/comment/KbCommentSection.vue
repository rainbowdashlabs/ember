/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {Comment, MemberGroup} from '@/api/types'
import type {MemberCompletion} from '@/api/stationMembers'
import {knowledgeBase, stationMembers, memberGroups} from '@/api'
import CommentThread from './CommentThread.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'

const props = defineProps<{
  fileId: number
  stationUid?: string
}>()

const {t} = useI18n()
const isFederated = computed(() => !!props.stationUid)

const commentsList = ref<Comment[]>([])
const members = ref<MemberCompletion[]>([])
const groups = ref<MemberGroup[]>([])
const loading = ref(true)
const error = ref('')
async function loadComments() {
  loading.value = true
  try {
    const rawComments = isFederated.value
      ? await knowledgeBase.listFederatedComments(props.stationUid!, props.fileId)
      : await knowledgeBase.listComments(props.fileId)
    commentsList.value = rawComments.map(c => ({
      id: c.id,
      parentId: c.parentId,
      author: c.author,
      authorName: c.authorName,
      content: c.content,
      deleted: c.deleted,
      createdAt: c.createdAt,
      updatedAt: c.updatedAt ?? null,
    }))
    if (!isFederated.value) {
      const [m, g] = await Promise.all([
        stationMembers.listCompletions({type: 'KB_FILE', entityId: props.fileId}),
        memberGroups.listGroups(),
      ])
      members.value = m
      groups.value = g
    }
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

async function createComment(parentId: number | null, content: string) {
  try {
    if (isFederated.value) {
      await knowledgeBase.createFederatedComment(props.stationUid!, props.fileId, {parentId, content})
    } else {
      await knowledgeBase.createComment(props.fileId, {parentId, content})
    }
    await loadComments()
  } catch { error.value = t('common.error') }
}

async function updateComment(commentId: number, content: string) {
  try {
    if (isFederated.value) {
      await knowledgeBase.updateFederatedComment(props.stationUid!, commentId, {content})
    } else {
      await knowledgeBase.updateComment(commentId, {content})
    }
    await loadComments()
  } catch { error.value = t('common.error') }
}

async function deleteComment(commentId: number) {
  try {
    if (isFederated.value) {
      await knowledgeBase.deleteFederatedComment(props.stationUid!, commentId)
    } else {
      await knowledgeBase.deleteComment(commentId)
    }
    await loadComments()
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
        @create="createComment"
        @update="updateComment"
        @delete="deleteComment"
      />
    </template>
  </div>
</template>
