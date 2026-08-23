/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EmptyHint from '@/components/typography/EmptyHint.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Modal from '@/components/feedback/Modal.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FileInput from '@/components/input/FileInput.vue'
import {clusterMembers} from '@/api'
import type {ManagedMemberDocument} from '@/api/clusterMembers'
import {formatDate, formatSize} from '@/util/format'

/**
 * What is filed about one person at one of the association's stations.
 *
 * <p>The association reads and adds, and does no more than that: a label, a binding to another
 * member or a removal is the station's business, because the document belongs to the station that
 * holds the person and stays there when that station leaves.
 */
const props = defineProps<{
  memberId: number
  /** Whether the reader may add to the file, which follows from the right to manage members. */
  canUpload?: boolean
}>()

const {t} = useI18n()

const documents = ref<ManagedMemberDocument[]>([])
const loading = ref(false)
const error = ref('')
const showUpload = ref(false)
const file = ref<File | null>(null)
const title = ref('')
const saving = ref(false)

async function reload() {
  loading.value = true
  error.value = ''
  try {
    documents.value = await clusterMembers.listManagedMemberDocuments(props.memberId)
  } catch {
    error.value = t('common.error')
  }
  loading.value = false
}

watch(() => props.memberId, reload, {immediate: true})

watch(showUpload, (open) => {
  if (open) return
  file.value = null
  title.value = ''
  saving.value = false
})

function onFile(chosen: File) {
  file.value = chosen
  if (!title.value) title.value = chosen.name
}

async function upload() {
  if (!file.value) return
  saving.value = true
  error.value = ''
  try {
    await clusterMembers.uploadManagedMemberDocument(
        props.memberId, file.value, title.value.trim() || file.value.name)
    showUpload.value = false
    await reload()
  } catch {
    error.value = t('common.error')
  }
  saving.value = false
}

async function download(document: ManagedMemberDocument) {
  error.value = ''
  try {
    await clusterMembers.downloadManagedMemberDocument(document.id, document.fileName)
  } catch {
    error.value = t('common.error')
  }
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="cluster-member-documents">
    <div class="flex items-center justify-between gap-2 flex-wrap">
      <SectionHeader>{{ t('clusterMemberDetail.documents.title') }}</SectionHeader>
      <PrimaryButton
          v-if="props.canUpload"
          data-testid="cluster-member-document-upload"
          :icon="['fas', 'upload']"
          @click="showUpload = true"
      >
        {{ t('clusterMemberDetail.documents.upload') }}
      </PrimaryButton>
    </div>

    <MutedText size="sm">{{ t('clusterMemberDetail.documents.hint') }}</MutedText>

    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Spinner v-if="loading" size="md"/>
    <EmptyHint v-else-if="documents.length === 0">{{ t('clusterMemberDetail.documents.none') }}</EmptyHint>
    <ul v-else class="space-y-2">
      <li
          v-for="document in documents"
          :key="document.id"
          class="flex items-center justify-between gap-3 rounded border border-(--border) px-3 py-2"
          data-testid="cluster-member-document"
      >
        <div class="min-w-0">
          <div class="truncate text-sm" data-testid="cluster-member-document-title">{{ document.title }}</div>
          <MutedText size="xs">
            {{ document.fileName }} · {{ formatSize(document.sizeBytes) }} · {{ formatDate(document.createdAt) }}
          </MutedText>
        </div>
        <DownloadButton data-testid="cluster-member-document-download" @click="download(document)"/>
      </li>
    </ul>

    <Modal v-model="showUpload">
      <div class="space-y-4">
        <SectionHeader>{{ t('clusterMemberDetail.documents.uploadTitle') }}</SectionHeader>
        <MutedText size="sm">{{ t('clusterMemberDetail.documents.uploadHint') }}</MutedText>

        <div class="space-y-1">
          <FieldLabel>{{ t('clusterMemberDetail.documents.file') }}</FieldLabel>
          <FileInput data-testid="cluster-member-document-file" @select="onFile"/>
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('clusterMemberDetail.documents.name') }}</FieldLabel>
          <TextInput v-model="title" data-testid="cluster-member-document-name"/>
        </div>

        <div class="flex justify-end gap-2">
          <SecondaryButton @click="showUpload = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton
              :disabled="!file || saving"
              data-testid="cluster-member-document-save"
              @click="upload"
          >
            {{ saving ? t('common.loading') : t('clusterMemberDetail.documents.upload') }}
          </PrimaryButton>
        </div>
      </div>
    </Modal>
  </NeutralContainer>
</template>
