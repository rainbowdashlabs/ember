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
import type { NewsComment } from '@/api/types'

const { t } = useI18n()

const props = defineProps<{
  comment: NewsComment
  allComments: NewsComment[]
  canDelete: boolean
  depth: number
}>()

const emit = defineEmits<{
  reply: [parentId: number, content: string]
  delete: [commentId: number]
}>()

const showReply = ref(false)
const replyContent = ref('')

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
</script>

<template>
  <div :class="depth > 0 ? 'ml-4 sm:ml-6 border-l-2 border-bg-light-accent dark:border-bg-dark-accent pl-3 sm:pl-4' : ''">
    <div class="py-2">
      <div class="flex items-start justify-between gap-2">
        <div>
          <span class="text-sm font-medium">{{ comment.authorName }}</span>
          <span class="text-xs text-(--text-muted) ml-2">{{ formatDate(comment.createdAt) }}</span>
        </div>
        <div class="flex items-center gap-1 shrink-0">
          <button
            type="button"
            class="text-xs text-primary hover:underline"
            @click="showReply = !showReply"
          >
            {{ t('news.reply') }}
          </button>
          <DeleteButton v-if="canDelete" @click="emit('delete', comment.id)" />
        </div>
      </div>
      <p class="text-sm mt-1 whitespace-pre-wrap">{{ comment.content }}</p>

      <!-- Reply form -->
      <div v-if="showReply" class="mt-2 space-y-2">
        <TextAreaInput v-model="replyContent" :placeholder="t('news.replyPlaceholder')" :rows="2" />
        <div class="flex gap-2">
          <PrimaryButton class="text-xs" :disabled="!replyContent.trim()" @click="submitReply">
            {{ t('news.submitReply') }}
          </PrimaryButton>
          <SecondaryButton class="text-xs" @click="showReply = false">
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
      :can-delete="canDelete"
      :depth="depth + 1"
      @reply="(parentId, content) => emit('reply', parentId, content)"
      @delete="(id) => emit('delete', id)"
    />
  </div>
</template>
