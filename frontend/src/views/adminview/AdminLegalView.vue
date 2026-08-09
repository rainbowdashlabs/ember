/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, onMounted, watch, computed} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TypeTabsBar from './adminlegalview/TypeTabsBar.vue'
import LocaleTabsBar from './adminlegalview/LocaleTabsBar.vue'
import FileListPanel from './adminlegalview/FileListPanel.vue'
import SingleFieldModal from '@/components/feedback/SingleFieldModal.vue'
import DeleteFileModal from './adminlegalview/DeleteFileModal.vue'
import {adminSettings} from '@/api'
import type {LegalFile} from '@/api/adminSettings'

const {t} = useI18n()

const error = ref('')

const legalTypes = ['privacy', 'tos', 'consent', 'imprint'] as const
type LegalType = (typeof legalTypes)[number]
const activeLegalTab = ref<LegalType>('privacy')
const activeLocale = ref('de')
const locales = ref<string[]>([])

const files = ref<LegalFile[]>([])
const loading = ref(false)
const showPreview = ref(false)

const showAddLocaleModal = ref(false)
const newLocaleCode = ref('')
const showAddFileModal = ref(false)
const newFileName = ref('')
const showDeleteFileModal = ref(false)
const fileToDeleteIndex = ref(-1)

const fileToDeleteName = computed(() => {
  if (fileToDeleteIndex.value < 0) return ''
  const file = files.value[fileToDeleteIndex.value]
  return file?.displayName || file?.filename || ''
})

async function loadLocales(type: LegalType) {
  try {
    const result = await adminSettings.getLegalLocales(type)
    locales.value = Array.isArray(result) && result.length > 0 ? result : ['de']
    if (!locales.value.includes(activeLocale.value)) {
      activeLocale.value = locales.value[0] ?? 'de'
    }
  } catch {
    locales.value = ['de']
  }
}

async function loadFiles(type: LegalType, locale: string) {
  loading.value = true
  error.value = ''
  showPreview.value = false
  try {
    const result = await adminSettings.getLegalFiles(type, locale)
    files.value = Array.isArray(result) ? result : []
  } catch {
    files.value = []
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

async function saveAll() {
  error.value = ''
  try {
    files.value = await adminSettings.saveLegalFiles(activeLegalTab.value, activeLocale.value, files.value)
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

async function addLocale() {
  const code = newLocaleCode.value.trim().toLowerCase()
  if (!code || locales.value.includes(code)) return
  showAddLocaleModal.value = false
  newLocaleCode.value = ''
  try {
    await adminSettings.saveLegalFiles(activeLegalTab.value, code, [{
      filename: '', displayName: 'content', content: '', enabled: true,
    }])
    await loadLocales(activeLegalTab.value)
    activeLocale.value = code
    await loadFiles(activeLegalTab.value, code)
  } catch {
    error.value = t('common.error')
  }
}

function addFile() {
  let name = newFileName.value.trim()
  if (!name) return
  name = name.replace(/\.md$/, '')
  showAddFileModal.value = false
  newFileName.value = ''
  files.value = [...files.value, {filename: '', displayName: name, content: '', enabled: true}]
}

function confirmDeleteFile(index: number) {
  fileToDeleteIndex.value = index
  showDeleteFileModal.value = true
}

function deleteFile() {
  if (fileToDeleteIndex.value < 0) return
  files.value = files.value.filter((_, i) => i !== fileToDeleteIndex.value)
  showDeleteFileModal.value = false
  fileToDeleteIndex.value = -1
}

watch(activeLegalTab, async (type) => {
  await loadLocales(type)
  await loadFiles(type, activeLocale.value)
})

watch(activeLocale, (locale) => {
  loadFiles(activeLegalTab.value, locale)
})

onMounted(async () => {
  await loadLocales(activeLegalTab.value)
  await loadFiles(activeLegalTab.value, activeLocale.value)
})
</script>

<template>
  <ViewContent :title="t('pages.admin-legal.title')" :subtitle="t('pages.admin-legal.subtitle')">
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer class="space-y-4">
        <TypeTabsBar v-model="activeLegalTab" :types="legalTypes"/>
        <LocaleTabsBar
            v-model="activeLocale"
            :locales="locales"
            @add="showAddLocaleModal = true"
        />
        <Spinner v-if="loading" size="md"/>
        <FileListPanel
            v-if="!loading"
            v-model:files="files"
            v-model:show-preview="showPreview"
            :save-action="saveAll"
            @add-file="showAddFileModal = true"
            @delete-file="confirmDeleteFile"
        />
      </NeutralContainer>

      <SingleFieldModal
          v-model:show="showAddLocaleModal"
          v-model:value="newLocaleCode"
          :title="t('adminSettings.legal.addLocaleTitle')"
          :placeholder="t('adminSettings.legal.localeCodePlaceholder')"
          :confirm-label="t('adminSettings.legal.addLocale')"
          @confirm="addLocale"
      />
      <SingleFieldModal
          v-model:show="showAddFileModal"
          v-model:value="newFileName"
          :title="t('adminSettings.legal.addFileTitle')"
          :placeholder="t('adminSettings.legal.fileNamePlaceholder')"
          :confirm-label="t('adminSettings.legal.addFile')"
          @confirm="addFile"
      />
      <DeleteFileModal
          v-model:show="showDeleteFileModal"
          :display-name="fileToDeleteName"
          @confirm="deleteFile"
      />
    </div>
  </ViewContent>
</template>
