/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import type { RestrictionSelection } from '@/api/types'
import SubHeader from '@/components/typography/SubHeader.vue'
import type { MemberGroup, UserTag } from '@/api/generated/schema'
import type { MemberLike } from '@/components/input/select/memberOption'

const { t } = useI18n()

defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
  members: MemberLike[]
}>()

const restriction = defineModel<RestrictionSelection>({ required: true })
</script>

<template>
  <NeutralContainer>
    <div class="space-y-4">
      <SubHeader>{{ t('forms.restrictions.title') }}</SubHeader>
      <RestrictionPicker
        v-model="restriction"
        :groups="groups"
        :tags="tags"
        :members="members"
        show-members
      />
    </div>
  </NeutralContainer>
</template>
