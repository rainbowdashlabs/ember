/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import BareButton from '@/components/button/BareButton.vue'
import Popover from '@/components/feedback/Popover.vue'
import EmojiPicker from '@/components/emoji/EmojiPicker.vue'
import type {ConversationMessageResponse} from '@/api/generated/schema'
import {QUICK_REACTIONS, type MessageActions} from './messageActions'

/**
 * The small bar a mouse finds over a message: six quick reactions, the full emoji picker, answering,
 * and for the reader's own message changing and deleting it, for somebody else's blocking them.
 */
const props = defineProps<{
  message: ConversationMessageResponse
  actions: MessageActions
}>()

const {t} = useI18n()

function own(): boolean {
  return !!props.message.author && props.message.author.uid === props.actions.readerUid
}
</script>

<template>
  <div class="flex items-center gap-0.5 rounded-theme border border-(--border) bg-(--bg) px-1 shadow-sm"
       :data-testid="`message-actions-${message.id}`">
    <ul class="flex items-center">
      <li v-for="emoji in QUICK_REACTIONS" :key="emoji">
        <BareButton :aria-label="t('messages.actions.reactWith', {emoji})" class="rounded-theme px-1 text-base hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
                    @click="actions.react(message, emoji)">{{ emoji }}</BareButton>
      </li>
    </ul>
    <Popover :label="t('messages.actions.moreEmoji')" role="dialog" align="end" panel-class="p-0">
      <template #trigger="{toggle, triggerAttrs}">
        <IconButton v-bind="triggerAttrs" :icon="['fas', 'face-smile']" :label="t('messages.actions.moreEmoji')" @click="toggle"/>
      </template>
      <template #default="{close}">
        <EmojiPicker @pick="emoji => { actions.react(message, emoji); close() }"/>
      </template>
    </Popover>
    <IconButton :icon="['fas', 'reply']" :label="t('messages.actions.reply')" @click="actions.reply(message)"/>
    <template v-if="own()">
      <IconButton :icon="['fas', 'pen']" :label="t('messages.actions.edit')" @click="actions.edit(message)"/>
      <IconButton :icon="['fas', 'trash']" :label="t('messages.actions.delete')" class="text-error" @click="actions.remove(message)"/>
    </template>
    <IconButton v-else-if="message.author" :icon="['fas', 'ban']" :label="t('messages.block')"
                @click="message.author && actions.block(message.author)"/>
  </div>
</template>
