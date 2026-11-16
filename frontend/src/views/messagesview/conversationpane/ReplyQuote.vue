/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import BareButton from '@/components/button/BareButton.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import type {MemberIdentity, ReplyPreviewResponse} from '@/api/generated/schema'
import ColoredName from '@/components/avatar/ColoredName.vue'
import {useMessageWords} from '@/composables/useMessageWords'

/** The small quote of the message another one answers. Clicking it scrolls to that message. */
defineProps<{
  reply: ReplyPreviewResponse
  /** How the station shows the quoted author in a station chat, for their group color and tag. */
  member?: MemberIdentity | null
}>()

defineEmits<{
  jump: [messageId: number]
}>()

const {t} = useI18n()
const {nameOf} = useMessageWords()
</script>

<template>
  <BareButton class="mb-0.5 flex max-w-full items-center gap-1 truncate text-left text-xs text-(--text-muted) hover:text-(--text)"
              :title="t('messages.jumpToReply')" @click="$emit('jump', reply.id)">
    <AppIcon :icon="['fas', 'reply']" class="h-3 w-3 shrink-0 -scale-x-100"/>
    <ColoredName class="shrink-0 font-semibold" testid="chat-person-name"
                 :name-color="member?.nameColor" :display-tag="member?.displayTag">{{ nameOf(reply.author) }}</ColoredName>
    <span class="truncate">{{ reply.removedByStation ? t('messages.removedByStation') : reply.deleted ? t('messages.deleted') : reply.text }}</span>
  </BareButton>
</template>
