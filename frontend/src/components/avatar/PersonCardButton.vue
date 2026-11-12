/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import MemberCardTrigger from './MemberCardTrigger.vue'
import BareButton from '@/components/button/BareButton.vue'
import {useMemberCardsShown} from '@/composables/useMemberCards'

/**
 * Turns a picture or name of a person met as an account, in messages, into the button that opens
 * their cards at the stations the reader shares with them. Without an account, or where a screen
 * above turned cards off, the content shows as it is.
 */
const props = defineProps<{
  accountUid: string | null | undefined
  name: string
}>()

const cardsShown = useMemberCardsShown()
const opens = computed(() => !!props.accountUid && cardsShown)
</script>

<template>
  <MemberCardTrigger v-slot="{toggle, triggerAttrs}" :enabled="opens" :identity="{accountUid: accountUid ?? undefined}" :name="name">
    <BareButton v-if="opens" v-bind="triggerAttrs" class="rounded-theme text-left" data-testid="person-card-trigger" @click="toggle">
      <slot/>
    </BareButton>
    <slot v-else/>
  </MemberCardTrigger>
</template>
