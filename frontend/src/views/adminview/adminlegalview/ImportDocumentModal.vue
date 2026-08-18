/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ImportResultPanel from './ImportResultPanel.vue'
import {adminSettings} from '@/api'
import type {LegalFile, LegalImport} from '@/api/adminSettings'

/**
 * Takes a document written elsewhere and shows what Ember made of it before anything is applied:
 * which sections it found, how many numbers became references, and what it could not match.
 */
const {t} = useI18n()

const props = defineProps<{
  type: string
  locale: string
}>()

const show = defineModel<boolean>('show', {required: true})

const emit = defineEmits<{
  apply: [files: LegalFile[]]
}>()

const markdown = ref('')
const result = ref<LegalImport | null>(null)
const running = ref(false)
const error = ref('')

async function run(action: () => Promise<LegalImport>) {
  running.value = true
  error.value = ''
  result.value = null
  try {
    result.value = await action()
  } catch {
    error.value = t('adminSettings.legal.importFailed')
  } finally {
    running.value = false
  }
}

function onFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (file) run(() => adminSettings.importLegalDocument(props.type, props.locale, file))
}

function apply() {
  if (!result.value) return
  emit('apply', result.value.files)
  show.value = false
}

watch(show, open => {
  if (!open) return
  markdown.value = ''
  result.value = null
  error.value = ''
})
</script>

<template>
  <Modal v-model="show" size="lg">
    <div class="space-y-4">
      <SubHeader>{{ t('adminSettings.legal.importTitle') }}</SubHeader>
      <MutedText size="sm">{{ t('adminSettings.legal.importHint') }}</MutedText>

      <input type="file" accept=".md,.txt,.docx,.odt,.rtf,.html,.htm,.epub"
             class="block w-full text-sm" @change="onFile"/>

      <TextAreaInput v-model="markdown" :rows="6"
                     :placeholder="t('adminSettings.legal.importPastePlaceholder')"/>
      <SecondaryButton :disabled="!markdown.trim() || running"
                       @click="run(() => adminSettings.importLegalMarkdown(type, locale, markdown))">
        {{ t('adminSettings.legal.importPaste') }}
      </SecondaryButton>

      <Spinner v-if="running" size="md"/>
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <ImportResultPanel v-if="result" :result="result"/>

      <div class="flex justify-end gap-2">
        <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!result || result.files.length === 0" @click="apply">
          {{ t('adminSettings.legal.importApply') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
