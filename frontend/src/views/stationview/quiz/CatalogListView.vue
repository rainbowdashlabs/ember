/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SearchInput from '@/components/input/text/SearchInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type { QuizCatalog, QuizCatalogExport } from '@/api/types'
import type { SharedCatalogEntry } from '@/api/quiz'
import { quiz, federation } from '@/api'
import { useSession } from '@/composables/useSession'
import { useBreakpoint } from '@/composables/useBreakpoint'
import FieldLabel from '@/components/typography/FieldLabel.vue'

const { t } = useI18n()
const router = useRouter()
const { loaded } = useSession()
const { isMobile } = useBreakpoint()

const catalogs = ref<QuizCatalog[]>([])
const sharedCatalogs = ref<SharedCatalogEntry[]>([])
const loading = ref(true)
const error = ref('')

// Search
const searchQuery = ref('')

// Filters
const showFederated = ref(true)
const filterStationId = ref<string | null>(null)

// Unique partner stations from shared catalogs
const partnerStations = computed(() => {
  const map = new Map<string, string>()
  for (const s of sharedCatalogs.value) {
    map.set(s.sourceStationId, s.stationName)
  }
  return [...map.entries()].map(([id, name]) => ({ id, name }))
})

// Filtered local catalogs by search
const filteredCatalogs = computed(() => {
  if (!searchQuery.value.trim()) return catalogs.value
  const lower = searchQuery.value.toLowerCase()
  return catalogs.value.filter(
    c => c.name.toLowerCase().includes(lower) || (c.description && c.description.toLowerCase().includes(lower)),
  )
})

// Filtered shared catalogs
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

// Create modal
const showCreateModal = ref(false)
const createName = ref('')
const createDescription = ref('')
const createTrainingEnabled = ref(false)

// Delete modal
const showDeleteModal = ref(false)
const catalogToDelete = ref<QuizCatalog | null>(null)

// Import
const fileInput = ref<HTMLInputElement | null>(null)

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const response = await quiz.listCatalogs()
    // Backend may return wrapped {catalogs, sharedCatalogs} or plain array
    if (Array.isArray(response)) {
      catalogs.value = response as unknown as typeof catalogs.value
      sharedCatalogs.value = []
    } else {
      catalogs.value = response.catalogs ?? []
      sharedCatalogs.value = (response.sharedCatalogs ?? []).filter(s => s.catalog != null)
    }
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

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

function confirmDelete(catalog: QuizCatalog) {
  catalogToDelete.value = catalog
  showDeleteModal.value = true
}

async function deleteCatalog() {
  if (!catalogToDelete.value) return
  error.value = ''
  try {
    await quiz.deleteCatalog(catalogToDelete.value.id)
    showDeleteModal.value = false
    catalogToDelete.value = null
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
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${catalog.name.replace(/\s+/g, '_')}.json`
    a.click()
    URL.revokeObjectURL(url)
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
      <div class="flex items-center justify-between flex-wrap gap-2">
        <PageHeader>{{ t('quiz.catalogs.title') }}</PageHeader>
        <div class="flex gap-2 flex-wrap">
          <SecondaryButton :icon="['fas', 'file-import']" @click="triggerImport">
            {{ t('quiz.catalogs.import') }}
          </SecondaryButton>
          <PrimaryButton :icon="['fas', 'plus']" @click="openCreateModal">
            {{ t('quiz.catalogs.create') }}
          </PrimaryButton>
        </div>
      </div>

      <input ref="fileInput" type="file" accept=".json" class="hidden" @change="handleImportFile" />

      <!-- Search -->
      <div>
        <SearchInput
          v-model="searchQuery"
          :placeholder="t('quiz.catalogs.search')"
        />
      </div>

      <!-- Filters -->
      <div class="flex flex-wrap items-center gap-2">
        <SelectionToggleButton :selected="showFederated" @toggle="showFederated = !showFederated">
          <font-awesome-icon :icon="['fas', 'arrow-right-arrow-left']" class="w-3 h-3 mr-1" />
          {{ t('federation.shared') }}
        </SelectionToggleButton>
        <SelectInput
          v-if="showFederated && partnerStations.length > 0"
          :model-value="filterStationId != null ? String(filterStationId) : ''"
          class="!w-auto !text-xs !py-1"
          @update:model-value="(v: string | undefined) => { filterStationId = v || null }"
        >
          <option value="">{{ t('quiz.catalogs.allStations') }}</option>
          <option v-for="station in partnerStations" :key="station.id" :value="String(station.id)">
            {{ station.name }}
          </option>
        </SelectInput>
      </div>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <EmptyState v-if="filteredCatalogs.length === 0 && filteredSharedCatalogs.length === 0">{{ t('quiz.catalogs.noCatalogs') }}</EmptyState>

        <div class="space-y-2">
          <!-- Local catalogs -->
          <NeutralContainer
            v-for="catalog in filteredCatalogs"
            :key="'local-' + catalog.id"
            class="cursor-pointer hover:border-primary transition-colors"
            @click="navigateToCatalog(catalog)"
          >
            <div v-if="isMobile" class="space-y-2">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">{{ catalog.name }}</span>
                <SuccessBadge v-if="catalog.trainingEnabled">{{ t('quiz.catalogs.trainingEnabled') }}</SuccessBadge>
              </div>
              <p v-if="catalog.description" class="text-xs text-(--text-muted)">{{ catalog.description }}</p>
              <div class="flex items-center justify-between border-t border-bg-light-accent dark:border-bg-dark-accent pt-2 mt-2">
                <span class="text-xs text-(--text-muted)">{{ t('quiz.catalogs.questionCount') }}</span>
                <div class="flex items-center gap-2" @click.stop>
                  <IconButton :icon="['fas', 'file-export']" :label="t('quiz.catalogs.export')" class="text-(--text-muted) hover:text-primary" @click="exportCatalog(catalog)" />
                  <DeleteButton @click="confirmDelete(catalog)" />
                </div>
              </div>
            </div>

            <div v-else class="flex items-center justify-between gap-4">
              <div class="flex-1 min-w-0 space-y-1">
                <div class="flex items-center gap-2 flex-wrap">
                  <span class="font-medium">{{ catalog.name }}</span>
                  <SuccessBadge v-if="catalog.trainingEnabled">{{ t('quiz.catalogs.trainingEnabled') }}</SuccessBadge>
                </div>
                <p v-if="catalog.description" class="text-xs text-(--text-muted) truncate">{{ catalog.description }}</p>
              </div>
              <div class="flex items-center gap-2 shrink-0" @click.stop>
                <IconButton :icon="['fas', 'file-export']" :label="t('quiz.catalogs.export')" class="text-(--text-muted) hover:text-primary" @click="exportCatalog(catalog)" />
                <DeleteButton @click="confirmDelete(catalog)" />
              </div>
            </div>
          </NeutralContainer>

          <!-- Shared catalogs (from partner stations) -->
          <NeutralContainer
            v-for="shared in filteredSharedCatalogs"
            :key="'shared-' + shared.catalog.id"
            class="cursor-pointer hover:border-primary transition-colors"
            @click="navigateToCatalog(shared.catalog)"
          >
            <div v-if="isMobile" class="space-y-2">
              <div class="flex items-center gap-2 flex-wrap">
                <span class="font-medium">{{ shared.catalog.name }}</span>
                <StationBadge :station-name="shared.stationName" />
                <SuccessBadge v-if="shared.catalog.trainingEnabled">{{ t('quiz.catalogs.trainingEnabled') }}</SuccessBadge>
              </div>
              <p v-if="shared.catalog.description" class="text-xs text-(--text-muted)">{{ shared.catalog.description }}</p>
              <div class="flex items-center justify-end border-t border-bg-light-accent dark:border-bg-dark-accent pt-2 mt-2">
                <IconButton
                  :icon="['fas', 'copy']"
                  :label="t('federation.copyToStation')"
                  @click.stop="copySharedCatalog(shared.catalog.id)"
                />
              </div>
            </div>

            <div v-else class="flex items-center justify-between gap-4">
              <div class="flex-1 min-w-0 space-y-1">
                <div class="flex items-center gap-2 flex-wrap">
                  <span class="font-medium">{{ shared.catalog.name }}</span>
                  <StationBadge :station-name="shared.stationName" />
                  <SuccessBadge v-if="shared.catalog.trainingEnabled">{{ t('quiz.catalogs.trainingEnabled') }}</SuccessBadge>
                </div>
                <p v-if="shared.catalog.description" class="text-xs text-(--text-muted) truncate">{{ shared.catalog.description }}</p>
              </div>
              <div class="flex items-center gap-2 shrink-0" @click.stop>
                <IconButton
                  :icon="['fas', 'copy']"
                  :label="t('federation.copyToStation')"
                  class="text-(--text-muted) hover:text-primary"
                  @click="copySharedCatalog(shared.catalog.id)"
                />
              </div>
            </div>
          </NeutralContainer>
        </div>
      </template>

      <!-- Create Modal -->
      <Modal v-model="showCreateModal">
        <div class="space-y-4">
          <SubHeader>{{ t('quiz.catalogs.create') }}</SubHeader>
          <TextInput v-model="createName" :placeholder="t('quiz.catalogs.name')" />
          <TextAreaInput v-model="createDescription" :placeholder="t('quiz.catalogs.description')" />
          <FieldLabel inline>
            <ToggleInput v-model="createTrainingEnabled" />
            {{ t('quiz.catalogs.trainingEnabled') }}
          </FieldLabel>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showCreateModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="!createName.trim()" @click="createCatalog">{{ t('common.save') }}</PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Delete Confirmation Modal -->
      <Modal v-model="showDeleteModal">
        <div class="space-y-4">
          <SubHeader>{{ t('quiz.catalogs.deleteCatalog') }}</SubHeader>
          <p class="text-sm">{{ t('quiz.catalogs.deleteConfirm') }}</p>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showDeleteModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton @click="deleteCatalog">{{ t('common.confirm') }}</PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
