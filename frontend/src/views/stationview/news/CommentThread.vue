/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import type { NewsComment } from '@/api/types'

const { t } = useI18n()

const props = defineProps<{
  comment: NewsComment
  allComments: NewsComment[]
  currentMemberId: number
  canModerate: boolean
  depth: number
}>()

const emit = defineEmits<{
  reply: [parentId: number, content: string]
  edit: [commentId: number, content: string]
  delete: [commentId: number]
}>()

const showReply = ref(false)
const replyContent = ref('')
const editing = ref(false)
const editContent = ref('')

const isAuthor = computed(() => props.comment.authorId === props.currentMemberId)

const children = computed(() =>
    props.allComments.filter(c => c.parentId === props.comment.id)
)

function formatDate(dateStr: string): string {
  return new Date(dateStr).toLocaleDateString('de-DE', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
  })
}

function submitReply() {
  if (!replyContent.value.trim()) return
  emit('reply', props.comment.id, replyContent.value.trim())
  replyContent.value = ''
  showReply.value = false
}

function startEdit() {
  editContent.value = props.comment.content
  editing.value = true
}

function submitEdit() {
  if (!editContent.value.trim()) return
  emit('edit', props.comment.id, editContent.value.trim())
  editing.value = false
}
</script>

<template>
  <div :class="depth > 0 ? 'ml-4 sm:ml-6 border-l-2 border-bg-light-accent dark:border-bg-dark-accent pl-3 sm:pl-4' : ''">
    <div class="py-2">
      <div class="flex items-center gap-2">
        <UserAvatar :member-id="comment.authorId" :name="comment.authorName" size="sm"/>
        <span class="text-sm font-medium">{{ comment.authorName }}</span>
        <span class="text-xs text-(--text-muted)">{{ formatDate(comment.createdAt) }}</span>
        <div class="flex items-center gap-0.5">
          <IconButton
            :icon="['fas', 'comment']"
            :label="t('news.reply')"
            class="text-primary hover:bg-primary/15"
            @click="showReply = !showReply"
          />
          <EditButton v-if="isAuthor" @click="startEdit" />
          <DeleteButton v-if="isAuthor || canModerate" @click="emit('delete', comment.id)" />
        </div>
      </div>

      <!-- Edit form -->
      <div v-if="editing" class="mt-1 space-y-2">
        <TextAreaInput v-model="editContent" :rows="2" />
        <div class="flex gap-2">
          <PrimaryButton :disabled="!editContent.trim()" @click="submitEdit">
            {{ t('common.save') }}
          </PrimaryButton>
          <SecondaryButton @click="editing = false">
            {{ t('common.cancel') }}
          </SecondaryButton>
        </div>
      </div>

      <!-- Content -->
      <p v-else class="text-sm mt-1 whitespace-pre-wrap">{{ comment.content }}</p>

      <!-- Reply form -->
      <div v-if="showReply" class="mt-2 space-y-2">
        <TextAreaInput v-model="replyContent" :placeholder="t('news.replyPlaceholder')" :rows="2" />
        <div class="flex gap-2">
          <PrimaryButton :disabled="!replyContent.trim()" @click="submitReply">
            {{ t('news.submitReply') }}
          </PrimaryButton>
          <SecondaryButton @click="showReply = false">
            {{ t('common.cancel') }}
          </SecondaryButton>
        </div>
      </div>
    </div>

    <!-- Recursive children -->
    <CommentThread
      v-for="child in children"
      :key="child.id"
      :comment="child"
      :all-comments="allComments"
      :current-member-id="currentMemberId"
      :can-moderate="canModerate"
      :depth="depth + 1"
      @reply="(parentId, content) => emit('reply', parentId, content)"
      @edit="(id, content) => emit('edit', id, content)"
      @delete="(id) => emit('delete', id)"
    />
  </div>
</template>
