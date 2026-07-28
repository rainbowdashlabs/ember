/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CatalogHeader from './catalogdetailview/CatalogHeader.vue'
import CategorySection from './catalogdetailview/CategorySection.vue'
import QuestionSection from './catalogdetailview/QuestionSection.vue'
import type { QuizCatalogDetail, QuizQuestion } from '@/api/quiz'
import { quiz, federation, storage } from '@/api'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
import { StationPermission } from '@/api/types'
const { hasPermission, loaded } = useSession()

const catalogId = computed(() => Number(route.params.id))
const canEdit = computed(() => hasPermission(StationPermission.TEST_CATALOG_EDIT))

const currentStationId = computed(() => {
  return storage.getItem('station_id') ?? null
})

const isFederated = computed(() => {
  if (!catalog.value || currentStationId.value == null) return false
  return catalog.value.stationId !== currentStationId.value
})

const readonly = computed(() => isFederated.value || !canEdit.value)

const catalog = ref<QuizCatalogDetail | null>(null)
const questions = ref<QuizQuestion[]>([])

const catalogHeaderRef = ref<InstanceType<typeof CatalogHeader> | null>(null)

const { loading, error, reload: loadData } = useAsyncLoader(async () => {
  const [catalogData, questionsData] = await Promise.all([
    quiz.getCatalog(catalogId.value),
    quiz.listQuestions(catalogId.value),
  ])
  catalog.value = catalogData
  questions.value = questionsData
  catalogHeaderRef.value?.resetForm()
}, { autoLoad: false })

async function saveCatalog(payload: { name: string; description: string; trainingEnabled: boolean }) {
  error.value = ''
  try {
    await quiz.updateCatalog(catalogId.value, payload)
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

async function copyToStation() {
  error.value = ''
  try {
    await federation.copyQuizCatalog(catalogId.value)
    router.push({ name: 'quiz-catalogs' })
  } catch {
    error.value = t('common.error')
  }
}

function onError(message: string) {
  error.value = message
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
</script>

<template>
  <ViewContent :title="t('pages.quiz-catalog-detail.title')" :subtitle="t('pages.quiz-catalog-detail.subtitle')">
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']" @click="router.push({ name: 'quiz-catalogs' })">
        {{ t('common.back') }}
      </SecondaryButton>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading && catalog">
        <CatalogHeader
          ref="catalogHeaderRef"
          :catalog="catalog"
          :is-federated="isFederated"
          :readonly="readonly"
          @save="saveCatalog"
          @copy-to-station="copyToStation"
        />

        <CategorySection
          v-if="!readonly"
          :categories="catalog.categories"
          @updated="loadData"
        />

        <QuestionSection
          :catalog-id="catalogId"
          :questions="questions"
          :categories="catalog.categories"
          :is-federated="isFederated"
          :readonly="readonly"
          @updated="loadData"
          @error="onError"
        />
      </template>
    </div>
  </ViewContent>
</template>
