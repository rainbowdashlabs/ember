/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DocumentFilterBar from './documentsview/DocumentFilterBar.vue'
import DocumentGrid from '@/components/documents/DocumentGrid.vue'
import DocumentModal from '@/components/documents/DocumentModal.vue'
import DocumentUploadModal from '@/components/documents/DocumentUploadModal.vue'
import {usePermissions} from '@/composables/usePermissions'
import {StationPermission} from '@/api/types'
import {memberDocuments, stationMembers} from '@/api'
import type {DocumentUpload, MemberDocument} from '@/api/memberDocuments'
import type {StationMember} from '@/api/types'

/**
 * The document store of the station: everything that was ever put in, whether it belongs to
 * somebody or to nobody, a page at a time and searchable by what the documents say.
 */
const {t} = useI18n()
const {hasPermission} = usePermissions()

const canEdit = computed(() => hasPermission(StationPermission.MEMBER_EDIT))

const documents = ref<MemberDocument[]>([])
const total = ref(0)
const page = ref(0)
const search = ref('')
const memberFilter = ref<string[]>([])
const allTags = ref<string[]>([])
const members = ref<StationMember[]>([])
const loading = ref(false)
const error = ref('')

const showUpload = ref(false)
const showDocument = ref(false)
const opened = ref<MemberDocument | null>(null)

/** How many documents a page holds, which the store answers with rather than being told. */
const pageSize = 24
const pages = computed(() => Math.max(Math.ceil(total.value / pageSize), 1))

const memberOptions = computed(() => members.value
    .map(member => ({value: String(member.id), label: member.name ?? String(member.id)})))

/**
 * Fetches the page that is asked for now.
 *
 * <p>The spinner only stands in for a list that is not there yet. Swapping a list that is already
 * on screen for a spinner on every keystroke is what makes a search flicker, so a reload keeps
 * showing what it has until the answer replaces it.
 */
async function reload() {
  loading.value = documents.value.length === 0
  error.value = ''
  try {
    const result = await memberDocuments.listStation({
      page: page.value,
      memberIds: memberFilter.value.map(Number),
      search: search.value.trim() || undefined,
    })
    documents.value = result.documents
    total.value = result.total
  } catch {
    error.value = t('common.error')
  }
  loading.value = false
}

/** Waits for the typing to stop, so a word is one request rather than one per letter. */
let searchTimeout: ReturnType<typeof setTimeout> | null = null

function onSearch() {
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    page.value = 0
    reload()
  }, 300)
}

async function loadTags() {
  try {
    allTags.value = await memberDocuments.listTags()
  } catch {
    allTags.value = []
  }
}

async function loadMembers() {
  try {
    members.value = await stationMembers.listMembers()
  } catch {
    members.value = []
  }
}

watch(page, reload)
watch(memberFilter, () => {
  page.value = 0
  reload()
}, {deep: true})

loadMembers()
loadTags()
reload()

async function upload(upload: DocumentUpload) {
  error.value = ''
  try {
    await memberDocuments.uploadForStation(upload)
    showUpload.value = false
    await reload()
  } catch {
    error.value = t('common.error')
  }
}

function open(document: MemberDocument) {
  opened.value = document
  showDocument.value = true
}

async function act(action: Promise<unknown>) {
  error.value = ''
  try {
    await action
    await reload()
    await loadTags()
    opened.value = documents.value.find(document => document.id === opened.value?.id) ?? null
    if (!opened.value) showDocument.value = false
  } catch {
    error.value = t('common.error')
  }
}
</script>

<template>
  <ViewContent :title="t('pages.station-members-documents.title')"
               :subtitle="t('pages.station-members-documents.subtitle')">
    <div class="space-y-4">
      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <DocumentFilterBar
          v-model:search="search"
          v-model:members="memberFilter"
          :member-options="memberOptions"
          :can-upload="canEdit"
          @search-input="onSearch"
          @upload="showUpload = true"
      />

      <Spinner v-if="loading" size="md"/>
      <DocumentGrid v-else :documents="documents" @open="open"/>

      <div v-if="pages > 1" class="flex items-center justify-center gap-3">
        <SecondaryButton :disabled="page === 0" @click="page -= 1">{{ t('common.previous') }}</SecondaryButton>
        <MutedText size="sm">{{ t('documents.pageOf', {page: page + 1, pages}) }}</MutedText>
        <SecondaryButton :disabled="page + 1 >= pages" @click="page += 1">{{ t('common.next') }}</SecondaryButton>
      </div>

      <DocumentUploadModal
          v-model="showUpload"
          can-hide
          :members="members"
          :all-tags="allTags"
          @upload="upload"
      />
      <DocumentModal
          v-model="showDocument"
          :document="opened"
          :all-members="members"
          :all-tags="allTags"
          :can-edit="canEdit"
          @members="(id, ids) => act(memberDocuments.setMembers(id, ids))"
          @tags="(id, tags) => act(memberDocuments.setTags(id, tags))"
          @remove="document => act(memberDocuments.remove(document.id))"
      />
    </div>
  </ViewContent>
</template>
