/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import TestRestrictions from './TestRestrictions.vue'
import TestAccessGrant from './TestAccessGrant.vue'
import TestAttemptList from './TestAttemptList.vue'
import TestDetailHeader from './TestDetailHeader.vue'
import TestInfoCard from './TestInfoCard.vue'
import TestSectionsList from './TestSectionsList.vue'
import FrozenQuestionsList from './FrozenQuestionsList.vue'
import PickQuestionModal from './PickQuestionModal.vue'
import type {FrozenQuestionDetail, QuizQuestion, QuizTestAttempt, QuizTestDetail} from '@/api/quiz'
import type { MemberGroup, UserTag, StationMember } from '@/api/types'

const activeTab = defineModel<string>('activeTab', {required: true})
const editStartAt = defineModel<string>('editStartAt', {required: true})
const editEndAt = defineModel<string>('editEndAt', {required: true})
const showPickModal = defineModel<boolean>('showPickModal', {required: true})
const pickSearch = defineModel<string>('pickSearch', {required: true})
const selectedUserTypes = defineModel<string[]>('selectedUserTypes', {required: true})
const selectedGroupIds = defineModel<number[]>('selectedGroupIds', {required: true})
const selectedTagIds = defineModel<number[]>('selectedTagIds', {required: true})

defineProps<{
  test: QuizTestDetail['test']
  detail: QuizTestDetail | null
  sections: QuizTestDetail['sections']
  attempts: QuizTestAttempt[]
  members: StationMember[]
  frozenQuestions: FrozenQuestionDetail[]
  frozenLoading: boolean
  filteredQuestions: QuizQuestion[]
  allGroups: MemberGroup[]
  allTags: UserTag[]
  restrictionsDirty: boolean
  detailTabs: { key: string; label: string }[]
  timesDirty: boolean
  canConfigure: boolean
  canReadResults: boolean
  catalogName: (catalogId: number) => string
  questionTypeName: (q: QuizQuestion) => string
  saveTimes: () => Promise<void>
}>()

const emit = defineEmits<{
  'mark-times-dirty': []
  activate: []
  close: []
  generate: []
  'random-replace': [position: number]
  'pick-replace': [position: number]
  pick: [questionId: number]
  'save-restrictions': []
  grant: [memberId: number, closesAt: string | null]
}>()

const { t } = useI18n()
const router = useRouter()
</script>

<template>
  <TestDetailHeader
      :test="test"
      :detail="detail"
      :can-configure="canConfigure"
      :can-read-results="canReadResults"
      @activate="emit('activate')"
      @close="emit('close')"
  />

  <TabBar
      v-if="detailTabs.length > 1"
      v-model="activeTab"
      :tabs="detailTabs"
  />

  <template v-if="activeTab === 'test'">
    <TestInfoCard
        :test="test"
        :can-configure="canConfigure"
        v-model:edit-start-at="editStartAt"
        v-model:edit-end-at="editEndAt"
        :times-dirty="timesDirty"
        :save-times="saveTimes"
        @mark-dirty="emit('mark-times-dirty')"
    />

    <TestSectionsList :sections="sections" :catalog-name="catalogName" />

    <FrozenQuestionsList
        v-if="canConfigure"
        :test="test"
        :frozen-questions="frozenQuestions"
        :frozen-loading="frozenLoading"
        :question-type-name="questionTypeName"
        @generate="emit('generate')"
        @random-replace="p => emit('random-replace', p)"
        @pick-replace="p => emit('pick-replace', p)"
    />

    <PickQuestionModal
        v-model="showPickModal"
        v-model:search="pickSearch"
        :questions="filteredQuestions"
        :question-type-name="questionTypeName"
        @pick="id => emit('pick', id)"
    />

    <TestRestrictions
        v-if="canReadResults"
        :all-groups="allGroups"
        :all-tags="allTags"
        :selected-user-types="selectedUserTypes"
        :selected-group-ids="selectedGroupIds"
        :selected-tag-ids="selectedTagIds"
        :restrictions-dirty="canConfigure && restrictionsDirty"
        @update:selected-user-types="types => canConfigure && (selectedUserTypes = types)"
        @update:selected-group-ids="ids => canConfigure && (selectedGroupIds = ids)"
        @update:selected-tag-ids="ids => canConfigure && (selectedTagIds = ids)"
        @save="emit('save-restrictions')"
    />

    <TestAccessGrant v-if="canConfigure" :members="members" @grant="(m, c) => emit('grant', m, c)" />
  </template>

  <template v-if="activeTab === 'results'">
    <TestAttemptList :test-id="test.id" :attempts="attempts" :members="members" />
  </template>

  <div class="flex justify-start">
    <SecondaryButton :icon="['fas', 'arrow-left']" @click="router.push({ name: 'quiz-tests' })">
      {{ t('quiz.tests.backToList') }}
    </SecondaryButton>
  </div>
</template>
