/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import UserAvatar from './UserAvatar.vue'
import MemberName from './MemberName.vue'
import MemberCardSection from './MemberCardSection.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import UserTagBadge from '@/components/badge/UserTagBadge.vue'
import ColorBadge from '@/components/badge/ColorBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import type {MemberCard} from '@/api/generated/schema'
import type {PersonIdentity} from '@/util/personIdentity'

/**
 * What a short look at a member's name shows: picture and name, then who looks after them, whom
 * they look after, their tags and their groups. A part with nothing in it is left out.
 *
 * <p>A member of another station shows only picture, name and station, since their own station
 * keeps the rest. The guardians and members listed here carry no card of their own, so a card never
 * opens inside a card.
 */
defineProps<{
  identity: PersonIdentity
  name: string
  card: MemberCard | null
  state: 'loading' | 'failed' | 'ready' | 'external'
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex w-72 max-w-[calc(100vw-2rem)] flex-col gap-3 p-3" data-testid="member-card">
    <div class="flex items-center gap-3">
      <UserAvatar :identity="identity" :name="name" size="lg"/>
      <div class="flex min-w-0 flex-col items-start gap-1">
        <span class="font-semibold break-words" :style="identity.nameColor ? {color: identity.nameColor} : {}">{{ card?.name ?? name }}</span>
        <MutedText v-if="card?.former">{{ t('memberCard.former') }}</MutedText>
        <StationBadge v-if="state === 'external'" :station-name="identity.stationName ?? ''"/>
      </div>
    </div>

    <Spinner v-if="state === 'loading'" size="sm"/>
    <MutedText v-else-if="state === 'failed'" tag="p">{{ t('memberCard.loadFailed') }}</MutedText>

    <template v-if="state === 'ready' && card">
      <MemberCardSection v-if="card.parents.length" :title="t('memberCard.guardians')">
        <MemberName v-for="parent in card.parents" :key="parent.memberUid" :identity="parent" :card="false"/>
      </MemberCardSection>
      <MemberCardSection v-if="card.children.length" :title="t('memberCard.managedMembers')">
        <MemberName v-for="child in card.children" :key="child.memberUid" :identity="child" :card="false"/>
      </MemberCardSection>
      <MemberCardSection v-if="card.tags.length" :title="t('memberCard.tags')">
        <UserTagBadge v-for="tag in card.tags" :key="tag.name" :color="tag.color">{{ tag.name }}</UserTagBadge>
      </MemberCardSection>
      <MemberCardSection v-if="card.groups.length" :title="t('memberCard.groups')">
        <ColorBadge v-for="group in card.groups" :key="group.name" :color="group.color">{{ group.name }}</ColorBadge>
      </MemberCardSection>
    </template>
  </div>
</template>
