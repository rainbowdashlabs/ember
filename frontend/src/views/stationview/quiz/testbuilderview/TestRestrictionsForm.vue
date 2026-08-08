/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import { toRestrictionSelection } from '@/components/input/restriction'
import type { MemberGroup, UserTag } from '@/api/types'

const props = defineProps<{
  groups: MemberGroup[]
  tags: UserTag[]
}>()

const selectedUserTypes = defineModel<string[]>('selectedUserTypes', { required: true })
const selectedGroupIds = defineModel<number[]>('selectedGroupIds', { required: true })
const selectedTagIds = defineModel<number[]>('selectedTagIds', { required: true })

const restriction = toRestrictionSelection(selectedUserTypes, selectedGroupIds, selectedTagIds)

const { t } = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-3">
    <SubHeader>{{ t('quiz.tests.restrictions') }}</SubHeader>
    <RestrictionsField
        :groups="props.groups"
        :tags="props.tags"
        v-model="restriction"
    />
  </NeutralContainer>
</template>
