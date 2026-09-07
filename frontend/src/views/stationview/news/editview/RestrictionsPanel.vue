/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import {toRestrictionSelection} from '@/components/input/restriction'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'

const selectedUserTypes = defineModel<string[]>('selectedUserTypes', {required: true})
const selectedGroupIds = defineModel<number[]>('selectedGroupIds', {required: true})
const selectedTagIds = defineModel<number[]>('selectedTagIds', {required: true})
/**
 * The individually named people. An entry written from a restricted appointment starts with the
 * appointment's audience, and an appointment may name people one by one, so this editor has to be
 * able to show and undo that rather than silently dropping it on the next save.
 */
const selectedMemberIds = defineModel<number[]>('selectedMemberIds', {required: true})

const props = defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  members: StationMember[]
}>()

const restriction = toRestrictionSelection(selectedUserTypes, selectedGroupIds, selectedTagIds, selectedMemberIds)

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('news.restrictToGroups') }}</SubHeader>
    <p class="text-xs text-(--text-muted)">{{ t('news.restrictHint') }}</p>
    <RestrictionPicker
        :groups="props.groups"
        :tags="props.tags"
        :members="props.members"
        :show-members="true"
        :show-mode="false"
        v-model="restriction"
    />
  </NeutralContainer>
</template>
