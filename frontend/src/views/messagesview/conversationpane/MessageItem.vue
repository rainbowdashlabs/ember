/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {ConversationMessageResponse} from '@/api/generated/schema'
import {useMessageWords} from '@/composables/useMessageWords'
import {useFinePointer} from '@/composables/useFinePointer'
import {useLongPress} from '@/composables/useLongPress'
import {formatTime} from '@/util/format'
import {mentions} from '@/util/messageText'
import SystemLine from './SystemLine.vue'
import MessageBody from './MessageBody.vue'
import ReplyQuote from './ReplyQuote.vue'
import ReactionChips from './ReactionChips.vue'
import MessageActionBar from './MessageActionBar.vue'
import type {MessageActions} from './messageActions'

/**
 * One message of a conversation. The first of a run from one author shows the avatar, the name and
 * the time; the ones following within seven minutes show only their text and their time on hover.
 * A mouse finds the actions on hover, a finger by resting on the message.
 */
const props = defineProps<{
  message: ConversationMessageResponse
  opensRun: boolean
  actions: MessageActions
}>()

const {nameOf} = useMessageWords()
const {finePointer} = useFinePointer()
const press = useLongPress(() => {
  if (!props.message.deleted) props.actions.menu(props.message)
})

const mentionsReader = computed(() =>
  props.actions.readerUid !== null && mentions(props.message.content, props.actions.readerUid))

const highlight = computed(() => mentionsReader.value
  ? 'bg-primary/10 border-l-2 border-primary'
  : 'hover:bg-bg-light-accent/60 dark:hover:bg-bg-dark-accent/60')
</script>

<template>
  <SystemLine v-if="message.systemKind" :message="message"/>
  <div v-else :id="`message-${message.id}`" :data-testid="`message-${message.id}`"
       :class="[opensRun ? 'mt-3' : '', highlight]" class="group relative flex gap-3 rounded-theme px-2 py-0.5"
       v-on="press">
    <div class="w-9 shrink-0">
      <UserAvatar v-if="opensRun" :identity="message.author ? {accountUid: message.author.uid} : null"
                  :name="nameOf(message.author)" class="h-9 w-9"/>
      <MutedText v-else class="hidden pt-1 text-[10px] group-hover:inline">{{ formatTime(message.createdAt) }}</MutedText>
    </div>
    <div class="min-w-0 flex-1">
      <p v-if="opensRun" class="flex items-baseline gap-2">
        <span class="text-sm font-semibold">{{ nameOf(message.author) }}</span>
        <MutedText>{{ formatTime(message.createdAt) }}</MutedText>
      </p>
      <ReplyQuote v-if="message.replyTo" :reply="message.replyTo" @jump="actions.jumpTo"/>
      <MessageBody :message="message" :reader-uid="actions.readerUid"
                   :blocked="!!message.author && actions.blocked.includes(message.author.uid)"/>
      <ReactionChips v-if="message.reactions.length > 0" :reactions="message.reactions"
                     @toggle="emoji => actions.react(message, emoji)"/>
    </div>
    <div v-if="finePointer && !message.deleted" class="absolute -top-3 right-2 hidden group-hover:block group-focus-within:block">
      <MessageActionBar :message="message" :actions="actions"/>
    </div>
  </div>
</template>
