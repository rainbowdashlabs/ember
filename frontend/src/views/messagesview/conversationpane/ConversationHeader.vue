/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import IconButton from '@/components/button/IconButton.vue'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {ConversationResponse, MessagePersonResponse} from '@/api/generated/schema'
import ConversationAvatar from '../conversationlist/ConversationAvatar.vue'

/**
 * The top of an open conversation: the way back to the list on a phone, who it is with, the
 * member list of a group, and the conversation's menu.
 */
defineProps<{
  conversation: ConversationResponse
  title: string
  others: readonly MessagePersonResponse[]
  /** The other person of a direct conversation, whom its menu can block. */
  other: MessagePersonResponse | null
  otherBlocked: boolean
}>()

defineEmits<{
  members: []
  mute: [muted: boolean]
  leave: []
  block: [person: MessagePersonResponse, blocked: boolean]
}>()

const {t} = useI18n()
</script>

<template>
  <header class="flex items-center gap-3 border-b border-(--border) px-3 py-2">
    <div class="lg:hidden">
      <IconButton :icon="['fas', 'arrow-left']" :label="t('messages.back')" to="/messages"
                  class="text-(--text) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"/>
    </div>
    <ConversationAvatar :people="others"/>
    <div class="min-w-0 flex-1">
      <p class="truncate font-semibold" data-testid="conversation-title">{{ title }}</p>
      <MutedText v-if="conversation.kind === 'GROUP'">
        {{ t('messages.memberCount', {count: conversation.members.length}) }}
      </MutedText>
    </div>
    <IconButton v-if="conversation.kind === 'GROUP'" :icon="['fas', 'users']" :label="t('messages.members.open')"
                data-testid="conversation-members"
                class="text-(--text-muted) hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
                @click="$emit('members')"/>
    <ActionsMenu :label="t('messages.menu')" test-id="conversation-menu">
      <DropdownMenuItem :icon="['fas', conversation.muted ? 'bell' : 'bell-slash']"
                        @click="$emit('mute', !conversation.muted)">
        {{ conversation.muted ? t('messages.unmute') : t('messages.mute') }}
      </DropdownMenuItem>
      <DropdownMenuItem v-if="other" :icon="['fas', 'ban']" :destructive="!otherBlocked"
                        @click="$emit('block', other, !otherBlocked)">
        {{ otherBlocked ? t('messages.unblock') : t('messages.block') }}
      </DropdownMenuItem>
      <DropdownMenuItem :icon="['fas', 'right-from-bracket']" destructive @click="$emit('leave')">
        {{ conversation.kind === 'GROUP' ? t('messages.leaveGroup') : t('messages.closeDirect') }}
      </DropdownMenuItem>
    </ActionsMenu>
  </header>
</template>
