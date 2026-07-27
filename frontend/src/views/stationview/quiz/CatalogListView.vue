/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import type { QuizCatalog, QuizCatalogExport } from '@/api/types'
import type { SharedCatalogEntry } from '@/api/quiz'
import { quiz, federation } from '@/api'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useConfirmDelete } from '@/composables/useConfirmDelete'
import { saveBlob } from '@/util/downloadAuthed'
import CatalogToolbar from './cataloglistview/CatalogToolbar.vue'
import CatalogList from './cataloglistview/CatalogList.vue'
import CreateCatalogModal from './cataloglistview/CreateCatalogModal.vue'
import DeleteCatalogModal from './cataloglistview/DeleteCatalogModal.vue'

const { t } = useI18n()
const router = useRouter()
const { loaded } = useSession()
const { isMobile } = useBreakpoint()

const catalogs = ref<QuizCatalog[]>([])
const sharedCatalogs = ref<SharedCatalogEntry[]>([])

const { loading, error, reload: loadData } = useAsyncLoader(async () => {
  const response = await quiz.listCatalogs()
  if (Array.isArray(response)) {
    catalogs.value = response as unknown as typeof catalogs.value
    sharedCatalogs.value = []
  } else {
    catalogs.value = response.catalogs ?? []
    sharedCatalogs.value = (response.sharedCatalogs ?? []).filter(s => s.catalog != null)
  }
}, { autoLoad: false })

const searchQuery = ref('')

const showFederated = ref(true)
const filterStationId = ref<string | null>(null)

const partnerStations = computed(() => {
  const map = new Map<string, string>()
  for (const s of sharedCatalogs.value) {
    map.set(s.sourceStationId, s.stationName)
  }
  return [...map.entries()].map(([id, name]) => ({ id, name }))
})

const filteredCatalogs = computed(() => {
  if (!searchQuery.value.trim()) return catalogs.value
  const lower = searchQuery.value.toLowerCase()
  return catalogs.value.filter(
    c => c.name.toLowerCase().includes(lower) || (c.description && c.description.toLowerCase().includes(lower)),
  )
})

const filteredSharedCatalogs = computed(() => {
  if (!showFederated.value) return []
  let result = sharedCatalogs.value
  if (filterStationId.value != null) {
    result = result.filter(s => s.sourceStationId === filterStationId.value)
  }
  if (searchQuery.value.trim()) {
    const lower = searchQuery.value.toLowerCase()
    result = result.filter(
      s =>
        s.catalog.name.toLowerCase().includes(lower) ||
        (s.catalog.description && s.catalog.description.toLowerCase().includes(lower)),
    )
  }
  return result
})

const showCreateModal = ref(false)
const createName = ref('')
const createDescription = ref('')
const createTrainingEnabled = ref(false)

const {
  show: showDeleteModal,
  target: catalogToDelete,
  requestDelete: confirmDelete,
  confirm: deleteCatalog,
} = useConfirmDelete<QuizCatalog>({
  onDelete: c => quiz.deleteCatalog(c.id),
  onSuccess: () => loadData(),
  error,
})

const fileInput = ref<HTMLInputElement | null>(null)

function openCreateModal() {
  createName.value = ''
  createDescription.value = ''
  createTrainingEnabled.value = false
  showCreateModal.value = true
}

async function createCatalog() {
  if (!createName.value.trim()) return
  error.value = ''
  try {
    await quiz.createCatalog({
      name: createName.value.trim(),
      description: createDescription.value.trim(),
      trainingEnabled: createTrainingEnabled.value,
    })
    showCreateModal.value = false
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

async function exportCatalog(catalog: QuizCatalog) {
  error.value = ''
  try {
    const data = await quiz.exportCatalog(catalog.id)
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
    saveBlob(blob, `${catalog.name.replace(/\s+/g, '_')}.json`)
  } catch {
    error.value = t('common.error')
  }
}

function triggerImport() {
  fileInput.value?.click()
}

async function handleImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  error.value = ''
  try {
    const text = await file.text()
    const data: QuizCatalogExport = JSON.parse(text)
    await quiz.importCatalog(data)
    await loadData()
  } catch {
    error.value = t('common.error')
  } finally {
    input.value = ''
  }
}

async function copySharedCatalog(catalogId: number) {
  error.value = ''
  try {
    await federation.copyQuizCatalog(catalogId)
    await loadData()
  } catch {
    error.value = t('common.error')
  }
}

function navigateToCatalog(catalog: QuizCatalog) {
  router.push({ name: 'quiz-catalog-detail', params: { id: catalog.id } })
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
</script>

<template>
  <ViewContent :title="t('pages.quiz-catalogs.title')" :subtitle="t('pages.quiz-catalogs.subtitle')">
    <div class="space-y-6">
      <CatalogToolbar
        v-model:search-query="searchQuery"
        v-model:show-federated="showFederated"
        v-model:filter-station-id="filterStationId"
        :partner-stations="partnerStations"
        @open-create-modal="openCreateModal"
        @trigger-import="triggerImport"
      />

      <input ref="fileInput" type="file" accept=".json" class="hidden" @change="handleImportFile" />

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <EmptyState v-if="filteredCatalogs.length === 0 && filteredSharedCatalogs.length === 0">{{ t('quiz.catalogs.noCatalogs') }}</EmptyState>

        <CatalogList
          :catalogs="filteredCatalogs"
          :shared-catalogs="filteredSharedCatalogs"
          :is-mobile="isMobile"
          @navigate="navigateToCatalog"
          @export-catalog="exportCatalog"
          @confirm-delete="confirmDelete"
          @copy-shared="copySharedCatalog"
        />
      </template>

      <CreateCatalogModal
        v-model="showCreateModal"
        v-model:name="createName"
        v-model:description="createDescription"
        v-model:training-enabled="createTrainingEnabled"
        @submit="createCatalog"
      />

      <DeleteCatalogModal
        v-model="showDeleteModal"
        @confirm="deleteCatalog"
      />
    </div>
  </ViewContent>
</template>
