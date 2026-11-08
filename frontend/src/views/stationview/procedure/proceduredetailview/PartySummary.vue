/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import MemberName from '@/components/avatar/MemberName.vue'
import type {MemberGroup, MemberIdentity, RestrictionAudience, UserTag} from '@/api/generated/schema'
import {restrictionLabels, toRestriction} from '@/components/input/restriction'

/**
 * Who runs a procedure, as its page shows it: the member types, groups and tags its party takes in as
 * chips, whether all or any of them have to match, and the members it names one by one.
 */
const props = defineProps<{
  party: RestrictionAudience
  named: MemberIdentity[]
  groups: MemberGroup[]
  tags: UserTag[]
}>()

const {t} = useI18n()

const chips = computed(() => {
  const labels = restrictionLabels(toRestriction(props.party), props.groups, props.tags)
  return [...labels.userTypes, ...labels.groups, ...labels.tags]
})

const nobody = computed(() => chips.value.length === 0 && props.named.length === 0)
</script>

<template>
  <NeutralContainer class="mb-4 space-y-2" data-testid="procedure-party-summary">
    <SubHeader>{{ t('procedures.party') }}</SubHeader>
    <MutedText v-if="nobody" size="sm" tag="p">{{ t('procedures.partyNobody') }}</MutedText>
    <div v-if="chips.length > 0" class="flex flex-wrap items-center gap-1">
      <MutedText v-if="chips.length > 1" size="sm">
        {{ party.mode === 'OR' ? t('procedures.partyAny') : t('procedures.partyAll') }}
      </MutedText>
      <InfoBadge v-for="chip in chips" :key="chip">{{ chip }}</InfoBadge>
    </div>
    <div v-if="named.length > 0" class="flex flex-wrap gap-2">
      <MemberName v-for="member in named" :key="member.memberUid" :identity="member" size="sm"/>
    </div>
  </NeutralContainer>
</template>
