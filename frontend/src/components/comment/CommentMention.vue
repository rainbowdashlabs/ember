/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import MemberCardTrigger from '@/components/avatar/MemberCardTrigger.vue'
import BareButton from '@/components/button/BareButton.vue'
import {useMemberCardsShown} from '@/composables/useMemberCards'
import type {PersonIdentity} from '@/util/personIdentity'

/**
 * A member named in a comment as `@Name`, which opens their card the way their name does elsewhere.
 *
 * <p>A mention stands in running text and inside nothing that is pressed itself, so the whole
 * `@Name` is the button. A mention of somebody no longer known, or of many at once, stays plain text.
 */
const props = defineProps<{
  name: string
  identity?: PersonIdentity | null
  bulk?: boolean
}>()

const cardsShown = useMemberCardsShown()
</script>

<template>
  <MemberCardTrigger
      v-if="props.identity?.memberUid && cardsShown && !bulk"
      v-slot="{toggle, triggerAttrs}"
      :identity="props.identity"
      :name="name"
  >
    <BareButton v-bind="triggerAttrs" class="font-semibold text-primary hover:underline" data-testid="mention" @click="toggle">@{{ name }}</BareButton>
  </MemberCardTrigger>
  <span v-else class="font-semibold" :class="bulk ? 'text-secondary' : 'text-primary'" data-testid="mention">@{{ name }}</span>
</template>
