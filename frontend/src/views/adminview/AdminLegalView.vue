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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import Modal from '@/components/feedback/Modal.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import {adminSettings} from '@/api'
import type {LegalFile} from '@/api/adminSettings'
import {marked} from 'marked'

const {t} = useI18n()

const error = ref('')
const success = ref('')

// -- Type & locale selection --
const legalTypes = ['privacy', 'tos', 'consent', 'imprint'] as const
type LegalType = (typeof legalTypes)[number]
const activeLegalTab = ref<LegalType>('privacy')
const activeLocale = ref('de')
const locales = ref<string[]>([])

// -- File management --
const files = ref<LegalFile[]>([])
const loading = ref(false)
const saving = ref(false)
const showPreview = ref(false)

// -- Modals --
const showAddLocaleModal = ref(false)
const newLocaleCode = ref('')
const showAddFileModal = ref(false)
const newFileName = ref('')
const showDeleteFileModal = ref(false)
const fileToDeleteIndex = ref(-1)

const renderedPreview = computed(() => {
  const enabledContent = files.value.filter(f => f.enabled).map(f => f.content).join('\n\n')
  if (!enabledContent) return ''
  return marked.parse(enabledContent) as string
})

// -- Loading --

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

// -- Saving --

async function saveAll() {
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    files.value = await adminSettings.saveLegalFiles(activeLegalTab.value, activeLocale.value, files.value)
    success.value = t('adminSettings.saved')
    setTimeout(() => { success.value = '' }, 3000)
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

// -- Reorder --

function moveUp(index: number) {
  if (index <= 0) return
  const arr = [...files.value]
  ;[arr[index - 1], arr[index]] = [arr[index], arr[index - 1]]
  files.value = arr
}

function moveDown(index: number) {
  if (index >= files.value.length - 1) return
  const arr = [...files.value]
  ;[arr[index], arr[index + 1]] = [arr[index + 1], arr[index]]
  files.value = arr
}

// -- Toggle --

function toggleEnabled(index: number) {
  files.value[index] = {...files.value[index], enabled: !files.value[index].enabled}
}

// -- Add locale --

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

// -- Add file --

function addFile() {
  let name = newFileName.value.trim()
  if (!name) return
  name = name.replace(/\.md$/, '')
  showAddFileModal.value = false
  newFileName.value = ''
  files.value = [...files.value, {filename: '', displayName: name, content: '', enabled: true}]
}

// -- Delete file --

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

// -- Watchers --

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
  <ViewContent>
    <div class="space-y-6">
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('adminSettings.legal.title') }}</SectionHeader>

        <!-- Document type tabs -->
        <div class="flex gap-2 flex-wrap">
          <SecondaryButton
              v-for="type in legalTypes"
              :key="type"
              :class="{ 'ring-2 ring-primary': activeLegalTab === type }"
              @click="activeLegalTab = type"
          >
            {{ t(`adminSettings.legal.${type}`) }}
          </SecondaryButton>
        </div>

        <!-- Locale tabs -->
        <div class="flex items-center gap-2 flex-wrap">
          <SecondaryButton
              v-for="locale in locales"
              :key="locale"
              :class="{ 'ring-2 ring-secondary': activeLocale === locale }"
              @click="activeLocale = locale"
          >
            {{ locale.toUpperCase() }}
          </SecondaryButton>
          <SecondaryButton :icon="['fas', 'plus']" @click="showAddLocaleModal = true">
            {{ t('adminSettings.legal.addLocale') }}
          </SecondaryButton>
        </div>

        <Spinner v-if="loading" size="md"/>

        <template v-if="!loading">
          <!-- Preview toggle + save -->
          <div class="flex items-center justify-between">
            <SecondaryButton :icon="['fas', showPreview ? 'pen' : 'eye']" @click="showPreview = !showPreview">
              {{ showPreview ? t('adminSettings.legal.edit') : t('adminSettings.legal.preview') }}
            </SecondaryButton>
            <div class="flex items-center gap-2">
              <SecondaryButton :icon="['fas', 'plus']" @click="showAddFileModal = true">
                {{ t('adminSettings.legal.addFile') }}
              </SecondaryButton>
              <PrimaryButton :disabled="saving" @click="saveAll">
                {{ t('adminSettings.legal.save') }}
              </PrimaryButton>
            </div>
          </div>

          <!-- Preview shows enabled files combined -->
          <div v-if="showPreview"
               class="markdown-content rounded-lg border border-(--border) bg-(--bg) p-4 min-h-[200px] overflow-auto"
               v-html="renderedPreview"/>

          <!-- File list -->
          <template v-if="!showPreview">
            <MutedText v-if="files.length === 0" size="sm">{{ t('adminSettings.legal.noFiles') }}</MutedText>

            <NeutralContainer v-for="(file, index) in files" :key="index" class="space-y-2"
                              :class="{ 'opacity-50': !file.enabled }">
              <div class="flex items-center gap-2">
                <ToggleInput :model-value="file.enabled" @update:model-value="toggleEnabled(index)"/>
                <SubHeader class="flex-1 min-w-0">{{ file.displayName || file.filename }}</SubHeader>
                <IconButton :icon="['fas', 'chevron-up']" :label="t('adminSettings.legal.moveUp')" :disabled="index === 0" @click="moveUp(index)"/>
                <IconButton :icon="['fas', 'chevron-down']" :label="t('adminSettings.legal.moveDown')" :disabled="index === files.length - 1" @click="moveDown(index)"/>
                <DeleteButton @click="confirmDeleteFile(index)"/>
              </div>
              <textarea
                  v-model="file.content"
                  class="w-full rounded-lg border border-(--border) bg-(--bg) text-(--text) p-3 min-h-[200px] font-mono text-sm outline-none resize-y focus:ring-2 focus:ring-primary/50"
                  :placeholder="t('adminSettings.legal.contentPlaceholder')"
              />
            </NeutralContainer>
          </template>
        </template>
      </NeutralContainer>

      <!-- Add locale modal -->
      <Modal v-model="showAddLocaleModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('adminSettings.legal.addLocaleTitle') }}</SectionHeader>
          <TextInput v-model="newLocaleCode" :placeholder="t('adminSettings.legal.localeCodePlaceholder')"/>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showAddLocaleModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="!newLocaleCode.trim()" @click="addLocale">{{ t('adminSettings.legal.addLocale') }}</PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Add file modal -->
      <Modal v-model="showAddFileModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('adminSettings.legal.addFileTitle') }}</SectionHeader>
          <TextInput v-model="newFileName" :placeholder="t('adminSettings.legal.fileNamePlaceholder')"/>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showAddFileModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="!newFileName.trim()" @click="addFile">{{ t('adminSettings.legal.addFile') }}</PrimaryButton>
          </div>
        </div>
      </Modal>

      <!-- Delete file modal -->
      <Modal v-model="showDeleteFileModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('adminSettings.legal.deleteFileTitle') }}</SectionHeader>
          <p class="text-sm">{{ t('adminSettings.legal.deleteFileConfirm', { name: fileToDeleteIndex >= 0 ? (files[fileToDeleteIndex]?.displayName || files[fileToDeleteIndex]?.filename) : '' }) }}</p>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showDeleteFileModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <DeleteButton @click="deleteFile">{{ t('common.delete') }}</DeleteButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
