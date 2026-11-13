/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import MemberCardTrigger from '@/components/avatar/MemberCardTrigger.vue'
import BareButton from '@/components/button/BareButton.vue'
import type {MemberIdentity} from '@/api/generated/schema'
import {useMemberCardsShown} from '@/composables/useMemberCards'

/**
 * The picture of a news entry's author, which opens their member card like every other person shown
 * at the station. An author who left the station opens the short card that names them as former;
 * an entry without an author identity is drawn without this, as a plain picture and name.
 */
defineProps<{
  author: MemberIdentity
  name: string
}>()

const cardsShown = useMemberCardsShown()
</script>

<template>
  <MemberCardTrigger v-slot="{toggle, triggerAttrs}" :enabled="cardsShown" :identity="author" :name="name">
    <BareButton v-if="cardsShown" v-bind="triggerAttrs" class="shrink-0 rounded-full" data-testid="news-author-card"
                @click="toggle">
      <UserAvatar :identity="author" :name="name" size="md"/>
    </BareButton>
    <UserAvatar v-else :identity="author" :name="name" size="md"/>
  </MemberCardTrigger>
</template>
