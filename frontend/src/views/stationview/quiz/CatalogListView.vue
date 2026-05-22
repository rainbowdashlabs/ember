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
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import type { QuizCatalog, QuizCatalogExport } from '@/api/types'
import { quiz } from '@/api'
import { useSession } from '@/composables/useSession'
import { useBreakpoint } from '@/composables/useBreakpoint'

const { t } = useI18n()
const router = useRouter()
const { loaded } = useSession()
const { isMobile } = useBreakpoint()

const catalogs = ref<QuizCatalog[]>([])
const loading = ref(true)
const error = ref('')

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
    catalogs.value = await quiz.listCatalogs()
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
      <div class="flex items-center justify-between flex-wrap gap-3">
        <PageHeader>{{ t('quiz.catalogs.title') }}</PageHeader>
        <div class="flex gap-2 flex-wrap">
          <SecondaryButton @click="triggerImport">
            <font-awesome-icon :icon="['fas', 'file-import']" class="mr-1" />
            {{ t('quiz.catalogs.import') }}
          </SecondaryButton>
          <PrimaryButton @click="openCreateModal">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" />
            {{ t('quiz.catalogs.create') }}
          </PrimaryButton>
        </div>
      </div>

      <input ref="fileInput" type="file" accept=".json" class="hidden" @change="handleImportFile" />

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <template v-if="!loading">
        <div v-if="catalogs.length === 0" class="text-center text-(--text-muted) py-8">
          {{ t('quiz.catalogs.noCatalogs') }}
        </div>

        <div class="space-y-2">
          <NeutralContainer
            v-for="catalog in catalogs"
            :key="catalog.id"
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
              <div class="flex items-center gap-3 shrink-0" @click.stop>
                <IconButton :icon="['fas', 'file-export']" :label="t('quiz.catalogs.export')" class="text-(--text-muted) hover:text-primary" @click="exportCatalog(catalog)" />
                <DeleteButton @click="confirmDelete(catalog)" />
              </div>
            </div>
          </NeutralContainer>
        </div>
      </template>

      <!-- Create Modal -->
      <Modal v-model="showCreateModal">
        <div class="space-y-4">
          <h3 class="text-lg font-semibold">{{ t('quiz.catalogs.create') }}</h3>
          <TextInput v-model="createName" :placeholder="t('quiz.catalogs.name')" />
          <TextAreaInput v-model="createDescription" :placeholder="t('quiz.catalogs.description')" />
          <label class="flex items-center gap-2 text-sm">
            <ToggleInput v-model="createTrainingEnabled" />
            {{ t('quiz.catalogs.trainingEnabled') }}
          </label>
          <div class="flex justify-end gap-3">
            <SecondaryButton @click="showCreateModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="!createName.trim()" @click="createCatalog">{{ t('common.save') }}</PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Delete Confirmation Modal -->
      <Modal v-model="showDeleteModal">
        <div class="space-y-4">
          <h3 class="text-lg font-semibold">{{ t('quiz.catalogs.deleteCatalog') }}</h3>
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
