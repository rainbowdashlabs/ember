/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import { QuizTestStatus } from '@/api/types'
import type { QuizTest, QuizTestSummary } from '@/api/types'
import { quiz } from '@/api'
import { useSession } from '@/composables/useSession'
import { useBreakpoint } from '@/composables/useBreakpoint'
import SectionHeader from '@/components/typography/SectionHeader.vue'

const { t } = useI18n()
const router = useRouter()
const { canManageQuiz, loaded } = useSession()
const { isMobile } = useBreakpoint()

const testSummaries = ref<QuizTestSummary[]>([])
const tests = ref<QuizTest[]>([])
const loading = ref(true)
const error = ref('')

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

function testClickRoute(test: QuizTest) {
  if (canManageQuiz()) return { name: 'quiz-test-detail', params: { id: test.id } }
  return { name: 'quiz-test-take', params: { id: test.id } }
}

function attemptCount(test: QuizTest): number {
  const summary = testSummaries.value.find(s => s.test.id === test.id)
  return summary?.attemptCount ?? 0
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    if (canManageQuiz()) {
      testSummaries.value = await quiz.listTests()
      tests.value = testSummaries.value.map(s => s.test)
    } else {
      testSummaries.value = []
      tests.value = await quiz.listAvailableTests()
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function deleteTest(test: QuizTest) {
  showConfirm(t('quiz.tests.confirmDelete'), async () => {
    await quiz.deleteTest(test.id)
    await loadData()
  })
}

function formatDate(dateStr: string | null): string {
  if (!dateStr) return '-'
  return new Date(dateStr).toLocaleDateString()
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

      <template v-if="!loading">
        <div class="flex items-center justify-between">
          <SectionHeader>{{ t('quiz.tests.title') }}</SectionHeader>
          <PrimaryButton v-if="canManageQuiz()" @click="router.push({ name: 'quiz-test-create' })">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
            {{ t('quiz.tests.create') }}
          </PrimaryButton>
        </div>

        <EmptyState v-if="tests.length === 0">{{ t('quiz.tests.noTests') }}</EmptyState>

        <!-- Mobile card layout -->
        <div v-if="isMobile" class="space-y-2">
          <NeutralContainer
            v-for="test in tests"
            :key="test.id"
            class="cursor-pointer"
            @click="router.push(testClickRoute(test))"
          >
            <div class="space-y-2">
              <div class="flex items-center justify-between">
                <span class="font-medium">{{ test.title }}</span>
                <font-awesome-icon v-if="test.restricted" :icon="['fas', 'lock']" class="ml-1 h-3 w-3 text-[var(--text-muted)]"/>
                <SuccessBadge v-if="test.status === QuizTestStatus.ACTIVE">{{ t('quiz.tests.statusActive') }}</SuccessBadge>
                <ErrorBadge v-else-if="test.status === QuizTestStatus.CLOSED">{{ t('quiz.tests.statusClosed') }}</ErrorBadge>
                <SecondaryBadge v-else>{{ t('quiz.tests.statusDraft') }}</SecondaryBadge>
              </div>
              <p v-if="test.description" class="text-xs text-(--text-muted) line-clamp-2">{{ test.description }}</p>
              <div class="flex items-center justify-between text-xs text-(--text-muted)">
                <span>{{ t('quiz.tests.startAt') }}: {{ formatDate(test.startAt) }}</span>
                <span v-if="canManageQuiz()">{{ attemptCount(test) }} {{ t('quiz.attemptCount') }}</span>
                <span>{{ t('quiz.tests.endAt') }}: {{ formatDate(test.endAt) }}</span>
              </div>
              <div class="flex items-center gap-2 pt-2 border-t border-bg-light-accent dark:border-bg-dark-accent">
                <PrimaryButton v-if="test.status === QuizTestStatus.ACTIVE && !canManageQuiz()" @click.stop="router.push({ name: 'quiz-test-take', params: { id: test.id } })">
                  {{ t('quiz.tests.takeTest') }}
                </PrimaryButton>
                <template v-if="canManageQuiz()">
                  <SecondaryButton @click.stop="router.push({ name: 'quiz-test-edit', params: { id: test.id } })">
                    {{ t('common.edit') }}
                  </SecondaryButton>
                  <ErrorButton @click.stop="deleteTest(test)">
                    {{ t('common.delete') }}
                  </ErrorButton>
                </template>
              </div>
            </div>
          </NeutralContainer>
        </div>

        <!-- Desktop table layout -->
        <div v-else class="space-y-2">
          <NeutralContainer
            v-for="test in tests"
            :key="test.id"
            class="cursor-pointer"
            @click="router.push(testClickRoute(test))"
          >
            <div class="flex items-center justify-between gap-4">
              <div class="flex-1 space-y-1">
                <div class="flex items-center gap-2 flex-wrap">
                  <span class="font-medium">{{ test.title }}</span>
                  <font-awesome-icon v-if="test.restricted" :icon="['fas', 'lock']" class="ml-1 h-3 w-3 text-[var(--text-muted)]"/>
                  <SuccessBadge v-if="test.status === QuizTestStatus.ACTIVE">{{ t('quiz.tests.statusActive') }}</SuccessBadge>
                  <ErrorBadge v-else-if="test.status === QuizTestStatus.CLOSED">{{ t('quiz.tests.statusClosed') }}</ErrorBadge>
                  <SecondaryBadge v-else>{{ t('quiz.tests.statusDraft') }}</SecondaryBadge>
                </div>
                <p v-if="test.description" class="text-xs text-(--text-muted) line-clamp-1">{{ test.description }}</p>
              </div>
              <div class="flex items-center gap-4 text-xs text-(--text-muted) shrink-0">
                <span v-if="canManageQuiz()">{{ attemptCount(test) }} {{ t('quiz.attemptCount') }}</span>
                <div class="text-right">
                  <div>{{ t('quiz.tests.startAt') }}: {{ formatDate(test.startAt) }}</div>
                  <div>{{ t('quiz.tests.endAt') }}: {{ formatDate(test.endAt) }}</div>
                </div>
                <div class="flex items-center gap-2" @click.stop>
                  <PrimaryButton v-if="test.status === QuizTestStatus.ACTIVE && !canManageQuiz()" @click="router.push({ name: 'quiz-test-take', params: { id: test.id } })">
                    {{ t('quiz.tests.takeTest') }}
                  </PrimaryButton>
                  <template v-if="canManageQuiz()">
                    <SecondaryButton @click="router.push({ name: 'quiz-test-edit', params: { id: test.id } })">
                      {{ t('common.edit') }}
                    </SecondaryButton>
                    <ErrorButton @click="deleteTest(test)">
                      {{ t('common.delete') }}
                    </ErrorButton>
                  </template>
                </div>
              </div>
            </div>
          </NeutralContainer>
        </div>
      </template>

      <Modal v-model="confirmModalOpen">
        <div class="space-y-4">
          <p class="text-sm">{{ confirmModalMessage }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="confirmModalOpen = false">{{ t('common.cancel') }}</SecondaryButton>
            <ErrorButton @click="executeConfirm">{{ t('common.confirm') }}</ErrorButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
