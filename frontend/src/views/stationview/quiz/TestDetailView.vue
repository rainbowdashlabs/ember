/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'

import { QuizTestStatus } from '@/api/types'
import type { QuizTestDetail, QuizTestAttempt, QuizCatalog, Role, MemberGroup, UserTag, QuizQuestion } from '@/api/types'
import type { FrozenQuestionDetail } from '@/api/quiz'
import { quiz, stationMembers, memberGroups, userTags } from '@/api'
import type { StationMember } from '@/api/types'
import { useSession } from '@/composables/useSession'
import TestRestrictions from './testdetailview/TestRestrictions.vue'
import TestAccessGrant from './testdetailview/TestAccessGrant.vue'
import TestAttemptList from './testdetailview/TestAttemptList.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { canManageQuiz, loaded } = useSession()


const testId = computed(() => Number(route.params.id))

const loading = ref(true)
const error = ref('')
const detail = ref<QuizTestDetail | null>(null)
const attempts = ref<QuizTestAttempt[]>([])
const catalogs = ref<QuizCatalog[]>([])
const members = ref<StationMember[]>([])

// Frozen questions
const frozenQuestions = ref<FrozenQuestionDetail[]>([])
const frozenLoading = ref(false)
const showPickModal = ref(false)
const pickPosition = ref<number | null>(null)
const availableQuestions = ref<QuizQuestion[]>([])
const pickSearch = ref('')

// Restrictions
const allRoles = ref<Role[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const selectedRoleIds = ref<number[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])
const restrictionsDirty = ref(false)

// Confirmation modal
const confirmModalOpen = ref(false)
const confirmModalMessage = ref('')
const confirmModalAction = ref<(() => Promise<void>) | null>(null)

function showConfirm(message: string, action: () => Promise<void>) {
  confirmModalMessage.value = message
  confirmModalAction.value = action
  confirmModalOpen.value = true
}

async function executeConfirm() {
  confirmModalOpen.value = false
  if (confirmModalAction.value) {
    try { await confirmModalAction.value() } catch { /* handled */ }
  }
}

const test = computed(() => detail.value?.test ?? null)
const sections = computed(() => detail.value?.sections ?? [])

function catalogName(catalogId: number): string {
  return catalogs.value.find(c => c.id === catalogId)?.name ?? `#${catalogId}`
}

const editStartAt = ref('')
const editEndAt = ref('')
const timesDirty = ref(false)

function formatDateTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  return `${d.toLocaleDateString()} ${d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
}

function toLocalInput(dateStr: string | null | undefined): string {
  if (!dateStr) return ''
  return dateStr.slice(0, 16)
}

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
    await loadData()
  } catch { error.value = t('common.error') }
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [d, catalogList] = await Promise.all([quiz.getTest(testId.value), quiz.listCatalogs()])
    detail.value = d
    catalogs.value = catalogList.catalogs
    editStartAt.value = toLocalInput(d.test.startAt)
    editEndAt.value = toLocalInput(d.test.endAt)
    timesDirty.value = false

    if (canManageQuiz()) {
      loadFrozenQuestions()
      const [attemptList, memberList, roleList, groupList, tagList, restrictions] = await Promise.all([
        quiz.listAttempts(testId.value),
        stationMembers.listMembers(),
        stationMembers.listAllRoles(),
        memberGroups.listGroups(),
        userTags.listTags(),
        quiz.getRestrictions(testId.value),
      ])
      attempts.value = attemptList
      members.value = memberList
      allRoles.value = roleList
      allGroups.value = groupList
      allTags.value = tagList
      selectedRoleIds.value = restrictions.roleIds ?? []
      selectedGroupIds.value = restrictions.groupIds ?? []
      selectedTagIds.value = restrictions.tagIds ?? []
      restrictionsDirty.value = false
    }
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

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
  return t(`quiz.questionTypes.${q.questionType}`)
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
  showConfirm(t('quiz.tests.confirmActivate'), async () => { await quiz.activateTest(testId.value); await loadData() })
}

function closeTest() {
  showConfirm(t('quiz.tests.confirmClose'), async () => { await quiz.closeTest(testId.value); await loadData() })
}

function onRoleIdsUpdate(ids: number[]) {
  selectedRoleIds.value = ids
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
      roleIds: selectedRoleIds.value,
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

onMounted(() => { if (loaded.value) loadData() })
watch(loaded, (isLoaded) => { if (isLoaded && loading.value) loadData() })
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && test">
        <!-- Header -->
        <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
          <div class="space-y-1">
            <div class="flex items-center gap-2 flex-wrap">
              <SubHeader>{{ test.title }}</SubHeader>
              <SuccessBadge v-if="test.status === QuizTestStatus.ACTIVE">{{ t('quiz.tests.statusActive') }}</SuccessBadge>
              <ErrorBadge v-else-if="test.status === QuizTestStatus.CLOSED">{{ t('quiz.tests.statusClosed') }}</ErrorBadge>
              <SecondaryBadge v-else>{{ t('quiz.tests.statusDraft') }}</SecondaryBadge>
            </div>
            <p v-if="test.description" class="text-sm text-(--text-muted)">{{ test.description }}</p>
            <p v-if="canManageQuiz() && detail" class="text-sm text-(--text-muted)">{{ detail.attemptCount }} {{ t('quiz.attemptCount') }}</p>
          </div>
          <div class="flex items-center gap-2 flex-wrap">
            <PrimaryButton v-if="test.status === QuizTestStatus.ACTIVE" @click="router.push({ name: 'quiz-test-take', params: { id: test.id } })">
              <font-awesome-icon :icon="['fas', 'play']" class="mr-1" />
              {{ t('quiz.tests.takeTest') }}
            </PrimaryButton>
            <template v-if="canManageQuiz()">
              <SuccessButton v-if="test.status === QuizTestStatus.DRAFT" @click="activateTest">
                {{ t('quiz.tests.activate') }}
              </SuccessButton>
              <ErrorButton v-if="test.status === QuizTestStatus.ACTIVE" @click="closeTest">
                {{ t('quiz.tests.close') }}
              </ErrorButton>
              <SecondaryButton v-if="test.status === QuizTestStatus.DRAFT"
                               @click="router.push({ name: 'quiz-test-edit', params: { id: test.id } })">
                {{ t('common.edit') }}
              </SecondaryButton>
              <SecondaryButton @click="quiz.downloadQuestionPdf(test.id)">
                <font-awesome-icon :icon="['fas', 'file-lines']" class="mr-1" />
                {{ t('quiz.tests.exportQuestions') }}
              </SecondaryButton>
              <SecondaryButton @click="quiz.downloadSolutionPdf(test.id)">
                <font-awesome-icon :icon="['fas', 'file-lines']" class="mr-1" />
                {{ t('quiz.tests.exportSolutions') }}
              </SecondaryButton>
            </template>
          </div>
        </div>

        <!-- Test info -->
        <NeutralContainer>
          <div class="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div>
              <span class="text-xs text-(--text-muted) block">{{ t('quiz.tests.timeLimit') }}</span>
              <span>{{ test.timeLimit ? t('quiz.tests.timeLimitValue', { minutes: test.timeLimit }) : t('quiz.tests.noTimeLimit') }}</span>
            </div>
            <div>
              <span class="text-xs text-(--text-muted) block">{{ t('quiz.tests.shuffle') }}</span>
              <span>{{ test.shuffle ? t('common.yes') : t('common.no') }}</span>
            </div>
            <div v-if="canManageQuiz() && test.status !== QuizTestStatus.CLOSED">
              <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.tests.startAt') }}</label>
              <DateTimeInput v-model="editStartAt" @update:model-value="markTimesDirty" />
            </div>
            <div v-else>
              <span class="text-xs text-(--text-muted) block">{{ t('quiz.tests.startAt') }}</span>
              <span>{{ formatDateTime(test.startAt) }}</span>
            </div>
            <div v-if="canManageQuiz() && test.status !== QuizTestStatus.CLOSED">
              <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.tests.endAt') }}</label>
              <DateTimeInput v-model="editEndAt" @update:model-value="markTimesDirty" />
            </div>
            <div v-else>
              <span class="text-xs text-(--text-muted) block">{{ t('quiz.tests.endAt') }}</span>
              <span>{{ formatDateTime(test.endAt) }}</span>
            </div>
          </div>
          <div v-if="timesDirty" class="flex justify-end mt-3">
            <PrimaryButton @click="saveTimes">{{ t('common.save') }}</PrimaryButton>
          </div>
        </NeutralContainer>

        <!-- Sections -->
        <div class="space-y-3">
          <SectionHeader>{{ t('quiz.sections.title') }} ({{ sections.length }})</SectionHeader>
          <NeutralContainer v-for="(section, idx) in sections" :key="section.id">
            <div class="space-y-2">
              <div class="flex items-center gap-2">
                <span class="text-xs font-semibold text-(--text-muted)">{{ idx + 1 }}.</span>
                <span class="font-medium">{{ section.title || t('quiz.sections.untitled') }}</span>
              </div>
              <p v-if="section.description" class="text-xs text-(--text-muted)">{{ section.description }}</p>
              <div class="flex flex-wrap gap-2">
                <InfoBadge v-for="source in section.sources" :key="source.id">
                  {{ catalogName(source.catalogId) }}
                  <template v-if="source.categoryId"> / {{ source.categoryId }}</template>
                  ({{ source.questionCount }})
                </InfoBadge>
              </div>
            </div>
          </NeutralContainer>
        </div>

        <!-- Frozen Questions -->
        <template v-if="canManageQuiz()">
          <div class="space-y-3">
            <div class="flex items-center justify-between flex-wrap gap-2">
              <SectionHeader>{{ t('quiz.frozenQuestions.title') }} ({{ frozenQuestions.length }})</SectionHeader>
              <SecondaryButton :disabled="frozenLoading" @click="generateQuestions">
                <Spinner v-if="frozenLoading" size="sm" />
                <font-awesome-icon v-else :icon="['fas', 'rotate']" class="mr-1" />
                {{ frozenQuestions.length > 0 ? t('quiz.frozenQuestions.regenerate') : t('quiz.frozenQuestions.generate') }}
              </SecondaryButton>
            </div>
            <div v-if="frozenQuestions.length === 0" class="text-center text-(--text-muted) py-4">
              {{ t('quiz.frozenQuestions.empty') }}
            </div>
            <NeutralContainer v-for="fq in frozenQuestions" :key="fq.position">
              <div v-if="fq.question" class="flex items-start gap-3">
                <span class="text-xs text-(--text-muted) w-6 shrink-0 pt-0.5">{{ fq.position + 1 }}.</span>
                <div class="flex-1 min-w-0">
                  <div class="flex items-center gap-2 flex-wrap">
                    <span class="text-sm font-medium">{{ fq.question.title }}</span>
                    <SecondaryBadge>{{ questionTypeName(fq.question) }}</SecondaryBadge>
                    <InfoBadge>{{ fq.question.points }} {{ t('quiz.points') }}</InfoBadge>
                  </div>
                  <p v-if="fq.question.description" class="text-xs text-(--text-muted) mt-0.5">{{ fq.question.description }}</p>
                </div>
                <div v-if="test.status === QuizTestStatus.DRAFT" class="flex gap-1 shrink-0">
                  <IconButton :icon="['fas', 'shuffle']" :label="t('quiz.frozenQuestions.randomReplace')" class="text-(--text-muted) hover:text-primary" @click="randomReplace(fq.position)" />
                  <IconButton :icon="['fas', 'arrow-right-arrow-left']" :label="t('quiz.frozenQuestions.pickReplace')" class="text-(--text-muted) hover:text-primary" @click="openPickModal(fq.position)" />
                </div>
              </div>
              <div v-else class="text-xs text-(--text-muted) italic">{{ t('quiz.frozenQuestions.questionMissing') }}</div>
            </NeutralContainer>
          </div>
        </template>

        <!-- Pick Question Modal -->
        <Modal v-model="showPickModal">
          <div class="space-y-4">
            <SubHeader>{{ t('quiz.frozenQuestions.pickTitle') }}</SubHeader>
            <TextInput v-model="pickSearch" :placeholder="t('quiz.frozenQuestions.searchPlaceholder')" />
            <div class="max-h-80 overflow-y-auto space-y-2">
              <div v-if="filteredAvailableQuestions.length === 0" class="text-center text-(--text-muted) py-4 text-sm">
                {{ t('quiz.frozenQuestions.noAvailable') }}
              </div>
              <NeutralContainer
                v-for="q in filteredAvailableQuestions"
                :key="q.id"
                class="cursor-pointer hover:!border-primary transition-colors"
                @click="pickQuestion(q.id)"
              >
                <div class="flex items-center gap-2">
                  <div class="flex-1 min-w-0">
                    <div class="flex items-center gap-2 flex-wrap">
                      <span class="text-sm font-medium">{{ q.title }}</span>
                      <SecondaryBadge>{{ questionTypeName(q) }}</SecondaryBadge>
                      <InfoBadge>{{ q.points }} {{ t('quiz.points') }}</InfoBadge>
                    </div>
                  </div>
                </div>
              </NeutralContainer>
            </div>
            <div class="flex justify-end">
              <SecondaryButton @click="showPickModal = false">{{ t('common.cancel') }}</SecondaryButton>
            </div>
          </div>
        </Modal>

        <!-- Management sections -->
        <template v-if="canManageQuiz()">
          <TestAttemptList :test-id="test.id" :attempts="attempts" :members="members" />

          <TestRestrictions
            :all-roles="allRoles"
            :all-groups="allGroups"
            :all-tags="allTags"
            :selected-role-ids="selectedRoleIds"
            :selected-group-ids="selectedGroupIds"
            :selected-tag-ids="selectedTagIds"
            :restrictions-dirty="restrictionsDirty"
            @update:selected-role-ids="onRoleIdsUpdate"
            @update:selected-group-ids="onGroupIdsUpdate"
            @update:selected-tag-ids="onTagIdsUpdate"
            @save="saveRestrictions"
          />

          <TestAccessGrant :members="members" @grant="grantAccess" />
        </template>

        <div class="flex justify-start">
          <SecondaryButton @click="router.push({ name: 'quiz-tests' })">
            <font-awesome-icon :icon="['fas', 'arrow-left']" class="mr-1" />
            {{ t('quiz.tests.backToList') }}
          </SecondaryButton>
        </div>
      </template>

      <!-- Confirmation Modal -->
      <Modal v-model="confirmModalOpen">
        <div class="space-y-4">
          <p class="text-sm">{{ confirmModalMessage }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="confirmModalOpen = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton @click="executeConfirm">{{ t('common.confirm') }}</PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
