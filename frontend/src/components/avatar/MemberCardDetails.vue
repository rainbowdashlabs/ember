/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import MemberName from './MemberName.vue'
import MemberCardSection from './MemberCardSection.vue'
import UserTagBadge from '@/components/badge/UserTagBadge.vue'
import ColorBadge from '@/components/badge/ColorBadge.vue'
import type {MemberCard} from '@/api/generated/schema'

/**
 * The parts of a member card below the name: who looks after the member, whom they look after,
 * their tags and their groups. A part with nothing in it is left out. The people listed carry no
 * card of their own, so a card never opens inside a card.
 */
defineProps<{
  card: MemberCard
}>()

const {t} = useI18n()
</script>

<template>
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
