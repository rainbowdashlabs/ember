/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import LegalFileRow from './LegalFileRow.vue'
import type {LegalFile} from '@/api/adminSettings'
import {applyPlaceholders} from '@/util/placeholders'
import {marked} from 'marked'

const {t} = useI18n()

const props = defineProps<{
  saveAction: () => Promise<void>
  placeholderValues: Record<string, string>
}>()

const files = defineModel<LegalFile[]>('files', {required: true})
const showPreview = defineModel<boolean>('showPreview', {required: true})

const emit = defineEmits<{
  addFile: []
  loadTemplate: []
  importDocument: []
  deleteFile: [index: number]
}>()

const renderedPreview = computed(() => {
  const enabledContent = files.value.filter(f => f.enabled).map(f => f.content).join('\n\n')
  if (!enabledContent) return ''
  return marked.parse(applyPlaceholders(enabledContent, props.placeholderValues)) as string
})

function toggleEnabled(index: number) {
  const file = files.value[index]
  if (!file) return
  files.value[index] = {...file, enabled: !file.enabled}
}

function swap(index: number, otherIndex: number) {
  const arr = [...files.value]
  const current = arr[index]
  const other = arr[otherIndex]
  if (!current || !other) return
  arr[index] = other
  arr[otherIndex] = current
  files.value = arr
}

function moveUp(index: number) {
  if (index <= 0) return
  swap(index, index - 1)
}

function moveDown(index: number) {
  if (index >= files.value.length - 1) return
  swap(index, index + 1)
}

function updateContent(index: number, value: string) {
  const file = files.value[index]
  if (!file) return
  files.value[index] = {...file, content: value}
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <SecondaryButton :icon="['fas', showPreview ? 'pen' : 'eye']" @click="showPreview = !showPreview">
        {{ showPreview ? t('adminSettings.legal.edit') : t('adminSettings.legal.preview') }}
      </SecondaryButton>
      <div class="flex items-center gap-2">
        <SecondaryButton :icon="['fas', 'file-import']" @click="emit('loadTemplate')">
          {{ t('adminSettings.legal.loadTemplate') }}
        </SecondaryButton>
        <SecondaryButton :icon="['fas', 'upload']" @click="emit('importDocument')">
          {{ t('adminSettings.legal.import') }}
        </SecondaryButton>
        <SecondaryButton :icon="['fas', 'plus']" @click="emit('addFile')">
          {{ t('adminSettings.legal.addFile') }}
        </SecondaryButton>
        <SaveButton :action="saveAction"/>
      </div>
    </div>

    <div v-if="showPreview"
         class="markdown-content rounded-lg border border-(--border) bg-(--bg) p-4 min-h-[200px] overflow-auto"
         v-html="renderedPreview"/>

    <template v-if="!showPreview">
      <MutedText v-if="files.length === 0" size="sm">{{ t('adminSettings.legal.noFiles') }}</MutedText>
      <LegalFileRow
          v-for="(file, index) in files"
          :key="index"
          :file="file"
          :index="index"
          :total="files.length"
          @toggle="toggleEnabled(index)"
          @move-up="moveUp(index)"
          @move-down="moveDown(index)"
          @delete="emit('deleteFile', index)"
          @update-content="(v) => updateContent(index, v)"
      />
    </template>
  </div>
</template>
