/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import CatalogHeader from './catalogdetailview/CatalogHeader.vue'
import CategorySection from './catalogdetailview/CategorySection.vue'
import QuestionSection from './catalogdetailview/QuestionSection.vue'
import CsvImportDialog from './CsvImportDialog.vue'
import type { QuizCatalogDetail, QuizQuestion } from '@/api/types'
import { quiz, federation, storage } from '@/api'
import { useSession } from '@/composables/useSession'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { loaded } = useSession()

const catalogId = computed(() => Number(route.params.id))

const currentStationId = computed(() => {
  return storage.getItem('station_id') ?? null
})

const isFederated = computed(() => {
  if (!catalog.value || currentStationId.value == null) return false
  return catalog.value.stationId !== currentStationId.value
})

const catalog = ref<QuizCatalogDetail | null>(null)
const questions = ref<QuizQuestion[]>([])
const loading = ref(true)
const error = ref('')

// CSV import
const showCsvImport = ref(false)

const catalogHeaderRef = ref<InstanceType<typeof CatalogHeader> | null>(null)

// --- Data Loading ---

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [catalogData, questionsData] = await Promise.all([
      quiz.getCatalog(catalogId.value),
      quiz.listQuestions(catalogId.value),
    ])
    catalog.value = catalogData
    questions.value = questionsData
    catalogHeaderRef.value?.resetForm()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

// --- Catalog Update ---

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

async function onCsvImported(_count: number) {
  showCsvImport.value = false
  await loadData()
}

function onError(message: string) {
  error.value = message
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
          @save="saveCatalog"
          @copy-to-station="copyToStation"
        />

        <CategorySection
          v-if="!isFederated"
          :categories="catalog.categories"
          @updated="loadData"
        />

        <QuestionSection
          :catalog-id="catalogId"
          :questions="questions"
          :categories="catalog.categories"
          :is-federated="isFederated"
          @updated="loadData"
          @error="onError"
        />
      </template>

      <!-- CSV Import Dialog -->
      <CsvImportDialog
        :show="showCsvImport"
        :catalog-id="catalogId"
        :categories="catalog?.categories ?? []"
        @update:show="showCsvImport = $event"
        @imported="onCsvImported"
      />
    </div>
  </ViewContent>
</template>
