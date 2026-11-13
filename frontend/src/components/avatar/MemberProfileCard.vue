/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import UserAvatar from './UserAvatar.vue'
import MemberCardDetails from './MemberCardDetails.vue'
import MemberMessageButton from './MemberMessageButton.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import type {MemberCard, MessagePersonResponse, SharedMemberCard} from '@/api/generated/schema'
import type {PersonIdentity} from '@/util/personIdentity'

/**
 * What a short look at a member's name shows: picture and name, then who looks after them, whom
 * they look after, their tags and their groups. A part with nothing in it is left out.
 *
 * <p>A member of another station shows only picture, name and station, since their own station
 * keeps the rest. A person met as an account, in messages, shows the parts once per station the
 * reader shares with them, under that station's name. A member of the reader's own station whom the
 * reader may write to gets a button that opens the private conversation with them.
 */
withDefaults(defineProps<{
  identity: PersonIdentity
  name: string
  card: MemberCard | null
  state: 'loading' | 'failed' | 'ready' | 'external'
  shared?: readonly SharedMemberCard[] | null
  /** The person behind the member, where the reader may write to them. */
  writable?: MessagePersonResponse | null
}>(), {
  shared: null,
  writable: null,
})

const {t} = useI18n()
</script>

<template>
  <div class="flex w-72 max-w-[calc(100vw-2rem)] flex-col gap-3 p-3" data-testid="member-card">
    <div class="flex items-center gap-3">
      <UserAvatar :identity="identity" :name="name" size="lg"/>
      <div class="flex min-w-0 flex-col items-start gap-1">
        <span class="font-semibold break-words" :style="identity.nameColor ? {color: identity.nameColor} : {}">{{ card?.name ?? shared?.[0]?.card.name ?? name }}</span>
        <MutedText v-if="card?.former">{{ t('memberCard.former') }}</MutedText>
        <StationBadge v-if="state === 'external'" :station-name="identity.stationName ?? ''"/>
      </div>
    </div>

    <Spinner v-if="state === 'loading'" size="sm"/>
    <MutedText v-else-if="state === 'failed'" tag="p">{{ t('memberCard.loadFailed') }}</MutedText>

    <MemberCardDetails v-if="state === 'ready' && card" :card="card"/>
    <MemberMessageButton v-if="state === 'ready' && card && writable" :person="writable"/>
    <template v-if="state === 'ready' && shared">
      <div v-for="station in shared" :key="station.stationUid" class="flex flex-col gap-3" data-testid="shared-member-card">
        <SubHeader>{{ station.stationName }}</SubHeader>
        <MemberCardDetails :card="station.card"/>
      </div>
    </template>
  </div>
</template>
