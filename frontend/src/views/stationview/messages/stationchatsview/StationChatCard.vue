/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import type {MemberGroup, StationChatResponse, UserTag} from '@/api/generated/schema'
import {closeStationChat, reopenStationChat} from '@/api/messages'
import {audienceWords} from '@/components/input/restriction'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {formatDate} from '@/util/format'

/**
 * One of the station's chats as its managers handle it: whom it is for, named the way a restricted
 * appointment names its audience, how many are in it, and changing, closing or reopening it. A chat
 * that lost part of its audience, a deleted group or tag, or every member it named,
 * shows it, and stays closed until it is given a new audience.
 */
const props = defineProps<{
  chat: StationChatResponse
  groups: MemberGroup[]
  tags: UserTag[]
  onChanged: () => Promise<void> | void
}>()

const emit = defineEmits<{
  edit: [chat: StationChatResponse]
}>()

const {t} = useI18n()

const audience = computed(() => t('stationChats.forAudience', {
  audience: audienceWords(props.chat.audience, props.groups, props.tags, t) || t('stationChats.wholeStation'),
}))

const {failure, run: act} = useAsyncAction(async (change: () => Promise<void>) => {
  await change()
  await props.onChanged()
})
</script>

<template>
  <NeutralContainer class="space-y-3" :data-testid="`station-chat-${chat.uid}`">
    <div class="flex flex-wrap items-center gap-2">
      <span class="font-semibold">{{ chat.name }}</span>
      <SecondaryBadge v-if="chat.closedAt">{{ t('stationChats.closedSince', {date: formatDate(chat.closedAt)}) }}</SecondaryBadge>
    </div>
    <MutedText tag="p" :data-testid="`station-chat-audience-${chat.uid}`">
      {{ audience }} · {{ t('messages.memberCount', {count: chat.members}) }}
    </MutedText>
    <Alert v-if="chat.audienceGone" variant="info" :data-testid="`station-chat-audience-gone-${chat.uid}`">
      {{ t('stationChats.audienceGoneHint') }}
    </Alert>
    <FailureAlert :failure="failure"/>
    <ButtonRow align="end">
      <SecondaryButton :icon="['fas', 'pen']" :data-testid="`station-chat-edit-${chat.uid}`" @click="emit('edit', chat)">
        {{ t('stationChats.edit') }}
      </SecondaryButton>
      <SecondaryButton v-if="chat.closedAt && !chat.audienceGone" :icon="['fas', 'lock-open']"
                       :data-testid="`station-chat-reopen-${chat.uid}`" @click="act(() => reopenStationChat(chat.uid))">
        {{ t('stationChats.reopen') }}
      </SecondaryButton>
      <ErrorButton v-else-if="!chat.closedAt" :icon="['fas', 'lock']" :data-testid="`station-chat-close-${chat.uid}`"
                   @click="act(() => closeStationChat(chat.uid))">
        {{ t('stationChats.close') }}
      </ErrorButton>
    </ButtonRow>
  </NeutralContainer>
</template>
