/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {Comment, StationMember} from '@/api/types'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import MentionInput from '@/components/comment/MentionInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useSession} from '@/composables/useSession'

const props = defineProps<{
  comments: Comment[]
  members: StationMember[]
  parentId?: number | null
  depth?: number
}>()

const emit = defineEmits<{
  create: [parentId: number | null, content: string]
  update: [commentId: number, content: string]
  delete: [commentId: number]
}>()

const {t} = useI18n()
const {sessionInfo, hasRole} = useSession()
const currentMemberId = computed(() => sessionInfo.value?.member?.id ?? 0)
const isManager = computed(() => hasRole('EVENT_MANAGER') || hasRole('MANAGER'))

const replyingTo = ref<number | null>(null)
const replyContent = ref('')
const editingId = ref<number | null>(null)
const editContent = ref('')

const rootComments = computed(() =>
    props.comments
        .filter(c => (c.parentId ?? null) === (props.parentId ?? null))
        .sort((a, b) => a.createdAt.localeCompare(b.createdAt)))

function childrenOf(commentId: number): Comment[] {
  return props.comments.filter(c => c.parentId === commentId)
}

function memberName(memberId: number): string {
  const m = props.members.find(m => m.id === memberId)
  if (!m) return `#${memberId}`
  return m.name?.trim() || m.email || `#${memberId}`
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

function startReply(commentId: number) {
  replyingTo.value = commentId
  replyContent.value = ''
}

function cancelReply() {
  replyingTo.value = null
  replyContent.value = ''
}

function submitReply() {
  if (!replyContent.value.trim()) return
  emit('create', replyingTo.value, replyContent.value.trim())
  cancelReply()
}

function startEdit(comment: Comment) {
  editingId.value = comment.id
  editContent.value = comment.content
}

function cancelEdit() {
  editingId.value = null
  editContent.value = ''
}

function submitEdit() {
  if (!editContent.value.trim() || editingId.value == null) return
  emit('update', editingId.value, editContent.value.trim())
  cancelEdit()
}

const maxDepth = 6
</script>

<template>
  <div :class="(depth ?? 0) > 0 ? 'ml-4 pl-3 border-l-2 border-(--border)' : ''">
    <div v-for="comment in rootComments" :key="comment.id" class="space-y-2 py-2">
      <!-- Comment content -->
      <div v-if="editingId !== comment.id" class="space-y-1">
        <div class="flex items-center gap-2">
          <MemberName :name="memberName(comment.authorId)" :member-id="comment.authorId" class="text-sm font-medium"/>
          <MutedText size="xs">{{ formatDate(comment.createdAt) }}</MutedText>
          <MutedText v-if="comment.updatedAt" size="xs">({{ t('comments.edited') }})</MutedText>
        </div>
        <p class="text-sm whitespace-pre-wrap">{{ comment.content }}</p>
        <div class="flex items-center gap-1">
          <SecondaryButton v-if="(depth ?? 0) < maxDepth" compact @click="startReply(comment.id)">
            {{ t('comments.reply') }}
          </SecondaryButton>
          <IconButton
            v-if="comment.authorId === currentMemberId"
            :icon="['fas', 'pen']"
            :label="t('comments.edit')"
            class="text-(--text-muted) hover:text-primary"
            @click="startEdit(comment)"
          />
          <IconButton
            v-if="comment.authorId === currentMemberId || isManager"
            :icon="['fas', 'trash']"
            :label="t('comments.delete')"
            class="text-(--text-muted) hover:text-error"
            @click="emit('delete', comment.id)"
          />
        </div>
      </div>

      <!-- Edit form -->
      <div v-else class="space-y-2">
        <TextAreaInput v-model="editContent" :rows="2"/>
        <div class="flex gap-2">
          <PrimaryButton compact @click="submitEdit">{{ t('comments.save') }}</PrimaryButton>
          <SecondaryButton compact @click="cancelEdit">{{ t('common.cancel') }}</SecondaryButton>
        </div>
      </div>

      <!-- Reply form -->
      <div v-if="replyingTo === comment.id" class="ml-4 space-y-2">
        <MentionInput v-model="replyContent" :members="members" :placeholder="t('comments.replyPlaceholder')"/>
        <div class="flex gap-2">
          <PrimaryButton compact @click="submitReply">{{ t('comments.reply') }}</PrimaryButton>
          <SecondaryButton compact @click="cancelReply">{{ t('common.cancel') }}</SecondaryButton>
        </div>
      </div>

      <!-- Recursive children -->
      <CommentThread
        v-if="childrenOf(comment.id).length > 0"
        :comments="comments"
        :members="members"
        :parent-id="comment.id"
        :depth="(depth ?? 0) + 1"
        @create="(p, c) => emit('create', p, c)"
        @update="(id, c) => emit('update', id, c)"
        @delete="(id) => emit('delete', id)"
      />
    </div>
  </div>
</template>
