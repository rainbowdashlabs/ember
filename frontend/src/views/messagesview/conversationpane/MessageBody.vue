/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import MutedText from '@/components/typography/MutedText.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import type {ConversationMessageResponse} from '@/api/generated/schema'
import MessageText from './MessageText.vue'

/**
 * What a message says: its text, marked when it was changed, the words for a deleted message, or a
 * folded line for a message of somebody the reader blocked, which opens on request.
 */
defineProps<{
  message: ConversationMessageResponse
  blocked: boolean
  readerUid: string | null
}>()

const {t} = useI18n()
const unfolded = ref(false)
</script>

<template>
  <MutedText v-if="message.deleted" tag="p" size="sm" class="italic">{{ t('messages.deleted') }}</MutedText>
  <p v-else-if="blocked && !unfolded" class="flex items-center gap-2">
    <MutedText size="sm" class="italic">{{ t('messages.blockedMessage') }}</MutedText>
    <LinkButton @click="unfolded = true">{{ t('messages.showBlocked') }}</LinkButton>
  </p>
  <div v-else>
    <MessageText :content="message.content" :reader-uid="readerUid"/>
    <MutedText v-if="message.editedAt" class="text-[10px]">{{ t('messages.edited') }}</MutedText>
  </div>
</template>
