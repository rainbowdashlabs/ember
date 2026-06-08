/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {Comment, MemberGroup} from '@/api/types'
import type {MemberCompletion} from '@/api/stationMembers'
import MentionInput, {type SpecialMention} from '@/components/comment/MentionInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {useSession} from '@/composables/useSession'

const props = withDefaults(defineProps<{
  comments: Comment[]
  members: MemberCompletion[]
  groups?: MemberGroup[]
  specialMentions?: SpecialMention[]
  parentId?: number | null
  depth?: number
  highlightId?: number | null
  federated?: boolean
  readonly?: boolean
}>(), {
  groups: () => [],
  specialMentions: () => [],
})

const emit = defineEmits<{
  create: [parentId: number | null, content: string]
  update: [commentId: number, content: string]
  delete: [commentId: number]
}>()

const {t} = useI18n()
const {sessionInfo, hasPermission} = useSession()
const currentStationId = computed(() => sessionInfo.value?.stationId ?? '')
const currentMemberUid = computed(() => sessionInfo.value?.member?.uid ?? '')
const isManager = computed(() => hasPermission('EVENT_MANAGER') || hasPermission('STATION_ADMINISTRATOR'))

function isOwnComment(comment: Comment): boolean {
  if (!comment.author) return false
  return comment.author.stationUid === currentStationId.value && comment.author.memberUid === currentMemberUid.value
}


const sortDesc = ref(true)
const newComment = ref('')
const replyingTo = ref<number | null>(null)
const replyContent = ref('')
const editingId = ref<number | null>(null)
const editContent = ref('')

const rootComments = computed(() => {
  const filtered = props.comments.filter(c => (c.parentId ?? null) === (props.parentId ?? null))
  const isRoot = (props.depth ?? 0) === 0
  return filtered.sort((a, b) =>
      isRoot && sortDesc.value
          ? b.createdAt.localeCompare(a.createdAt)
          : a.createdAt.localeCompare(b.createdAt))
})

function childrenOf(commentId: number): Comment[] {
  return props.comments.filter(c => c.parentId === commentId)
}

type MentionPart = { type: 'text'; value: string } | { type: 'mention'; name: string; bulk?: boolean }

function resolveMentions(text: string): MentionPart[] {
  const parts: MentionPart[] = []
  const regex = /@\[([^\]]+)]/g
  let last = 0
  let match: RegExpExecArray | null
  while ((match = regex.exec(text)) !== null) {
    if (match.index > last) {
      parts.push({type: 'text', value: text.substring(last, match.index)})
    }
    const inner = match[1]
    const colonIdx = inner.indexOf(':')
    const slashIdx = inner.indexOf('/')
    // Bulk mention: type:name:id (e.g. group:Vorstand:5)
    const bulkMatch = inner.match(/^(GROUP|EVENT|REGISTERED|DECLINED):([^:]+):\d+$/)
    if (bulkMatch) {
      parts.push({type: 'mention', name: bulkMatch[2], bulk: true})
    } else if (slashIdx >= 0 && colonIdx > slashIdx) {
      const memberUid = inner.substring(slashIdx + 1, colonIdx)
      const fallback = inner.substring(colonIdx + 1)
      const member = props.members.find(m => m.memberUid === memberUid)
      parts.push({type: 'mention', name: member?.name?.trim() || fallback})
    } else if (colonIdx >= 0) {
      const id = parseInt(inner.substring(0, colonIdx))
      const fallback = inner.substring(colonIdx + 1)
      const member = props.members.find(m => m.id === id)
      parts.push({type: 'mention', name: member?.name?.trim() || fallback})
    } else {
      parts.push({type: 'mention', name: inner})
    }
    last = regex.lastIndex
  }
  if (last < text.length) {
    parts.push({type: 'text', value: text.substring(last)})
  }
  return parts
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

function postTopLevel() {
  if (!newComment.value.trim()) return
  emit('create', props.parentId ?? null, newComment.value.trim())
  newComment.value = ''
}

const maxDepth = 6
</script>

<template>
  <div :class="(depth ?? 0) > 0 ? 'ml-4 pl-3 border-l-2 border-(--border)' : ''">
    <!-- Top-level new comment input (only at root depth, when not readonly) -->
    <div v-if="(depth ?? 0) === 0 && !readonly" class="space-y-2 mb-4">
      <MentionInput v-model="newComment" :members="members" :groups="groups" :special-mentions="specialMentions" :placeholder="t('comments.placeholder')"/>
      <div class="flex gap-2">
        <PrimaryButton :disabled="!newComment.trim()" compact @click="postTopLevel">{{ t('comments.post') }}</PrimaryButton>
      </div>
    </div>
    <div v-if="(depth ?? 0) === 0 && rootComments.length > 1" class="flex justify-end mb-2">
      <SecondaryButton compact @click="sortDesc = !sortDesc">
        <font-awesome-icon :icon="['fas', sortDesc ? 'arrow-down-wide-short' : 'arrow-up-wide-short']" class="mr-1" />
        {{ sortDesc ? t('comments.newest') : t('comments.oldest') }}
      </SecondaryButton>
    </div>
    <div v-for="comment in rootComments" :key="comment.id" :id="`comment-${comment.id}`"
         class="space-y-2 py-2 transition-colors duration-1000"
         :class="{'bg-primary/10 rounded-theme px-2 -mx-2': highlightId === comment.id}"
    >
      <!-- Deleted comment placeholder -->
      <div v-if="comment.deleted" class="space-y-1">
        <div class="flex items-center gap-2">
          <MutedText size="xs">{{ formatDate(comment.createdAt) }}</MutedText>
        </div>
        <p class="text-sm italic text-(--text-muted)">{{ t('comments.deleted') }}</p>
      </div>
      <!-- Comment content -->
      <div v-else-if="editingId !== comment.id" class="space-y-1">
        <div class="flex items-center gap-2">
          <MemberName :identity="comment.author" class="text-sm font-medium"/>
          <MutedText size="xs">{{ formatDate(comment.createdAt) }}</MutedText>
          <MutedText v-if="comment.updatedAt" size="xs">({{ t('comments.edited') }})</MutedText>
        </div>
        <p class="text-sm whitespace-pre-wrap"><template v-for="(part, i) in resolveMentions(comment.content)" :key="i"><span v-if="part.type === 'mention'" class="font-semibold" :class="part.bulk ? 'text-secondary' : 'text-primary'">@{{ part.name }}</span><template v-else>{{ part.value }}</template></template></p>
        <div class="flex items-center gap-1">
          <SecondaryButton v-if="(depth ?? 0) < maxDepth" compact @click="startReply(comment.id)">
            {{ t('comments.reply') }}
          </SecondaryButton>
          <IconButton
            v-if="isOwnComment(comment)"
            :icon="['fas', 'pen']"
            :label="t('comments.edit')"
            class="text-(--text-muted) hover:text-primary"
            @click="startEdit(comment)"
          />
          <IconButton
            v-if="federated ? isOwnComment(comment) : (isOwnComment(comment) || isManager)"
            :icon="['fas', 'trash']"
            :label="t('comments.delete')"
            class="text-(--text-muted) hover:text-error"
            @click="emit('delete', comment.id)"
          />
        </div>
      </div>

      <!-- Edit form -->
      <div v-else class="space-y-2">
        <MentionInput v-model="editContent" :members="members" :groups="groups" :special-mentions="specialMentions"/>
        <div class="flex gap-2">
          <PrimaryButton compact @click="submitEdit">{{ t('comments.save') }}</PrimaryButton>
          <SecondaryButton compact @click="cancelEdit">{{ t('common.cancel') }}</SecondaryButton>
        </div>
      </div>

      <!-- Reply form -->
      <div v-if="replyingTo === comment.id" class="ml-4 space-y-2">
        <MentionInput v-model="replyContent" :members="members" :groups="groups" :special-mentions="specialMentions" :placeholder="t('comments.replyPlaceholder')"/>
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
        :groups="groups"
        :special-mentions="specialMentions"
        :parent-id="comment.id"
        :depth="(depth ?? 0) + 1"
        :highlight-id="highlightId"
        :federated="federated"
        :readonly="readonly"
        @create="(p, c) => emit('create', p, c)"
        @update="(id, c) => emit('update', id, c)"
        @delete="(id) => emit('delete', id)"
      />
    </div>
  </div>
</template>
