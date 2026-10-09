/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import MutedText from '@/components/typography/MutedText.vue'
import DocumentFilterBar from './storeview/DocumentFilterBar.vue'
import DocumentPruneBar from './storeview/DocumentPruneBar.vue'
import FilingRulesLink from './storeview/FilingRulesLink.vue'
import {useDocumentPruning} from './storeview/useDocumentPruning'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {showToast} from '@/util/toast'
import {fromMember} from '@/components/input/select/memberOption'
import DocumentGrid from '@/components/documents/DocumentGrid.vue'
import DocumentModal from '@/components/documents/DocumentModal.vue'
import DocumentUploadModal from '@/components/documents/DocumentUploadModal.vue'
import GenerateDocumentModal from '@/components/documents/GenerateDocumentModal.vue'
import BulkGenerateModal from '@/components/documents/bulk/BulkGenerateModal.vue'
import GenerationJobsPanel from './storeview/GenerationJobsPanel.vue'
import {usePermissions} from '@/composables/usePermissions'
import {documents as documentsApi, stationMembers} from '@/api'
import type {DocumentFilter, DocumentUpload} from '@/api/documents'
import {StationPermission, type MemberDocumentResponse, type MemberWithName} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure} from '@/util/failure'

/**
 * The document store of the station: everything that was ever put in, whether it belongs to
 * somebody or to nobody, a page at a time and searchable by what the documents say.
 *
 * <p>Whoever may file documents for members also generates them here from a template: for one member,
 * opened in the store once filed, or for many at once in a background run, followed below the list.
 */
const {t} = useI18n()
const {hasPermission} = usePermissions()

const canEdit = computed(() => hasPermission(StationPermission.DOCUMENT_EDIT_MEMBER))
const readsMembers = computed(() => hasPermission(StationPermission.DOCUMENT_READ_MEMBER))

const documents = ref<MemberDocumentResponse[]>([])
const total = ref(0)
const page = ref(0)
const search = ref('')
const memberFilter = ref<string[]>([])
const unboundOnly = ref(false)
const departedOnly = ref(false)
const allTags = ref<string[]>([])
const members = ref<MemberWithName[]>([])

const showUpload = ref(false)
const showGenerate = ref(false)
const showBulk = ref(false)
const jobsPanel = ref<InstanceType<typeof GenerationJobsPanel> | null>(null)
const showDocument = ref(false)
const opened = ref<MemberDocumentResponse | null>(null)

/** The document just generated here, opened as soon as the list that holds it arrives. */
const awaitedDocument = ref<number | null>(null)

/** How many documents a page holds, which the store answers with rather than being told. */
const pageSize = 24
const pages = computed(() => Math.max(Math.ceil(total.value / pageSize), 1))

/** The members a document can be bound to, which are the current ones; those who left are only named. */
const currentMembers = computed(() => members.value.filter(member => !member.formerAt))
const memberOptions = computed(() => currentMembers.value.map(fromMember))

/** What the store is narrowed to now. */
function currentFilter(): DocumentFilter {
  return {
    memberIds: memberFilter.value.map(Number),
    search: search.value.trim() || undefined,
    unbound: unboundOnly.value || undefined,
    departed: departedOnly.value || undefined,
  }
}

const pruning = useDocumentPruning(currentFilter)
const {selected, confirming, busy: pruneBusy} = pruning

/**
 * Whether the list on screen is about to be replaced by another filter's. A document ticked on it then
 * would be dropped with the old list or, worse, stay chosen while the new list no longer shows it, so
 * nothing can be chosen until the narrowed list has arrived.
 */
const narrowing = ref(false)

/** Waits for the typing to stop, so a word is one request rather than one per letter. */
let searchTimeout: ReturnType<typeof setTimeout> | null = null

/** Fetches the page that is asked for now. */
const {loading: fetching, failure, reload} = useAsyncLoader(async (isCurrent) => {
  try {
    const result = await documentsApi.listStation({...currentFilter(), page: page.value})
    if (!isCurrent()) return
    documents.value = result.documents
    total.value = result.total
  } finally {
    if (isCurrent() && !searchTimeout) narrowing.value = false
  }
}, {autoLoad: false})

/**
 * The spinner only stands in for a list that is not there yet. Swapping a list that is already on
 * screen for a spinner on every keystroke is what makes a search flicker, so a reload keeps showing
 * what it has until the answer replaces it.
 */
const loading = computed(() => fetching.value && documents.value.length === 0)

function onSearch() {
  narrowing.value = true
  if (searchTimeout) clearTimeout(searchTimeout)
  searchTimeout = setTimeout(() => {
    searchTimeout = null
    refilter()
  }, 300)
}

async function loadTags() {
  try {
    allTags.value = await documentsApi.listTags()
  } catch {
    allTags.value = []
  }
}

/** Every member, those who left included, so a document still bound to one can name them. */
async function loadMembers() {
  try {
    members.value = await stationMembers.listMembers(true)
  } catch {
    members.value = []
  }
}

/** A different filter is a different set of documents, so the choice made in the old one goes. */
function refilter() {
  narrowing.value = true
  page.value = 0
  pruning.clear()
  reload()
}

watch(page, reload)
watch(memberFilter, refilter, {deep: true})
watch([unboundOnly, departedOnly], refilter)

loadMembers()
loadTags()
reload()

/**
 * Puts a document in the store.
 *
 * <p>The window is closed whatever happens, because the reason it failed is shown on the page behind
 * it. A file the station is not allowed to keep is the ordinary refusal here and the server says so in
 * its own words, which is no use to anybody reading it through a dialog that is covering it.
 */
async function upload(upload: DocumentUpload) {
  failure.value = null
  try {
    await documentsApi.uploadForStation(upload)
  } catch (e) {
    showUpload.value = false
    failure.value = describeFailure(e, t)
    return
  }
  showUpload.value = false
  await reload()
}

function open(document: MemberDocumentResponse) {
  opened.value = document
  showDocument.value = true
}

/**
 * Shows a document generated from the store: the first page, where the newest document stands, and
 * the document itself opened on it. A filter that leaves the document out leaves it closed; the dialog
 * has already said it was filed.
 */
async function showFiled(documentId: number) {
  awaitedDocument.value = documentId
  if (page.value === 0) await reload()
  else page.value = 0
}

watch(documents, list => {
  if (awaitedDocument.value === null) return
  const filed = list.find(document => document.id === awaitedDocument.value)
  awaitedDocument.value = null
  if (filed) open(filed)
})

/**
 * Runs a change to one document and catches the list up afterwards.
 *
 * <p>Caught apart: by the time the list is fetched again the change is written, and a reader told that
 * removing a document failed removes it again, on a document that is already gone.
 */
async function act(action: Promise<unknown>) {
  failure.value = null
  try {
    await action
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }

  try {
    await reload()
    await loadTags()
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
    return
  }
  opened.value = documents.value.find(document => document.id === opened.value?.id) ?? null
  if (!opened.value) showDocument.value = false
}

/** Shows how the signatures stand after a manager changed them in the open document. */
function signaturesChanged() {
  void act(Promise.resolve())
}

/** Deletes the chosen documents once the reader confirmed it, and shows the store without them. */
async function prune() {
  failure.value = null
  try {
    const count = await pruning.prune()
    showToast(t('documents.pruned', {count}), 'success')
  } catch (e) {
    confirming.value = false
    failure.value = describeFailure(e, t)
    return
  }
  await reload()
}

/** Chooses every document the filter matches, not only the page in front of the reader. */
async function selectAll() {
  try {
    await pruning.selectAll()
  } catch (e) {
    failure.value = describeFailure(e, t)
  }
}
</script>

<template>
  <ViewContent :title="t('pages.documents-store.title')"
               :subtitle="t('pages.documents-store.subtitle')">
    <div class="space-y-4">
      <FailureAlert :failure="failure"/>

      <DocumentFilterBar
          v-model:search="search"
          v-model:members="memberFilter"
          v-model:unbound="unboundOnly"
          v-model:departed="departedOnly"
          :member-options="memberOptions"
          :can-upload="canEdit"
          :can-generate="canEdit"
          :reads-members="readsMembers"
          @search-input="onSearch"
          @upload="showUpload = true"
          @generate="showGenerate = true"
          @bulk="showBulk = true"
      />

      <DocumentPruneBar
          v-if="canEdit && total > 0"
          :selected="selected.length"
          :total="total"
          :busy="pruneBusy || narrowing"
          @select-all="selectAll"
          @clear="pruning.clear()"
          @prune="confirming = true"
      />

      <Spinner v-if="loading" size="md"/>
      <DocumentGrid
          v-else
          v-model:selected="selected"
          :documents="documents"
          :selectable="canEdit"
          :choice-held="narrowing"
          @open="open"
      />

      <ButtonRow v-if="pages > 1" align="center">
        <SecondaryButton :disabled="page === 0" @click="page -= 1">{{ t('common.previous') }}</SecondaryButton>
        <MutedText size="sm" class="text-center">{{ t('documents.pageOf', {page: page + 1, pages}) }}</MutedText>
        <SecondaryButton :disabled="page + 1 >= pages" @click="page += 1">{{ t('common.next') }}</SecondaryButton>
      </ButtonRow>

      <GenerationJobsPanel v-if="canEdit" ref="jobsPanel"/>
      <FilingRulesLink/>
    </div>

    <ConfirmDeleteModal
        v-model="confirming"
        :title="t('documents.pruneTitle')"
        :message="t('documents.pruneMessage', {count: selected.length})"
        :busy="pruneBusy"
        confirm-test-id="documents-prune-confirm"
        @confirm="prune"
    />
    <DocumentUploadModal
        v-model="showUpload"
        can-hide
        can-label
        :members="currentMembers"
        :all-tags="allTags"
        @upload="upload"
    />
    <GenerateDocumentModal v-if="showGenerate" v-model="showGenerate" :members="memberOptions" @filed="showFiled"/>
    <BulkGenerateModal v-if="showBulk" v-model="showBulk" @started="jobsPanel?.reload()"/>
    <DocumentModal
        v-model="showDocument"
        :document="opened"
        :all-members="members"
        :all-tags="allTags"
        :can-edit="canEdit"
        :version-url="documentsApi.versionUrl"
        :manage-signatures="canEdit"
        :on-signatures-changed="signaturesChanged"
        @members="(id, ids) => act(documentsApi.setMembers(id, ids))"
        @tags="(id, tags) => act(documentsApi.setTags(id, tags))"
        @remove="document => act(documentsApi.remove(document.id))"
    />
  </ViewContent>
</template>
