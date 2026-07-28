/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import type { QuizTest, QuizTestSummary, QuizAvailableTest } from '@/api/quiz'
import { quiz } from '@/api'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useBreakpoint } from '@/composables/useBreakpoint'
import TabBar from '@/components/navigation/TabBar.vue'
import TestList from './testlistview/TestList.vue'
import ResultsList from './testlistview/ResultsList.vue'
import ConfirmDeleteModal from './testlistview/ConfirmDeleteModal.vue'

const { t } = useI18n()
const router = useRouter()
import { StationPermission } from '@/api/types'
import { useConfirmAction } from '@/composables/useConfirmAction'
const { hasPermission, loaded } = useSession()
const canConfigure = () => hasPermission(StationPermission.TEST_CONFIGURE)
const canReadResults = () => hasPermission(StationPermission.TEST_RESULT_READ)
const { isMobile } = useBreakpoint()

const activeTab = ref('tests')
const tabs = computed(() => {
  const t_ = [{ key: 'tests', label: t('quiz.tests.tabTests') }]
  if (canReadResults()) t_.push({ key: 'results', label: t('quiz.tests.tabResults') })
  return t_
})

const testSummaries = ref<QuizTestSummary[]>([])
const tests = ref<QuizTest[]>([])
const availableTests = ref<QuizAvailableTest[]>([])

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

function testClickRoute(test: QuizTest) {
  if (canReadResults()) return { name: 'quiz-test-detail', params: { id: test.id } }
  return { name: 'quiz-test-take', params: { id: test.id } }
}

function attemptCount(test: QuizTest): number {
  const summary = testSummaries.value.find(s => s.test.id === test.id)
  return summary?.attemptCount ?? 0
}

function attemptStatus(test: QuizTest): string | null {
  const available = availableTests.value.find(a => a.test.id === test.id)
  return available?.attemptStatus ?? null
}

function isSubmitted(test: QuizTest): boolean {
  const status = attemptStatus(test)
  return status === 'SUBMITTED' || status === 'GRADED'
}

const { loading, error, reload: loadData } = useAsyncLoader(async () => {
  if (canReadResults()) {
    testSummaries.value = await quiz.listTests()
    tests.value = testSummaries.value.map(s => s.test)
    availableTests.value = []
  } else {
    testSummaries.value = []
    availableTests.value = await quiz.listAvailableTests()
    tests.value = availableTests.value.map(a => a.test)
  }
}, { autoLoad: false })

function deleteTest(test: QuizTest) {
  showConfirm(t('quiz.tests.confirmDelete'), async () => {
    await quiz.deleteTest(test.id)
    await loadData()
  })
}

function attemptStartedAt(test: QuizTest): string | null {
  const available = availableTests.value.find(a => a.test.id === test.id)
  return available?.startedAt ?? null
}

function attemptSubmittedAt(test: QuizTest): string | null {
  const available = availableTests.value.find(a => a.test.id === test.id)
  return available?.submittedAt ?? null
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
</script>

<template>
  <ViewContent :title="t('pages.quiz-tests.title')" :subtitle="t('pages.quiz-tests.subtitle')">
    <div class="space-y-6">
      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <div class="flex items-center justify-end">
          <PrimaryButton :icon="['fas', 'plus']" v-if="canConfigure()" @click="router.push({ name: 'quiz-test-create' })">
            {{ t('quiz.tests.create') }}
          </PrimaryButton>
        </div>

        <TabBar v-if="tabs.length > 1" v-model="activeTab" :tabs="tabs" />

        <EmptyState v-if="tests.length === 0">{{ t('quiz.tests.noTests') }}</EmptyState>

        <TestList
          v-if="activeTab === 'tests'"
          :tests="tests"
          :is-mobile="isMobile"
          :can-configure="canConfigure()"
          :can-read-results="canReadResults()"
          :submitted-for="isSubmitted"
          :attempt-count-for="attemptCount"
          :attempt-started-at-for="attemptStartedAt"
          :attempt-submitted-at-for="attemptSubmittedAt"
          @navigate="(test) => router.push(testClickRoute(test))"
          @take="(test) => router.push({ name: 'quiz-test-take', params: { id: test.id } })"
          @edit="(test) => router.push({ name: 'quiz-test-edit', params: { id: test.id } })"
          @remove="deleteTest"
        />

        <ResultsList
          v-if="activeTab === 'results'"
          :summaries="testSummaries"
          @open="(summary) => router.push({ name: 'quiz-test-detail', params: { id: summary.test.id } })"
        />
      </template>

      <ConfirmDeleteModal
        v-model="confirmAction.show.value"
        :message="confirmAction.target.value?.message ?? ''"
        @confirm="confirmAction.confirm"
      />
    </div>
  </ViewContent>
</template>
