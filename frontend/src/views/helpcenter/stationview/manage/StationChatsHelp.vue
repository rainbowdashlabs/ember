/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import HelpArticle from '@/components/helpcenter/HelpArticle.vue'
import HelpSection from '@/components/helpcenter/HelpSection.vue'
import HelpTip from '@/components/helpcenter/HelpTip.vue'
import BulletList from '@/components/typography/BulletList.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import {TagVisibility, type MemberGroup, type RestrictionAudience, type StationChatResponse, type UserTag} from '@/api/generated/schema'
import StationChatCard from '@/views/stationview/messages/stationchatsview/StationChatCard.vue'

const {t} = useI18n()

const STATION_ID = '00000000-0000-0000-0000-0000000000f1'

const groups = computed<MemberGroup[]>(() => [
  {id: 1, name: t('helpCenter.stationChats.example.youth'), color: null, groupSetId: null, position: 0, stationId: STATION_ID, userTypes: []},
])

const tags = computed<UserTag[]>(() => [
  {id: 2, name: t('helpCenter.stationChats.example.youthLeaders'), color: null, position: 0, stationId: STATION_ID, visibility: TagVisibility.BADGE},
])

const youthWithLeaders: RestrictionAudience = {userTypes: [], groupIds: [1], tagIds: [2], memberIds: [], mode: 'OR'}
const everybody: RestrictionAudience = {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: 'AND'}
const nameless: RestrictionAudience = {userTypes: [], groupIds: [3], tagIds: [], memberIds: [], mode: 'AND'}

const chats = computed<StationChatResponse[]>(() => [
  {
    uid: '00000000-0000-0000-0000-0000000000e1', name: t('helpCenter.stationChats.example.youth'), audience: youthWithLeaders,
    audienceGone: false, includeGuardians: true, members: 18,
    createdAt: new Date().toISOString(), lastActivityAt: new Date().toISOString(), closedAt: null,
  },
  {
    uid: '00000000-0000-0000-0000-0000000000e2', name: t('helpCenter.stationChats.example.exercise'), audience: everybody,
    audienceGone: false, includeGuardians: false, members: 42,
    createdAt: new Date().toISOString(), lastActivityAt: new Date().toISOString(), closedAt: new Date().toISOString(),
  },
  {
    uid: '00000000-0000-0000-0000-0000000000e3', name: t('helpCenter.stationChats.example.oldGroup'), audience: nameless,
    audienceGone: true, includeGuardians: false, members: 0,
    createdAt: new Date().toISOString(), lastActivityAt: new Date().toISOString(), closedAt: new Date().toISOString(),
  },
])

function nothing(): void {
  return undefined
}
</script>

<template>
  <HelpArticle :title="t('helpCenter.stationChats.title')" :subtitle="t('helpCenter.stationChats.subtitle')">
    <HelpSection :title="t('helpCenter.stationChats.whatIs')">
      <p>{{ t('helpCenter.stationChats.whatIsText') }}</p>
      <p>{{ t('helpCenter.stationChats.membersText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationChats.howTo')">
      <BulletList>
        <li>{{ t('helpCenter.stationChats.step1') }}</li>
        <li>{{ t('helpCenter.stationChats.step2') }}</li>
        <li>{{ t('helpCenter.stationChats.step3') }}</li>
      </BulletList>
      <PrimaryButton :icon="['fas', 'plus']">{{ t('stationChats.open.title') }}</PrimaryButton>
      <NeutralContainer inert class="space-y-3">
        <SubHeader>{{ t('stationChats.open.title') }}</SubHeader>
        <FieldLabel>{{ t('stationChats.open.name') }}</FieldLabel>
        <TextInput :model-value="t('helpCenter.stationChats.example.youth')"/>
        <FieldLabel>{{ t('stationChats.open.audience') }}</FieldLabel>
        <RestrictionsField :model-value="youthWithLeaders" :groups="groups" :tags="tags"/>
        <FieldHint>{{ t('stationChats.open.audienceHint') }}</FieldHint>
        <ToggleSetting :model-value="true" :label="t('stationChats.guardians')" :hint="t('stationChats.guardiansHint')"/>
        <ButtonRow pair align="end">
          <SecondaryButton>{{ t('messages.composer.cancel') }}</SecondaryButton>
          <PrimaryButton :icon="['fas', 'plus']">{{ t('stationChats.open.submit') }}</PrimaryButton>
        </ButtonRow>
      </NeutralContainer>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationChats.listTitle')">
      <p>{{ t('helpCenter.stationChats.listText') }}</p>
      <div inert class="space-y-3">
        <StationChatCard v-for="chat in chats" :key="chat.uid" :chat="chat" :groups="groups" :tags="tags" :on-changed="nothing"/>
      </div>
      <EmptyState :message="t('stationChats.empty')"/>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationChats.guardiansTitle')">
      <p>{{ t('helpCenter.stationChats.guardiansText') }}</p>
    </HelpSection>

    <HelpSection :title="t('helpCenter.stationChats.closeTitle')">
      <p>{{ t('helpCenter.stationChats.closeText') }}</p>
      <p>{{ t('helpCenter.stationChats.moderateText') }}</p>
    </HelpSection>

    <HelpTip>{{ t('helpCenter.stationChats.tip') }}</HelpTip>
  </HelpArticle>
</template>
