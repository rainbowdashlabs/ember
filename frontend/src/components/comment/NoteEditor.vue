/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {NoteVersion} from '@/api/types'
import {comments as notesApi} from '@/api'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import DiffView from '@/components/display/DiffView.vue'
import {formatDateTime as formatDate} from '@/util/format'

const props = defineProps<{
  entityType: string
  entityId: number
}>()

const {t} = useI18n()

const content = ref('')
const originalContent = ref('')
const versions = ref<NoteVersion[]>([])
const loading = ref(true)
const error = ref('')
const showHistory = ref(false)

const hasChanges = ref(false)

function checkChanges() {
  hasChanges.value = content.value !== originalContent.value
}

async function loadNote() {
  loading.value = true
  try {
    const note = await notesApi.getNote(props.entityType, props.entityId)
    content.value = note?.content ?? ''
    originalContent.value = content.value
  } catch { /* no note yet */ }
  finally { loading.value = false }
}

async function save() {
  error.value = ''
  try {
    const note = await notesApi.updateNote(props.entityType, props.entityId, {content: content.value})
    originalContent.value = note.content
    hasChanges.value = false
  } catch (e) {
    error.value = t('common.error')
    throw e
  }
}

async function loadVersions() {
  try {
    versions.value = await notesApi.getNoteVersions(props.entityType, props.entityId)
  } catch { /* ignore */ }
}

function toggleHistory() {
  showHistory.value = !showHistory.value
  if (showHistory.value) loadVersions()
}

onMounted(loadNote)
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between">
      <SubHeader>{{ t('notes.title') }}</SubHeader>
      <SecondaryButton compact :icon="['fas', 'clock-rotate-left']" @click="toggleHistory">
        {{ t('notes.history') }}
      </SecondaryButton>
    </div>

    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Spinner v-if="loading" size="sm"/>

    <template v-if="!loading">
      <TextAreaInput
        v-model="content"
        :rows="6"
        :placeholder="t('notes.placeholder')"
        @input="checkChanges"
      />
      <SaveButton :disabled="!hasChanges" compact :action="save"/>
    </template>

    <!-- Version history -->
    <template v-if="showHistory && versions.length > 0">
      <NeutralContainer class="space-y-2 max-h-60 overflow-y-auto">
        <SubHeader class="text-xs">{{ t('notes.versionHistory') }}</SubHeader>
        <div v-for="v in versions" :key="v.id" class="border-b border-(--border) pb-2 last:border-0">
          <MutedText size="xs">{{ formatDate(v.createdAt) }}</MutedText>
          <DiffView :patch="v.diffPatch" compact/>
        </div>
      </NeutralContainer>
    </template>
    <MutedText v-if="showHistory && versions.length === 0" size="sm">{{ t('notes.noVersions') }}</MutedText>
  </div>
</template>
