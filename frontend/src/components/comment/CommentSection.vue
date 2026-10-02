/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import type {CommentSource} from '@/api/comments'
import type {CommentResponse, MemberCompletion, MemberGroup} from '@/api/generated/schema'
import type {SpecialMention} from '@/components/comment/MentionInput.vue'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useCommentHighlight} from '@/composables/useCommentHighlight'
import {describeFailure} from '@/util/failure'
import CommentThread from './CommentThread.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'

/**
 * The comments under anything that takes them: an appointment, a news entry, a wiki article or a
 * board ticket, the station's own or a partner's. Everything that differs between them comes from the
 * source, so the thread reads, writes and offers its buttons the same way everywhere.
 *
 * <p>The source is read again whenever it changes, so a caller hands over one built in a computed and
 * not one built inline in its template.
 */
const props = withDefaults(defineProps<{
  source: CommentSource
  /** Leaves the heading out where the surrounding page already names the comments, such as a tab. */
  untitled?: boolean
  /** Shows the thread without a field for a new comment. */
  readonly?: boolean
}>(), {
  untitled: false,
  readonly: false,
})

const emit = defineEmits<{
  /** The thread as it was last read, for a page that counts or lists the comments elsewhere too. */
  loaded: [comments: CommentResponse[]]
}>()

const {t} = useI18n()
const {highlightId, revealComment} = useCommentHighlight()

const comments = ref<CommentResponse[]>([])
const members = ref<MemberCompletion[]>([])
const groups = ref<MemberGroup[]>([])

function show(thread: CommentResponse[]) {
  comments.value = thread
  emit('loaded', thread)
}

const {loading, failure, reload} = useAsyncLoader(async (isCurrent) => {
  const [thread, mentionables] = await Promise.all([props.source.list(), props.source.mentionables()])
  if (!isCurrent()) return
  show(thread)
  members.value = mentionables.members
  groups.value = mentionables.groups
}, {autoLoad: false})
loading.value = true

const specialMentions = computed<SpecialMention[]>(() => {
  const eventId = props.source.eventId
  if (eventId === undefined) return []
  return [
    {type: 'EVENT', entityId: eventId, label: t('comments.mentionEvent'), icon: ['fas', 'calendar-days']},
    {type: 'REGISTERED', entityId: eventId, label: t('comments.mentionRegistered'), icon: ['fas', 'user-check']},
    {type: 'DECLINED', entityId: eventId, label: t('comments.mentionDeclined'), icon: ['fas', 'user-slash']},
  ]
})

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
    show(await props.source.list())
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
  }
}

function createComment(parentId: number | null, content: string) {
  return act(() => props.source.create(parentId, content))
}

function updateComment(commentId: number, content: string) {
  return act(() => props.source.update(commentId, content))
}

function deleteComment(commentId: number) {
  return act(() => props.source.remove(commentId))
}

watch(() => props.source, reload)

onMounted(async () => {
  await reload()
  await revealComment()
})
</script>

<template>
  <div class="space-y-4">
    <SubHeader v-if="!untitled">{{ t('comments.title') }}</SubHeader>
    <FailureAlert :failure="failure"/>
    <Spinner v-if="loading" size="sm"/>

    <template v-if="!loading">
      <CommentThread
        :comments="comments"
        :members="members"
        :groups="groups"
        :highlight-id="highlightId"
        :special-mentions="specialMentions"
        :moderator="source.moderator"
        :readonly="readonly"
        @create="createComment"
        @update="updateComment"
        @delete="deleteComment"
      />
    </template>
  </div>
</template>
