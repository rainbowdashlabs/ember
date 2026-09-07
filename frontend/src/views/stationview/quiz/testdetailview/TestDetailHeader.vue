/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SuccessButton from '@/components/button/SuccessButton.vue'
import ActionsMenu from '@/components/button/ActionsMenu.vue'
import DropdownMenuItem from '@/components/button/DropdownMenuItem.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import {QuizTestStatus, type QuizTestDetail} from '@/api/quiz'
import { quiz } from '@/api'

const props = defineProps<{
  test: QuizTestDetail['test']
  detail: QuizTestDetail | null
  canConfigure: boolean
  canReadResults: boolean
}>()

const emit = defineEmits<{
  activate: []
  close: []
}>()

const { t } = useI18n()
const router = useRouter()

function take() {
  router.push({ name: 'quiz-test-take', params: { id: props.test.id } })
}

function edit() {
  router.push({ name: 'quiz-test-edit', params: { id: props.test.id } })
}
</script>

<template>
  <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
    <div class="space-y-1">
      <div class="flex items-center gap-2 flex-wrap">
        <SubHeader>{{ test.title }}</SubHeader>
        <SuccessBadge v-if="test.status === QuizTestStatus.ACTIVE">{{ t('quiz.tests.statusActive') }}</SuccessBadge>
        <ErrorBadge v-else-if="test.status === QuizTestStatus.CLOSED">{{ t('quiz.tests.statusClosed') }}</ErrorBadge>
        <SecondaryBadge v-else>{{ t('quiz.tests.statusDraft') }}</SecondaryBadge>
      </div>
      <p v-if="test.description" class="text-sm text-(--text-muted)">{{ test.description }}</p>
      <p v-if="canReadResults && detail" class="text-sm text-(--text-muted)">{{ detail.attemptCount }} {{ t('quiz.attemptCount') }}</p>
    </div>
    <!--
      A test is opened to be written while it runs and to be started while it is a draft, so that
      is the button that stays. The two exports look alike side by side and belong in a list where
      their names can be read, and closing the test comes last and coloured: it is behind a
      confirmation either way.
    -->
    <div class="flex items-center gap-2 flex-wrap">
      <PrimaryButton :icon="['fas', 'play']" v-if="test.status === QuizTestStatus.ACTIVE" @click="take">
        {{ t('quiz.tests.takeTest') }}
      </PrimaryButton>
      <template v-if="canConfigure">
        <SuccessButton v-if="test.status === QuizTestStatus.DRAFT" @click="emit('activate')">
          {{ t('quiz.tests.activate') }}
        </SuccessButton>
        <ActionsMenu :label="t('common.actions')" test-id="test-actions">
          <DropdownMenuItem v-if="test.status === QuizTestStatus.DRAFT" :icon="['fas', 'pen']" @click="edit">
            {{ t('common.edit') }}
          </DropdownMenuItem>
          <DropdownMenuItem :icon="['fas', 'file-lines']" @click="quiz.downloadQuestionPdf(test.id)">
            {{ t('quiz.tests.exportQuestions') }}
          </DropdownMenuItem>
          <DropdownMenuItem :icon="['fas', 'file-lines']" @click="quiz.downloadSolutionPdf(test.id)">
            {{ t('quiz.tests.exportSolutions') }}
          </DropdownMenuItem>
          <DropdownMenuItem v-if="test.status === QuizTestStatus.ACTIVE" :icon="['fas', 'ban']" destructive
                            @click="emit('close')">
            {{ t('quiz.tests.close') }}
          </DropdownMenuItem>
        </ActionsMenu>
      </template>
    </div>
  </div>
</template>
