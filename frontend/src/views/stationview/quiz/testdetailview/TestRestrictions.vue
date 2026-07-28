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
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import RestrictionsField from '@/components/input/RestrictionsField.vue'
import type {RestrictionSelection} from '@/components/input/restriction'
import type {MemberGroup, UserTag} from '@/api/types'

defineProps<{
  allGroups: MemberGroup[]
  allTags: UserTag[]
  restrictionsDirty: boolean
}>()

const selectedUserTypes = defineModel<string[]>('selectedUserTypes', { required: true })
const selectedGroupIds = defineModel<number[]>('selectedGroupIds', { required: true })
const selectedTagIds = defineModel<number[]>('selectedTagIds', { required: true })

const restriction = computed<RestrictionSelection>({
  get: (): RestrictionSelection => ({
    userTypes: selectedUserTypes.value,
    groupIds: selectedGroupIds.value,
    tagIds: selectedTagIds.value,
    memberIds: [],
    mode: 'AND',
  }),
  set: (value: RestrictionSelection) => {
    selectedUserTypes.value = value.userTypes
    selectedGroupIds.value = value.groupIds
    selectedTagIds.value = value.tagIds
  },
})

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
          v-model="restriction"
      />
      <div v-if="restrictionsDirty" class="flex justify-end mt-3">
        <PrimaryButton @click="emit('save')">{{ t('common.save') }}</PrimaryButton>
      </div>
    </NeutralContainer>
  </div>
</template>
