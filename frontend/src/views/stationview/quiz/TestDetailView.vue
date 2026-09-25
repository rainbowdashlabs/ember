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
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {StationPermission, type MemberGroup, type StationMember, type UserTag} from '@/api/types'
import type {FrozenQuestionDetail, QuizCatalog, QuizQuestion, QuizTestAttempt, QuizTestDetail} from '@/api/quiz'
import { quiz, stationMembers, memberGroups, userTags } from '@/api'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import TestDetailBody from './testdetailview/TestDetailBody.vue'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { instantToLocalInput } from '@/util/format'
import { describeFailure } from '@/util/failure'

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
  /** Whatever the act answers with is ignored; the page is caught up separately afterwards. */
  action: () => Promise<unknown>
}

const test = computed(() => detail.value?.test ?? null)
const sections = computed(() => detail.value?.sections ?? [])

/**
 * The test's own title at the head of the page, because "Test" is the word above every one of them
 * and it is what the tab, the history and a bookmark carry. It holds the place while the test loads
 * and where it cannot be loaded at all.
 */
const pageTitle = computed(() => test.value?.title || t('pages.quiz-test-detail.title'))

function catalogName(catalogId: number): string {
  return catalogs.value.find(c => c.id === catalogId)?.name ?? `#${catalogId}`
}

const editStartAt = ref('')
const editEndAt = ref('')
const timesDirty = ref(false)

function markTimesDirty() { timesDirty.value = true }

/**
 * Writes the opening and closing times, then reads the test back.
 *
 * <p>The two are answered for separately: times the server had already taken, followed by a page
 * that would not refresh, used to say the times had been refused.
 */
async function saveTimes() {
  if (!test.value) return
  failure.value = null
  try {
    await quiz.updateTest(test.value.id, {
      title: test.value.title, description: test.value.description,
      timeLimit: test.value.timeLimit, shuffle: test.value.shuffle,
      startAt: editStartAt.value ? new Date(editStartAt.value).toISOString() : null,
      endAt: editEndAt.value ? new Date(editEndAt.value).toISOString() : null,
    })
    timesDirty.value = false
  } catch (e) {
    failure.value = describeFailure(e, t)
    throw e
  }
  await reload()
}

const {loading, failure, reload} = useAsyncLoader(async () => {
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

/**
 * Opening and closing a test, both asked about first and both followed by reading the test back.
 *
 * <p>The two halves are the lifecycle's to keep apart, so a test that was opened and a page that
 * then failed to refresh does not read as a test that would not open. What this used to do was
 * worse than either: the refusal was caught and dropped, and a test that stayed shut said nothing.
 */
const confirmAction = useConfirmAction<PendingConfirm>({
  onConfirm: async pending => { await pending.action() },
  onSuccess: () => reload(),
  failure,
})

function showConfirm(message: string, action: () => Promise<unknown>) {
  confirmAction.request({message, action})
}

/** An empty question sheet and one that could not be read are different answers, so they read so. */
async function loadFrozenQuestions() {
  try { frozenQuestions.value = await quiz.listFrozenQuestions(testId.value) }
  catch (e) {
    frozenQuestions.value = []
    failure.value = describeFailure(e, t)
  }
}

async function generateQuestions() {
  frozenLoading.value = true
  failure.value = null
  try { frozenQuestions.value = await quiz.generateFrozenQuestions(testId.value) }
  catch (e) { failure.value = describeFailure(e, t) }
  finally { frozenLoading.value = false }
}

function questionTypeName(q: QuizQuestion): string {
  return t(`quiz.questionTypes.${q.quizQuestionType}`)
}

async function randomReplace(position: number) {
  failure.value = null
  try { frozenQuestions.value = await quiz.randomReplaceFrozenQuestion(testId.value, position) }
  catch (e) { failure.value = describeFailure(e, t) }
}

async function openPickModal(position: number) {
  pickPosition.value = position
  pickSearch.value = ''
  failure.value = null
  try {
    availableQuestions.value = await quiz.listAvailableReplacements(testId.value)
    showPickModal.value = true
  } catch (e) { failure.value = describeFailure(e, t) }
}

async function pickQuestion(questionId: number) {
  if (pickPosition.value === null) return
  failure.value = null
  try {
    frozenQuestions.value = await quiz.replaceFrozenQuestion(testId.value, pickPosition.value, questionId)
    showPickModal.value = false
    pickPosition.value = null
  } catch (e) { failure.value = describeFailure(e, t) }
}

const filteredAvailableQuestions = computed(() => {
  const search = pickSearch.value.toLowerCase().trim()
  if (!search) return availableQuestions.value
  return availableQuestions.value.filter(q =>
    q.title.toLowerCase().includes(search) || questionTypeName(q).toLowerCase().includes(search),
  )
})

function activateTest() {
  showConfirm(t('quiz.tests.confirmActivate'), () => quiz.activateTest(testId.value))
}

function closeTest() {
  showConfirm(t('quiz.tests.confirmClose'), () => quiz.closeTest(testId.value))
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
  failure.value = null
  try {
    await quiz.setRestrictions(testId.value, {
      userTypes: selectedUserTypes.value,
      groupIds: selectedGroupIds.value,
      tagIds: selectedTagIds.value,
    })
    restrictionsDirty.value = false
  } catch (e) { failure.value = describeFailure(e, t) }
}

async function grantAccess(memberId: number, closesAt: string | null) {
  failure.value = null
  try { await quiz.grantAccess(testId.value, memberId, closesAt) }
  catch (e) { failure.value = describeFailure(e, t) }
}

watch(loaded, (isLoaded) => { if (isLoaded) reload() })
</script>

<template>
  <ViewContent :title="pageTitle" :subtitle="t('pages.quiz-test-detail.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <FailureAlert :failure="failure"/>

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
          <ButtonRow pair align="end">
            <SecondaryButton @click="confirmAction.show.value = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton @click="confirmAction.confirm">{{ t('common.confirm') }}</PrimaryButton>
          </ButtonRow>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
