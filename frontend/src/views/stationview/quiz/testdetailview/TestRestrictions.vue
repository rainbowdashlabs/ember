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
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import type {Role, MemberGroup, UserTag} from '@/api/types'

defineProps<{
  allRoles: Role[]
  allGroups: MemberGroup[]
  allTags: UserTag[]
  selectedRoleIds: Set<number>
  selectedGroupIds: Set<number>
  selectedTagIds: Set<number>
  restrictionsDirty: boolean
}>()

const emit = defineEmits<{
  toggleRole: [roleId: number]
  toggleGroup: [groupId: number]
  toggleTag: [tagId: number]
  save: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-3">
    <SectionHeader>{{ t('quiz.tests.restrictions') }}</SectionHeader>
    <NeutralContainer>
      <div class="space-y-3">
        <div class="space-y-2">
          <label class="text-xs text-(--text-muted)">{{ t('quiz.tests.restrictionRoles') }}</label>
          <div class="flex flex-wrap gap-2">
            <SelectionToggleButton
              v-for="role in allRoles"
              :key="role.id"
              :selected="selectedRoleIds.has(role.id)"
              @toggle="emit('toggleRole', role.id)"
            >
              {{ role.role }}
            </SelectionToggleButton>
          </div>
        </div>
        <div class="space-y-2">
          <label class="text-xs text-(--text-muted)">{{ t('quiz.tests.restrictionGroups') }}</label>
          <div class="flex flex-wrap gap-2">
            <SelectionToggleButton
              v-for="group in allGroups"
              :key="group.id"
              :selected="selectedGroupIds.has(group.id)"
              @toggle="emit('toggleGroup', group.id)"
            >
              {{ group.name }}
            </SelectionToggleButton>
            <span v-if="allGroups.length === 0" class="text-xs text-(--text-muted)">&ndash;</span>
          </div>
        </div>
        <div class="space-y-2">
          <label class="text-xs text-(--text-muted)">{{ t('quiz.tests.restrictionTags') }}</label>
          <div class="flex flex-wrap gap-2">
            <SelectionToggleButton
              v-for="tag in allTags"
              :key="tag.id"
              :selected="selectedTagIds.has(tag.id)"
              @toggle="emit('toggleTag', tag.id)"
            >
              {{ tag.name }}
            </SelectionToggleButton>
            <span v-if="allTags.length === 0" class="text-xs text-(--text-muted)">&ndash;</span>
          </div>
        </div>
        <p v-if="selectedRoleIds.size === 0 && selectedGroupIds.size === 0 && selectedTagIds.size === 0"
           class="text-xs text-(--text-muted) italic">{{ t('quiz.tests.noRestrictions') }}</p>
        <div v-if="restrictionsDirty" class="flex justify-end">
          <PrimaryButton @click="emit('save')">{{ t('common.save') }}</PrimaryButton>
        </div>
      </div>
    </NeutralContainer>
  </div>
</template>
