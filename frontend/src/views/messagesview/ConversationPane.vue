/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import type {ConversationMessageResponse, MessagePersonResponse} from '@/api/generated/schema'
import {block, getBlocks, leaveConversation, muteConversation, unblock} from '@/api/messages'
import {useConversation} from '@/composables/useConversation'
import {useMessageWords} from '@/composables/useMessageWords'
import {useSession} from '@/composables/useSession'
import {mentionablesOf} from '@/util/messageText'
import {timelineOf} from '@/util/messageTimeline'
import {personName} from '@/util/conversationTitle'
import ConversationHeader from './conversationpane/ConversationHeader.vue'
import MessageTimeline from './conversationpane/MessageTimeline.vue'
import MessageComposer from './conversationpane/MessageComposer.vue'
import GroupMembersPanel from './conversationpane/GroupMembersPanel.vue'
import MessageActionSheet from './conversationpane/MessageActionSheet.vue'
import type {MessageActions} from './conversationpane/messageActions'

/**
 * One open conversation: its header, its messages and the box to write in. Reading it at the
 * bottom marks it read; deleting, blocking and leaving each ask first.
 */
const props = defineProps<{
  uid: string
  onChanged: () => Promise<void> | void
}>()

/** A question asked before something that cannot be taken back, with what answering yes does. */
interface Confirmation {
  title: string
  message: string
  label: string
  run: () => Promise<void>
}

const {t} = useI18n()
const router = useRouter()
const {sessionInfo} = useSession()
const {titleOf} = useMessageWords()
const conversation = useConversation(props.uid)
const {detail, messages, hasOlder, unreadAfter, gone, loading, failure} = conversation

const readerUid = computed(() => sessionInfo.value?.account?.uid ?? null)
const blocked = ref<string[]>([])
const replyTo = ref<ConversationMessageResponse | null>(null)
const editing = ref<ConversationMessageResponse | null>(null)
const membersOpen = ref(false)
const menuMessage = ref<ConversationMessageResponse | null>(null)
const menuOpen = ref(false)
const confirmation = ref<Confirmation | null>(null)
const confirming = ref(false)
const atBottom = ref(true)

const others = computed(() => detail.value?.members.filter(member => member.uid !== readerUid.value) ?? [])
const title = computed(() => detail.value ? titleOf({...detail.value, others: others.value}) : '')
const directOther = computed(() => detail.value?.kind === 'DIRECT' ? others.value[0] ?? null : null)
const items = computed(() => timelineOf(messages.value, unreadAfter.value, readerUid.value))
const mentionables = computed(() => detail.value?.kind === 'GROUP' ? mentionablesOf(others.value) : [])

async function loadBlocks() {
  blocked.value = (await getBlocks().catch(() => [])).map(person => person.uid)
}

onMounted(loadBlocks)

async function changed() {
  await conversation.refreshDetail()
  await props.onChanged()
}

watch([() => messages.value.length, atBottom], async () => {
  if (!atBottom.value) return
  await conversation.markRead()
  await props.onChanged()
})

watch(gone, isGone => { if (isGone) void props.onChanged() })

function ask(question: Confirmation) {
  confirmation.value = question
  confirming.value = true
}

async function confirm() {
  const question = confirmation.value
  confirming.value = false
  if (question) await question.run()
}

function askBlock(person: MessagePersonResponse) {
  ask({
    title: t('messages.blockConfirm.title', {name: personName(person)}),
    message: t('messages.blockConfirm.text'),
    label: t('messages.block'),
    run: async () => {
      await block(person.uid)
      await loadBlocks()
      await changed()
    },
  })
}

async function setBlocked(person: MessagePersonResponse, wanted: boolean) {
  if (wanted) return askBlock(person)
  await unblock(person.uid)
  await loadBlocks()
  await changed()
}

function askLeave() {
  const group = detail.value?.kind === 'GROUP'
  ask({
    title: group ? t('messages.leaveGroup') : t('messages.closeDirect'),
    message: group ? t('messages.leaveConfirm.group') : t('messages.leaveConfirm.direct'),
    label: group ? t('messages.leaveGroup') : t('messages.closeDirect'),
    run: async () => {
      await leaveConversation(props.uid)
      await router.push('/messages')
      await props.onChanged()
    },
  })
}

async function mute(muted: boolean) {
  await muteConversation(props.uid, muted)
  await changed()
}

function editLast() {
  const own = [...messages.value].reverse()
    .find(message => !message.systemKind && !message.deleted && message.author?.uid === readerUid.value)
  if (own) editing.value = own
}

function jumpTo(messageId: number) {
  document.getElementById(`message-${messageId}`)?.scrollIntoView({behavior: 'smooth', block: 'center'})
}

const actions = computed<MessageActions>(() => ({
  readerUid: readerUid.value,
  blocked: blocked.value,
  reply: message => { editing.value = null; replyTo.value = message },
  edit: message => { replyTo.value = null; editing.value = message },
  remove: message => ask({
    title: t('messages.deleteConfirm.title'),
    message: t('messages.deleteConfirm.text'),
    label: t('messages.actions.delete'),
    run: () => conversation.remove(message.id),
  }),
  react: (message, emoji) => { void conversation.toggleReaction(message, emoji) },
  block: askBlock,
  jumpTo,
  menu: message => { menuMessage.value = message; menuOpen.value = true },
}))

async function send(text: string) {
  await conversation.send(text, replyTo.value?.id ?? null)
  replyTo.value = null
  await props.onChanged()
}

async function save(message: ConversationMessageResponse, text: string) {
  await conversation.edit(message.id, text)
  editing.value = null
}
</script>

<template>
  <Spinner v-if="loading" size="lg" class="m-auto"/>
  <FailureAlert :failure="failure" class="m-3"/>
  <EmptyState v-if="gone" :message="t('messages.gone')"/>
  <template v-else-if="detail">
    <ConversationHeader :conversation="detail" :title="title" :others="others" :other="directOther"
                        :other-blocked="!!directOther && blocked.includes(directOther.uid)"
                        @members="membersOpen = true" @mute="mute" @leave="askLeave" @block="setBlocked"/>
    <MessageTimeline :items="items" :has-older="hasOlder" :actions="actions" :load-older="conversation.loadOlder"
                     @at-bottom="value => atBottom = value"/>
    <MessageComposer :placeholder="t('messages.composer.placeholder', {name: title})" :mentionables="mentionables"
                     :reply-to="replyTo" :editing="editing" :send="send" :save="save"
                     @cancel="replyTo = null; editing = null" @edit-last="editLast"/>
    <GroupMembersPanel v-if="detail.kind === 'GROUP'" v-model="membersOpen" :conversation="detail"
                       :reader-uid="readerUid" :on-changed="changed"/>
    <MessageActionSheet v-if="menuMessage" v-model="menuOpen" :message="menuMessage" :actions="actions"/>
    <ConfirmDeleteModal v-if="confirmation" v-model="confirming" :title="confirmation.title"
                        :message="confirmation.message" :confirm-label="confirmation.label" @confirm="confirm"/>
  </template>
</template>
