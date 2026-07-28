/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import type { QuizTest, QuizQuestion } from '@/api/quiz'
import type { MemberGroup, UserTag, StationMember } from '@/api/types'
import type { FrozenQuestionDetail } from '@/api/quiz'
import TestInfoCard from './TestInfoCard.vue'
import TestSectionsList from './TestSectionsList.vue'
import FrozenQuestionsList from './FrozenQuestionsList.vue'
import PickQuestionModal from './PickQuestionModal.vue'
import TestRestrictions from './TestRestrictions.vue'
import TestAccessGrant from './TestAccessGrant.vue'

defineProps<{
  test: QuizTest
  sections: unknown[]
  catalogName: (catalogId: number) => string
  canConfigure: boolean
  canReadResults: boolean
  editStartAt: string
  editEndAt: string
  timesDirty: boolean
  saveTimes: () => Promise<void>
  frozenQuestions: FrozenQuestionDetail[]
  frozenLoading: boolean
  questionTypeName: (q: QuizQuestion) => string
  showPickModal: boolean
  pickSearch: string
  filteredAvailableQuestions: QuizQuestion[]
  allGroups: MemberGroup[]
  allTags: UserTag[]
  selectedUserTypes: string[]
  selectedGroupIds: number[]
  selectedTagIds: number[]
  restrictionsDirty: boolean
  members: StationMember[]
}>()

defineEmits<{
  (e: 'update:editStartAt', value: string): void
  (e: 'update:editEndAt', value: string): void
  (e: 'update:showPickModal', value: boolean): void
  (e: 'update:pickSearch', value: string): void
  (e: 'markDirty'): void
  (e: 'generate'): void
  (e: 'randomReplace', position: number): void
  (e: 'pickReplace', position: number): void
  (e: 'pick', questionId: number): void
  (e: 'updateUserTypes', types: string[]): void
  (e: 'updateGroupIds', ids: number[]): void
  (e: 'updateTagIds', ids: number[]): void
  (e: 'saveRestrictions'): void
  (e: 'grant', memberId: number, closesAt: string | null): void
}>()
</script>

<template>
  <TestInfoCard
      :test="test"
      :can-configure="canConfigure"
      :edit-start-at="editStartAt"
      :edit-end-at="editEndAt"
      :times-dirty="timesDirty"
      :save-times="saveTimes"
      @update:edit-start-at="(v: string) => $emit('update:editStartAt', v)"
      @update:edit-end-at="(v: string) => $emit('update:editEndAt', v)"
      @mark-dirty="$emit('markDirty')"
  />
  <TestSectionsList :sections="(sections as any)" :catalog-name="catalogName" />
  <FrozenQuestionsList
      v-if="canConfigure"
      :test="test"
      :frozen-questions="frozenQuestions"
      :frozen-loading="frozenLoading"
      :question-type-name="questionTypeName"
      @generate="$emit('generate')"
      @random-replace="$emit('randomReplace', $event)"
      @pick-replace="$emit('pickReplace', $event)"
  />
  <PickQuestionModal
      :model-value="showPickModal"
      :search="pickSearch"
      :questions="filteredAvailableQuestions"
      :question-type-name="questionTypeName"
      @update:model-value="$emit('update:showPickModal', $event)"
      @update:search="$emit('update:pickSearch', $event)"
      @pick="$emit('pick', $event)"
  />
  <TestRestrictions
      v-if="canReadResults"
      :all-groups="allGroups"
      :all-tags="allTags"
      :selected-user-types="selectedUserTypes"
      :selected-group-ids="selectedGroupIds"
      :selected-tag-ids="selectedTagIds"
      :restrictions-dirty="canConfigure && restrictionsDirty"
      @update:selected-user-types="canConfigure && $emit('updateUserTypes', $event)"
      @update:selected-group-ids="canConfigure && $emit('updateGroupIds', $event)"
      @update:selected-tag-ids="canConfigure && $emit('updateTagIds', $event)"
      @save="$emit('saveRestrictions')"
  />
  <TestAccessGrant v-if="canConfigure" :members="members" @grant="$emit('grant', $event[0], $event[1])" />
</template>
