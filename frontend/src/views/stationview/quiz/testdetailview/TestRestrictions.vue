/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import type {MemberGroup, UserTag} from '@/api/types'

defineProps<{
  allGroups: MemberGroup[]
  allTags: UserTag[]
  restrictionsDirty: boolean
}>()

const selectedUserTypes = defineModel<string[]>('selectedUserTypes', { required: true })
const selectedGroupIds = defineModel<number[]>('selectedGroupIds', { required: true })
const selectedTagIds = defineModel<number[]>('selectedTagIds', { required: true })

const emit = defineEmits<{
  save: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <SubHeader>{{ t('quiz.tests.restrictions') }}</SubHeader>
    <NeutralContainer>
      <RestrictionsField
          :groups="allGroups"
          :tags="allTags"
          v-model:selected-user-types="selectedUserTypes"
          v-model:selected-group-ids="selectedGroupIds"
          v-model:selected-tag-ids="selectedTagIds"
      />
      <div v-if="restrictionsDirty" class="flex justify-end mt-3">
        <PrimaryButton @click="emit('save')">{{ t('common.save') }}</PrimaryButton>
      </div>
    </NeutralContainer>
  </div>
</template>
