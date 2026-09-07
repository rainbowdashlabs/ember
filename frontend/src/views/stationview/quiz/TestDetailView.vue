/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {StationPermission, type MemberGroup, type StationMember, type UserTag} from '@/api/types'
import type {FrozenQuestionDetail, QuizCatalog, QuizQuestion, QuizTestAttempt, QuizTestDetail} from '@/api/quiz'
import { quiz, stationMembers, memberGroups, userTags } from '@/api'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import TestDetailBody from './testdetailview/TestDetailBody.vue'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { instantToLocalInput } from '@/util/format'

const { t } = useI18n()
const route = useRoute()
const { hasPermission, loaded } = useSession()
const canConfigure = () => hasPermission(StationPermission.TEST_CONFIGURE)
const canReadResults = () => hasPermission(StationPermission.TEST_RESULT_READ)

const activeTab = ref('test')
const detailTabs = computed(() => {
  const t_ = [{ key: 'test', label: t('quiz.tests.tabTest') }]
  if (canReadResults()) t_.push({ key: 'results', label: t('quiz.tests.tabResults') })
  return t_
})


const testId = computed(() => Number(route.params.id))

const detail = ref<QuizTestDetail | null>(null)
const attempts = ref<QuizTestAttempt[]>([])
const catalogs = ref<QuizCatalog[]>([])
const members = ref<StationMember[]>([])

const frozenQuestions = ref<FrozenQuestionDetail[]>([])
const frozenLoading = ref(false)
const showPickModal = ref(false)
const pickPosition = ref<number | null>(null)
const availableQuestions = ref<QuizQuestion[]>([])
const pickSearch = ref('')

const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const selectedUserTypes = ref<string[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])
const restrictionsDirty = ref(false)

interface PendingConfirm {
  message: string
  action: () => Promise<void>
}

const confirmAction = useConfirmAction<PendingConfirm>({
  onConfirm: async (pending) => {
    try {
      await pending.action()
    } catch {
      return
    }
  },
})

function showConfirm(message: string, action: () => Promise<void>) {
  confirmAction.request({message, action})
}

const test = computed(() => detail.value?.test ?? null)
const sections = computed(() => detail.value?.sections ?? [])

function catalogName(catalogId: number): string {
  return catalogs.value.find(c => c.id === catalogId)?.name ?? `#${catalogId}`
}

const editStartAt = ref('')
const editEndAt = ref('')
const timesDirty = ref(false)

function markTimesDirty() { timesDirty.value = true }

async function saveTimes() {
  if (!test.value) return
  error.value = ''
  try {
    await quiz.updateTest(test.value.id, {
      title: test.value.title, description: test.value.description,
      timeLimit: test.value.timeLimit, shuffle: test.value.shuffle,
      startAt: editStartAt.value ? new Date(editStartAt.value).toISOString() : null,
      endAt: editEndAt.value ? new Date(editEndAt.value).toISOString() : null,
    })
    timesDirty.value = false
    await reload()
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

const {loading, error, reload} = useAsyncLoader(async () => {
  const [d, catalogList] = await Promise.all([quiz.getTest(testId.value), quiz.listCatalogs()])
  detail.value = d
  catalogs.value = catalogList.catalogs
  editStartAt.value = instantToLocalInput(d.test.startAt)
  editEndAt.value = instantToLocalInput(d.test.endAt)
  timesDirty.value = false

  if (canReadResults()) {
    loadFrozenQuestions()
    const [attemptList, memberList, groupList, tagList, restrictions] = await Promise.all([
      quiz.listAttempts(testId.value),
      stationMembers.listMembers(),
      memberGroups.listGroups(),
      userTags.listTags(),
      quiz.getRestrictions(testId.value),
    ])
    attempts.value = attemptList
    members.value = memberList
    allGroups.value = groupList
    allTags.value = tagList
    selectedUserTypes.value = restrictions.userTypes ?? []
    selectedGroupIds.value = restrictions.groupIds ?? []
    selectedTagIds.value = restrictions.tagIds ?? []
    restrictionsDirty.value = false
  }
}, {autoLoad: loaded.value})

async function loadFrozenQuestions() {
  try { frozenQuestions.value = await quiz.listFrozenQuestions(testId.value) }
  catch { frozenQuestions.value = [] }
}

async function generateQuestions() {
  frozenLoading.value = true
  error.value = ''
  try { frozenQuestions.value = await quiz.generateFrozenQuestions(testId.value) }
  catch { error.value = t('common.error') }
  finally { frozenLoading.value = false }
}

function questionTypeName(q: QuizQuestion): string {
  return t(`quiz.questionTypes.${q.quizQuestionType}`)
}

async function randomReplace(position: number) {
  error.value = ''
  try { frozenQuestions.value = await quiz.randomReplaceFrozenQuestion(testId.value, position) }
  catch { error.value = t('common.error') }
}

async function openPickModal(position: number) {
  pickPosition.value = position
  pickSearch.value = ''
  error.value = ''
  try {
    availableQuestions.value = await quiz.listAvailableReplacements(testId.value)
    showPickModal.value = true
  } catch { error.value = t('common.error') }
}

async function pickQuestion(questionId: number) {
  if (pickPosition.value === null) return
  error.value = ''
  try {
    frozenQuestions.value = await quiz.replaceFrozenQuestion(testId.value, pickPosition.value, questionId)
    showPickModal.value = false
    pickPosition.value = null
  } catch { error.value = t('common.error') }
}

const filteredAvailableQuestions = computed(() => {
  const search = pickSearch.value.toLowerCase().trim()
  if (!search) return availableQuestions.value
  return availableQuestions.value.filter(q =>
    q.title.toLowerCase().includes(search) || questionTypeName(q).toLowerCase().includes(search),
  )
})

function activateTest() {
  showConfirm(t('quiz.tests.confirmActivate'), async () => { await quiz.activateTest(testId.value); await reload() })
}

function closeTest() {
  showConfirm(t('quiz.tests.confirmClose'), async () => { await quiz.closeTest(testId.value); await reload() })
}

function onUserTypesUpdate(types: string[]) {
  selectedUserTypes.value = types
  restrictionsDirty.value = true
}

function onGroupIdsUpdate(ids: number[]) {
  selectedGroupIds.value = ids
  restrictionsDirty.value = true
}

function onTagIdsUpdate(ids: number[]) {
  selectedTagIds.value = ids
  restrictionsDirty.value = true
}

async function saveRestrictions() {
  error.value = ''
  try {
    await quiz.setRestrictions(testId.value, {
      userTypes: selectedUserTypes.value,
      groupIds: selectedGroupIds.value,
      tagIds: selectedTagIds.value,
    })
    restrictionsDirty.value = false
  } catch { error.value = t('common.error') }
}

async function grantAccess(memberId: number, closesAt: string | null) {
  error.value = ''
  try { await quiz.grantAccess(testId.value, memberId, closesAt) }
  catch { error.value = t('common.error') }
}

watch(loaded, (isLoaded) => { if (isLoaded) reload() })
</script>

<template>
  <ViewContent :title="t('pages.quiz-test-detail.title')" :subtitle="t('pages.quiz-test-detail.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <TestDetailBody
          v-if="!loading && test" :test="test" :detail="detail" :sections="sections"
          :attempts="attempts" :members="members" :frozen-questions="frozenQuestions"
          :frozen-loading="frozenLoading" :filtered-available-questions="filteredAvailableQuestions"
          :all-groups="allGroups" :all-tags="allTags" :restrictions-dirty="restrictionsDirty"
          :detail-tabs="detailTabs" :times-dirty="timesDirty" :can-configure="canConfigure()"
          :can-read-results="canReadResults()" :catalog-name="catalogName"
          :question-type-name="questionTypeName" :save-times="saveTimes"
          v-model:active-tab="activeTab" v-model:edit-start-at="editStartAt"
          v-model:edit-end-at="editEndAt" v-model:show-pick-modal="showPickModal"
          v-model:pick-search="pickSearch" :selected-user-types="selectedUserTypes"
          :selected-group-ids="selectedGroupIds" :selected-tag-ids="selectedTagIds"
          @update:selected-user-types="onUserTypesUpdate"
          @update:selected-group-ids="onGroupIdsUpdate" @update:selected-tag-ids="onTagIdsUpdate"
          @activate="activateTest" @close="closeTest" @mark-times-dirty="markTimesDirty"
          @generate="generateQuestions" @random-replace="randomReplace" @pick-replace="openPickModal"
          @pick="pickQuestion" @save-restrictions="saveRestrictions" @grant="grantAccess"
      />

      <Modal v-model="confirmAction.show.value">
        <div class="space-y-4">
          <p class="text-sm">{{ confirmAction.target.value?.message ?? '' }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="confirmAction.show.value = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton @click="confirmAction.confirm">{{ t('common.confirm') }}</PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
