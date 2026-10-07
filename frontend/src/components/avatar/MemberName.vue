/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import UserAvatar from './UserAvatar.vue'
import MemberCardTrigger from './MemberCardTrigger.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import DisplayTagBadge from '@/components/badge/DisplayTagBadge.vue'
import BareButton from '@/components/button/BareButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {PersonIdentity} from '@/util/personIdentity'
import {useExternalMember} from '@/composables/useExternalMember'
import {useMemberCardsShown} from '@/composables/useMemberCards'
import {computed} from 'vue'

/**
 * A member's avatar and name, with an optional second line (such as the address) that stands under
 * the name rather than under the avatar, so it lines up whatever size the picture has. The name keeps
 * to one line, cut short with an ellipsis only where the space runs out.
 *
 * <p>The avatar opens the member's card: on a tap, and with a mouse also when the pointer rests on
 * the avatar or the name. `card` turns that off where the name sits inside something that is itself
 * pressed, such as a picker option or a link, and a screen above can turn it off for everything
 * below it. A name without a member behind it, such as an account, never opens a card.
 */
const props = withDefaults(defineProps<{
  identity: PersonIdentity | null | undefined
  size?: 'sm' | 'md' | 'lg'
  detail?: string | null
  card?: boolean
}>(), {
  size: 'sm',
  detail: null,
  card: true,
})

const cardsShown = useMemberCardsShown()
const isExternal = useExternalMember(() => props.identity)
/**
 * The name to show, falling back to the station's name for a member of another station whose own
 * name could not be resolved, so the author never reads as an empty label beside a "?" avatar.
 */
const displayName = computed(() => {
  if (props.identity?.name) return props.identity.name
  if (isExternal.value && props.identity?.stationName) return props.identity.stationName
  return ''
})
const opensCard = computed(() =>
  props.card && cardsShown && !!props.identity?.memberUid && !!props.identity.stationUid)
</script>

<template>
  <MemberCardTrigger
      v-slot="{toggle, triggerAttrs}"
      :enabled="opensCard"
      :identity="identity ?? {}"
      :name="displayName"
      class="inline-flex min-w-0 max-w-full items-center gap-1.5"
  >
    <BareButton v-if="opensCard" v-bind="triggerAttrs" class="shrink-0 rounded-full" data-testid="member-card-trigger" @click="toggle">
      <UserAvatar :identity="identity" :name="displayName" :size="size"/>
    </BareButton>
    <UserAvatar v-else :identity="identity" :name="displayName" :size="size"/>
    <span class="inline-flex min-w-0 flex-col">
      <span class="inline-flex min-w-0 items-center gap-1.5">
        <span data-testid="member-name" class="truncate" :style="identity?.nameColor ? { color: identity.nameColor } : {}"><slot>{{ displayName }}</slot></span>
        <DisplayTagBadge v-if="identity?.displayTag" :tag="identity.displayTag"/>
        <StationBadge v-if="isExternal && identity?.name" :station-name="identity?.stationName ?? ''" />
      </span>
      <MutedText v-if="detail" data-testid="member-detail" class="truncate font-normal">{{ detail }}</MutedText>
    </span>
  </MemberCardTrigger>
</template>
