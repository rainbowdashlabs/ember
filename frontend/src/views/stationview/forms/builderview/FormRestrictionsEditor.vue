/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import { type RestrictionSelection } from '@/components/input/restriction'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { MemberGroup, StationMember, UserTag } from '@/api/types'

const { t } = useI18n()

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  members: StationMember[]
}>()

const restriction = defineModel<RestrictionSelection>({ required: true })
</script>

<template>
  <NeutralContainer>
    <div class="space-y-4">
      <SubHeader>{{ t('forms.restrictions.title') }}</SubHeader>
      <RestrictionPicker
        :groups="groups"
        :tags="tags"
        :members="members"
        v-model="restriction"
        show-members
      />
    </div>
  </NeutralContainer>
</template>
