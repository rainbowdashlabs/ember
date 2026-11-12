/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import BareButton from '@/components/button/BareButton.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import EmojiPicker from '@/components/emoji/EmojiPicker.vue'
import type {ConversationMessageResponse} from '@/api/generated/schema'
import {personName} from '@/util/conversationTitle'
import {QUICK_REACTIONS, type MessageActions} from './messageActions'

/**
 * The actions of a message as a menu, for a phone, where resting a finger on the message opens it
 * because there is no hover. It also says who reacted with what.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  message: ConversationMessageResponse
  actions: MessageActions
}>()

const {t} = useI18n()
const picking = ref(false)

const own = computed(() => !!props.message.author && props.message.author.uid === props.actions.readerUid)

function run(action: () => void) {
  open.value = false
  picking.value = false
  action()
}
</script>

<template>
  <Modal v-model="open" size="sm">
    <div class="space-y-3 p-2">
      <SubHeader>{{ t('messages.actions.menu') }}</SubHeader>
      <ul class="flex justify-between">
        <li v-for="emoji in QUICK_REACTIONS" :key="emoji">
          <BareButton :aria-label="t('messages.actions.reactWith', {emoji})" class="rounded-theme p-1 text-2xl"
                      @click="run(() => actions.react(message, emoji))">{{ emoji }}</BareButton>
        </li>
      </ul>
      <EmojiPicker v-if="picking" @pick="emoji => run(() => actions.react(message, emoji))"/>
      <ul>
        <li v-if="!picking"><DropdownMenuItem :icon="['fas', 'face-smile']" @click="picking = true">{{ t('messages.actions.moreEmoji') }}</DropdownMenuItem></li>
        <li><DropdownMenuItem :icon="['fas', 'reply']" @click="run(() => actions.reply(message))">{{ t('messages.actions.reply') }}</DropdownMenuItem></li>
        <li v-if="own"><DropdownMenuItem :icon="['fas', 'pen']" @click="run(() => actions.edit(message))">{{ t('messages.actions.edit') }}</DropdownMenuItem></li>
        <li v-if="own"><DropdownMenuItem :icon="['fas', 'trash']" destructive @click="run(() => actions.remove(message))">{{ t('messages.actions.delete') }}</DropdownMenuItem></li>
        <li v-if="!own && message.author">
          <DropdownMenuItem :icon="['fas', 'ban']" destructive @click="run(() => message.author && actions.block(message.author))">{{ t('messages.block') }}</DropdownMenuItem>
        </li>
      </ul>
      <MutedText v-for="group in message.reactions" :key="group.emoji" tag="p">
        {{ group.emoji }} {{ group.people.map(personName).join(', ') }}
      </MutedText>
    </div>
  </Modal>
</template>
