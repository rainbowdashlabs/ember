/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {MemberCompletion, MemberGroup, UserTag} from '@/api/generated/schema'
import type {RestrictionSelection} from '@/api/types'

/**
 * Who runs a procedure: groups, member types, tags or members named one by one. They see its public
 * steps, tick the steps marked for them and are told about it. Nobody chosen leaves it to those who edit
 * procedures.
 */
const party = defineModel<RestrictionSelection>({required: true})

defineProps<{
  members: MemberCompletion[]
  groups: MemberGroup[]
  tags: UserTag[]
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="procedure-party">
    <SubHeader>{{ t('procedures.party') }}</SubHeader>
    <MutedText size="sm" tag="p">{{ t('procedures.partyHint') }}</MutedText>
    <RestrictionPicker v-model="party" :groups="groups" :tags="tags" :members="members" show-members/>
  </NeutralContainer>
</template>
