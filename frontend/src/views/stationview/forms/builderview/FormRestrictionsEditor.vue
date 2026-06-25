/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { MemberGroup, StationMember, UserTag } from '@/api/types'

const { t } = useI18n()

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  members: StationMember[]
}>()

const selectedUserTypes = defineModel<string[]>('selectedUserTypes', { required: true })
const selectedGroupIds = defineModel<number[]>('selectedGroupIds', { required: true })
const selectedTagIds = defineModel<number[]>('selectedTagIds', { required: true })
const selectedMemberIds = defineModel<number[]>('selectedMemberIds', { required: true })
</script>

<template>
  <NeutralContainer>
    <div class="space-y-4">
      <SubHeader>{{ t('forms.restrictions.title') }}</SubHeader>
      <RestrictionPicker
        :groups="groups"
        :tags="tags"
        :members="members"
        v-model:selected-user-types="selectedUserTypes"
        v-model:selected-group-ids="selectedGroupIds"
        v-model:selected-tag-ids="selectedTagIds"
        v-model:selected-member-ids="selectedMemberIds"
        show-members
      />
    </div>
  </NeutralContainer>
</template>
