/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import BareButton from '@/components/button/BareButton.vue'
import AppIcon from '@/components/display/AppIcon.vue'
import type {ReplyPreviewResponse} from '@/api/generated/schema'
import {useMessageWords} from '@/composables/useMessageWords'

/** The small quote of the message another one answers. Clicking it scrolls to that message. */
defineProps<{
  reply: ReplyPreviewResponse
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
    <span class="font-semibold">{{ nameOf(reply.author) }}</span>
    <span class="truncate">{{ reply.deleted ? t('messages.deleted') : reply.text }}</span>
  </BareButton>
</template>
