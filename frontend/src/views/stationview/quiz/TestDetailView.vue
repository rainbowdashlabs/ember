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
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import DateTimeInput from '@/components/input/datetime/DateTimeInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'

import { QuizTestStatus, QuizAttemptStatus } from '@/api/types'
import type { QuizTestDetail, QuizTestAttempt, QuizCatalog, Role, MemberGroup, UserTag, QuizQuestion } from '@/api/types'
import type { FrozenQuestionDetail } from '@/api/quiz'
import { quiz, stationMembers, memberGroups, userTags } from '@/api'
import type { StationMember } from '@/api/types'
import { useSession } from '@/composables/useSession'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useStations } from '@/composables/useStations'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { canManageQuiz, loaded } = useSession()
const { isMobile } = useBreakpoint()
const { currentStationId } = useStations()

const testId = computed(() => Number(route.params.id))

const loading = ref(true)
const error = ref('')
const detail = ref<QuizTestDetail | null>(null)
const attempts = ref<QuizTestAttempt[]>([])
const catalogs = ref<QuizCatalog[]>([])
const members = ref<StationMember[]>([])

// Access management
const accessMemberSearch = ref('')
const accessMemberId = ref<number | undefined>(undefined)
const accessClosesAt = ref('')

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
const selectedRoleIds = ref<Set<number>>(new Set())
const selectedGroupIds = ref<Set<number>>(new Set())
const selectedTagIds = ref<Set<number>>(new Set())
const restrictionsDirty = ref(false)

const filteredMembers = computed(() => {
  const search = accessMemberSearch.value.toLowerCase().trim()
  if (!search) return members.value.slice(0, 10)
  return members.value.filter(m => m.name?.toLowerCase().includes(search) || m.email?.toLowerCase().includes(search)).slice(0, 10)
})

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
  const cat = catalogs.value.find(c => c.id === catalogId)
  return cat?.name ?? `#${catalogId}`
}

// Editable start/end time
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

function markTimesDirty() {
  timesDirty.value = true
}

async function saveTimes() {
  if (!test.value) return
  error.value = ''
  try {
    await quiz.updateTest(test.value.id, {
      title: test.value.title,
      description: test.value.description,
      timeLimit: test.value.timeLimit,
      shuffle: test.value.shuffle,
      startAt: editStartAt.value ? new Date(editStartAt.value).toISOString() : null,
      endAt: editEndAt.value ? new Date(editEndAt.value).toISOString() : null,
    })
    timesDirty.value = false
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

function memberName(memberId: number): string {
  const m = members.value.find(m => m.id === memberId)
  return m?.name ?? m?.email ?? `#${memberId}`
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [d, catalogResponse] = await Promise.all([
      quiz.getTest(testId.value),
      quiz.listCatalogs(),
    ])
    detail.value = d
    catalogs.value = catalogResponse.catalogs
    editStartAt.value = toLocalInput(d.test.startAt)
    editEndAt.value = toLocalInput(d.test.endAt)
    timesDirty.value = false

    if (canManageQuiz()) {
      loadFrozenQuestions()
      const [attemptList, memberList, roleList, groupList, tagList, restrictions] = await Promise.all([
        quiz.listAttempts(testId.value),
        stationMembers.listMembers(currentStationId.value!),
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
      selectedRoleIds.value = new Set(restrictions.roleIds)
      selectedGroupIds.value = new Set(restrictions.groupIds)
      selectedTagIds.value = new Set(restrictions.tagIds)
      restrictionsDirty.value = false
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function loadFrozenQuestions() {
  try {
    frozenQuestions.value = await quiz.listFrozenQuestions(testId.value)
  } catch {
    frozenQuestions.value = []
  }
}

async function generateQuestions() {
  frozenLoading.value = true
  error.value = ''
  try {
    frozenQuestions.value = await quiz.generateFrozenQuestions(testId.value)
  } catch {
    error.value = t('common.error')
  } finally {
    frozenLoading.value = false
  }
}

function questionTypeName(q: QuizQuestion): string {
  return t(`quiz.questionTypes.${q.questionType}`)
}

async function randomReplace(position: number) {
  error.value = ''
  try {
    frozenQuestions.value = await quiz.randomReplaceFrozenQuestion(testId.value, position)
  } catch {
    error.value = t('common.error')
  }
}

async function openPickModal(position: number) {
  pickPosition.value = position
  pickSearch.value = ''
  error.value = ''
  try {
    availableQuestions.value = await quiz.listAvailableReplacements(testId.value)
    showPickModal.value = true
  } catch {
    error.value = t('common.error')
  }
}

async function pickQuestion(questionId: number) {
  if (pickPosition.value === null) return
  error.value = ''
  try {
    frozenQuestions.value = await quiz.replaceFrozenQuestion(testId.value, pickPosition.value, questionId)
    showPickModal.value = false
    pickPosition.value = null
  } catch {
    error.value = t('common.error')
  }
}

const filteredAvailableQuestions = computed(() => {
  const search = pickSearch.value.toLowerCase().trim()
  if (!search) return availableQuestions.value
  return availableQuestions.value.filter(q =>
    q.title.toLowerCase().includes(search) || questionTypeName(q).toLowerCase().includes(search)
  )
})

function activateTest() {
  showConfirm(t('quiz.tests.confirmActivate'), async () => {
    await quiz.activateTest(testId.value)
    await loadData()
  })
}

function closeTest() {
  showConfirm(t('quiz.tests.confirmClose'), async () => {
    await quiz.closeTest(testId.value)
    await loadData()
  })
}

function selectAccessMember(m: StationMember) {
  accessMemberId.value = m.id
  accessMemberSearch.value = m.name ?? m.email ?? ''
}

async function grantAccess() {
  if (!accessMemberId.value) return
  error.value = ''
  try {
    await quiz.grantAccess(
      testId.value,
      accessMemberId.value,
      accessClosesAt.value ? new Date(accessClosesAt.value).toISOString() : null,
    )
    accessMemberId.value = undefined
    accessMemberSearch.value = ''
    accessClosesAt.value = ''
  } catch {
    error.value = t('common.error')
  }
}

function toggleRole(roleId: number) {
  const next = new Set(selectedRoleIds.value)
  if (next.has(roleId)) next.delete(roleId)
  else next.add(roleId)
  selectedRoleIds.value = next
  restrictionsDirty.value = true
}

function toggleGroup(groupId: number) {
  const next = new Set(selectedGroupIds.value)
  if (next.has(groupId)) next.delete(groupId)
  else next.add(groupId)
  selectedGroupIds.value = next
  restrictionsDirty.value = true
}

function toggleTag(tagId: number) {
  const next = new Set(selectedTagIds.value)
  if (next.has(tagId)) next.delete(tagId)
  else next.add(tagId)
  selectedTagIds.value = next
  restrictionsDirty.value = true
}

async function saveRestrictions() {
  error.value = ''
  try {
    await quiz.setRestrictions(testId.value, {
      roleIds: [...selectedRoleIds.value],
      groupIds: [...selectedGroupIds.value],
      tagIds: [...selectedTagIds.value],
    })
    restrictionsDirty.value = false
  } catch {
    error.value = t('common.error')
  }
}

function attemptStatusLabel(status: string): string {
  if (status === QuizAttemptStatus.IN_PROGRESS) return t('quiz.attempt.statusInProgress')
  if (status === QuizAttemptStatus.SUBMITTED) return t('quiz.attempt.statusSubmitted')
  if (status === QuizAttemptStatus.GRADED) return t('quiz.attempt.statusGraded')
  return status
}

onMounted(() => {
  if (loaded.value) loadData()
})

watch(loaded, (isLoaded) => {
  if (isLoaded && loading.value) loadData()
})
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
              <h2 class="text-xl font-semibold">{{ test.title }}</h2>
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

        <!-- Frozen Questions (management view, DRAFT only) -->
        <template v-if="canManageQuiz()">
          <div class="space-y-3">
            <div class="flex items-center justify-between flex-wrap gap-2">
              <SectionHeader>{{ t('quiz.frozenQuestions.title') }} ({{ frozenQuestions.length }})</SectionHeader>
              <div class="flex gap-2">
                <SecondaryButton :disabled="frozenLoading" @click="generateQuestions">
                  <Spinner v-if="frozenLoading" size="sm" />
                  <font-awesome-icon v-else :icon="['fas', 'rotate']" class="mr-1" />
                  {{ frozenQuestions.length > 0 ? t('quiz.frozenQuestions.regenerate') : t('quiz.frozenQuestions.generate') }}
                </SecondaryButton>
              </div>
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
                <div v-if="test && test.status === QuizTestStatus.DRAFT" class="flex gap-1 shrink-0">
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
            <h3 class="text-lg font-semibold">{{ t('quiz.frozenQuestions.pickTitle') }}</h3>
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

        <!-- Attempts (management view) -->
        <template v-if="canManageQuiz()">
          <div class="space-y-3">
            <SectionHeader>{{ t('quiz.attempt.title') }} ({{ attempts.length }})</SectionHeader>

            <div v-if="attempts.length === 0" class="text-center text-(--text-muted) py-4">
              {{ t('quiz.attempt.noAttempts') }}
            </div>

            <!-- Mobile cards -->
            <template v-if="isMobile">
              <NeutralContainer
                v-for="attempt in attempts"
                :key="attempt.id"
                class="cursor-pointer"
                @click="router.push({ name: 'quiz-test-evaluate', params: { id: test.id, attemptId: attempt.id } })"
              >
                <div class="space-y-2">
                  <div class="flex items-center justify-between">
                    <span class="font-medium text-sm">{{ memberName(attempt.memberId) }}</span>
                    <SuccessBadge v-if="attempt.status === QuizAttemptStatus.GRADED">{{ attemptStatusLabel(attempt.status) }}</SuccessBadge>
                    <InfoBadge v-else-if="attempt.status === QuizAttemptStatus.SUBMITTED">{{ attemptStatusLabel(attempt.status) }}</InfoBadge>
                    <SecondaryBadge v-else>{{ attemptStatusLabel(attempt.status) }}</SecondaryBadge>
                  </div>
                  <div class="flex items-center justify-between text-xs text-(--text-muted)">
                    <span>{{ formatDateTime(attempt.startedAt) }}</span>
                    <span v-if="attempt.status === QuizAttemptStatus.GRADED">
                      {{ attempt.totalPoints }}/{{ attempt.maxPoints }} {{ t('quiz.attempt.points') }}
                    </span>
                  </div>
                </div>
              </NeutralContainer>
            </template>

            <!-- Desktop table -->
            <template v-else>
              <NeutralContainer
                v-for="attempt in attempts"
                :key="attempt.id"
                class="cursor-pointer"
                @click="router.push({ name: 'quiz-test-evaluate', params: { id: test.id, attemptId: attempt.id } })"
              >
                <div class="flex items-center justify-between gap-4">
                  <div class="flex items-center gap-3 flex-1">
                    <span class="font-medium text-sm">{{ memberName(attempt.memberId) }}</span>
                    <SuccessBadge v-if="attempt.status === QuizAttemptStatus.GRADED">{{ attemptStatusLabel(attempt.status) }}</SuccessBadge>
                    <InfoBadge v-else-if="attempt.status === QuizAttemptStatus.SUBMITTED">{{ attemptStatusLabel(attempt.status) }}</InfoBadge>
                    <SecondaryBadge v-else>{{ attemptStatusLabel(attempt.status) }}</SecondaryBadge>
                  </div>
                  <div class="flex items-center gap-4 text-xs text-(--text-muted) shrink-0">
                    <span>{{ formatDateTime(attempt.startedAt) }}</span>
                    <span v-if="attempt.submittedAt">{{ formatDateTime(attempt.submittedAt) }}</span>
                    <span v-if="attempt.status === QuizAttemptStatus.GRADED" class="font-medium text-sm text-(--text)">
                      {{ attempt.totalPoints }}/{{ attempt.maxPoints }} {{ t('quiz.attempt.points') }}
                    </span>
                  </div>
                </div>
              </NeutralContainer>
            </template>
          </div>

          <!-- Restrictions -->
          <div class="space-y-3">
            <SectionHeader>{{ t('quiz.tests.restrictions') }}</SectionHeader>
            <NeutralContainer>
              <div class="space-y-3">
                <div class="space-y-2">
                  <label class="text-xs text-(--text-muted)">{{ t('quiz.tests.restrictionRoles') }}</label>
                  <div class="flex flex-wrap gap-2">
                    <button v-for="role in allRoles" :key="role.id"
                            :class="selectedRoleIds.has(role.id) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
                            class="px-2 py-1 text-xs rounded border transition-colors"
                            @click="toggleRole(role.id)">
                      {{ role.role }}
                    </button>
                  </div>
                </div>
                <div class="space-y-2">
                  <label class="text-xs text-(--text-muted)">{{ t('quiz.tests.restrictionGroups') }}</label>
                  <div class="flex flex-wrap gap-2">
                    <button v-for="group in allGroups" :key="group.id"
                            :class="selectedGroupIds.has(group.id) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
                            class="px-2 py-1 text-xs rounded border transition-colors"
                            @click="toggleGroup(group.id)">
                      {{ group.name }}
                    </button>
                    <span v-if="allGroups.length === 0" class="text-xs text-(--text-muted)">–</span>
                  </div>
                </div>
                <div class="space-y-2">
                  <label class="text-xs text-(--text-muted)">{{ t('quiz.tests.restrictionTags') }}</label>
                  <div class="flex flex-wrap gap-2">
                    <button v-for="tag in allTags" :key="tag.id"
                            :class="selectedTagIds.has(tag.id) ? 'border-primary bg-primary/15 text-primary' : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary'"
                            class="px-2 py-1 text-xs rounded border transition-colors"
                            @click="toggleTag(tag.id)">
                      {{ tag.name }}
                    </button>
                    <span v-if="allTags.length === 0" class="text-xs text-(--text-muted)">–</span>
                  </div>
                </div>
                <p v-if="selectedRoleIds.size === 0 && selectedGroupIds.size === 0 && selectedTagIds.size === 0"
                   class="text-xs text-(--text-muted) italic">{{ t('quiz.tests.noRestrictions') }}</p>
                <div v-if="restrictionsDirty" class="flex justify-end">
                  <PrimaryButton @click="saveRestrictions">{{ t('common.save') }}</PrimaryButton>
                </div>
              </div>
            </NeutralContainer>
          </div>

          <!-- Grant individual access -->
          <div class="space-y-3">
            <SectionHeader>{{ t('quiz.tests.accessManagement') }}</SectionHeader>
            <NeutralContainer>
              <div class="flex flex-col sm:flex-row gap-3 items-start sm:items-end">
                <div class="flex-1 relative">
                  <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.tests.grantAccessMember') }}</label>
                  <TextInput v-model="accessMemberSearch" :placeholder="t('quiz.tests.searchMember')" />
                  <div v-if="accessMemberSearch && !accessMemberId && filteredMembers.length > 0"
                       class="absolute z-10 top-full mt-1 w-full rounded-lg border border-bg-light-accent dark:border-bg-dark-accent bg-bg-light dark:bg-bg-dark shadow-lg max-h-48 overflow-y-auto">
                    <button v-for="m in filteredMembers" :key="m.id"
                            class="w-full text-left px-3 py-2 text-sm hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
                            @click="selectAccessMember(m)">
                      {{ m.name ?? m.email }}
                    </button>
                  </div>
                </div>
                <div>
                  <label class="text-xs text-(--text-muted) block mb-1">{{ t('quiz.tests.accessClosesAt') }}</label>
                  <DateTimeInput v-model="accessClosesAt" />
                </div>
                <PrimaryButton @click="grantAccess" :disabled="!accessMemberId">
                  {{ t('quiz.tests.grantAccess') }}
                </PrimaryButton>
              </div>
            </NeutralContainer>
          </div>
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
