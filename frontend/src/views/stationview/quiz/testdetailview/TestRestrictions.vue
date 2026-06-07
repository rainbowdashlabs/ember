/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import type {MemberGroup, UserTag} from '@/api/types'

defineProps<{
  allGroups: MemberGroup[]
  allTags: UserTag[]
  selectedUserTypes: string[]
  selectedGroupIds: number[]
  selectedTagIds: number[]
  restrictionsDirty: boolean
}>()

const emit = defineEmits<{
  'update:selectedUserTypes': [types: string[]]
  'update:selectedGroupIds': [ids: number[]]
  'update:selectedTagIds': [ids: number[]]
  save: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <SectionHeader>{{ t('quiz.tests.restrictions') }}</SectionHeader>
    <NeutralContainer>
      <RestrictionPicker
          :groups="allGroups"
          :tags="allTags"
          :selected-user-types="selectedUserTypes"
          :selected-group-ids="selectedGroupIds"
          :selected-tag-ids="selectedTagIds"
          @update:selected-user-types="types => emit('update:selectedUserTypes', types)"
          @update:selected-group-ids="ids => emit('update:selectedGroupIds', ids)"
          @update:selected-tag-ids="ids => emit('update:selectedTagIds', ids)"
      />
      <div v-if="restrictionsDirty" class="flex justify-end mt-3">
        <PrimaryButton @click="emit('save')">{{ t('common.save') }}</PrimaryButton>
      </div>
    </NeutralContainer>
  </div>
</template>
