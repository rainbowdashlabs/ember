/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import {ChatAudience, type StationChatResponse} from '@/api/generated/schema'
import {closeStationChat, renameStationChat, reopenStationChat, setStationChatGuardians} from '@/api/messages'
import {formatDate} from '@/util/format'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * One of the station's chats as its managers handle it: whom it is for and how many are in it, its
 * name, whether the guardians are in it, and closing or reopening it. A chat whose group or tag was
 * deleted stays closed, so it offers neither the guardians nor reopening.
 */
const props = defineProps<{
  chat: StationChatResponse
  onChanged: () => Promise<void> | void
}>()

const {t} = useI18n()
const name = ref(props.chat.name)
const failure = ref<Failure | null>(null)

watch(() => props.chat.name, value => { name.value = value })

const orphaned = computed(() => props.chat.audience !== ChatAudience.STATION && props.chat.audienceName === null)

const audience = computed(() => props.chat.audience === ChatAudience.STATION
  ? t('stationChats.audience.STATION')
  : `${t(`stationChats.audience.${props.chat.audience}`)}: ${props.chat.audienceName ?? t('stationChats.audienceGone')}`)

const guardians = computed({
  get: () => props.chat.includeGuardians,
  set: (wanted: boolean) => { void act(() => setStationChatGuardians(props.chat.uid, wanted)) },
})

async function act(change: () => Promise<void>) {
  failure.value = null
  try {
    await change()
    await props.onChanged()
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}
</script>

<template>
  <NeutralContainer class="space-y-3" :data-testid="`station-chat-${chat.uid}`">
    <div class="flex flex-wrap items-center gap-2">
      <span class="font-semibold">{{ chat.name }}</span>
      <SecondaryBadge v-if="chat.closedAt">{{ t('stationChats.closedSince', {date: formatDate(chat.closedAt)}) }}</SecondaryBadge>
    </div>
    <MutedText tag="p">{{ audience }} · {{ t('messages.memberCount', {count: chat.members}) }}</MutedText>
    <div class="space-y-1">
      <FieldLabel :for="`station-chat-rename-${chat.uid}`">{{ t('stationChats.rename') }}</FieldLabel>
      <div class="flex gap-2">
        <TextInput :id="`station-chat-rename-${chat.uid}`" v-model="name" maxlength="100" class="min-w-0 flex-1"/>
        <SaveButton :action="() => act(() => renameStationChat(chat.uid, name.trim()))"/>
      </div>
    </div>
    <ToggleSetting v-if="chat.audience !== ChatAudience.STATION && !orphaned" v-model="guardians"
                   :label="t('stationChats.guardians')" :hint="t('stationChats.guardiansHint')"/>
    <FailureAlert :failure="failure"/>
    <ButtonRow v-if="!(chat.closedAt && orphaned)" align="end">
      <SecondaryButton v-if="chat.closedAt" :icon="['fas', 'lock-open']" :data-testid="`station-chat-reopen-${chat.uid}`"
                       @click="act(() => reopenStationChat(chat.uid))">
        {{ t('stationChats.reopen') }}
      </SecondaryButton>
      <ErrorButton v-else :icon="['fas', 'lock']" :data-testid="`station-chat-close-${chat.uid}`"
                   @click="act(() => closeStationChat(chat.uid))">
        {{ t('stationChats.close') }}
      </ErrorButton>
    </ButtonRow>
  </NeutralContainer>
</template>
