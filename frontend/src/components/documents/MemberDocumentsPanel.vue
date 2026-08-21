/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import DocumentGrid from './DocumentGrid.vue'
import DocumentModal from './DocumentModal.vue'
import DocumentUploadModal from './DocumentUploadModal.vue'
import {memberDocuments} from '@/api'
import type {DocumentUpload, MemberDocument} from '@/api/memberDocuments'
import type {StationMember} from '@/api/types'

/**
 * The documents of one member, on their own profile as well as on the profile a manager opens.
 *
 * <p>Which of the two it is decides nothing here: what a reader may do is handed in, because the
 * answer comes from their rights and from whose profile it is, and both are known above.
 */
const props = defineProps<{
  memberId: number
  /** Whether the reader may put documents on this profile. */
  canUpload?: boolean
  /** Whether the reader may bind, tag and remove, which follows from the right to edit members. */
  canEdit?: boolean
  allMembers?: StationMember[]
}>()

const {t} = useI18n()

const documents = ref<MemberDocument[]>([])
const search = ref('')
const allTags = ref<string[]>([])
const loading = ref(false)
const error = ref('')
const showUpload = ref(false)
const showDocument = ref(false)
const opened = ref<MemberDocument | null>(null)

async function reload() {
  loading.value = true
  error.value = ''
  try {
    documents.value = await memberDocuments.listForMember(props.memberId)
  } catch {
    error.value = t('common.error')
  }
  loading.value = false
}

watch(() => props.memberId, reload, {immediate: true})

/** Only somebody who may edit gets to write labels, so only they need what has been written. */
if (props.canEdit) {
  memberDocuments.listTags().then(tags => {
    allTags.value = tags
  }).catch(() => {
    allTags.value = []
  })
}

/**
 * What the search leaves. One member's documents are few enough to sift here rather than to ask
 * for again, and asking again would want the right to read other members.
 */
const shown = computed(() => {
  const term = search.value.trim().toLowerCase()
  if (!term) return documents.value
  return documents.value.filter(document => document.title.toLowerCase().includes(term)
      || document.fileName.toLowerCase().includes(term)
      || document.tags.some(tag => tag.toLowerCase().includes(term)))
})

async function upload(upload: DocumentUpload) {
  error.value = ''
  try {
    await memberDocuments.uploadForMember(props.memberId, upload)
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
    opened.value = documents.value.find(document => document.id === opened.value?.id) ?? null
    if (!opened.value) showDocument.value = false
  } catch {
    error.value = t('common.error')
  }
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <div class="flex items-center justify-between gap-2 flex-wrap">
      <SectionHeader>{{ t('documents.title') }}</SectionHeader>
      <div class="flex items-center gap-2">
        <TextInput
            v-if="documents.length > 0"
            v-model="search"
            class="min-w-56"
            :placeholder="t('documents.searchOwnPlaceholder')"
            :aria-label="t('documents.search')"
        />
        <PrimaryButton v-if="props.canUpload" :icon="['fas', 'upload']" @click="showUpload = true">
          {{ t('documents.upload') }}
        </PrimaryButton>
      </div>
    </div>

    <Alert v-if="error" variant="error">{{ error }}</Alert>
    <Spinner v-if="loading" size="md"/>
    <DocumentGrid v-else :documents="shown" @open="open"/>

    <DocumentUploadModal v-model="showUpload" :can-hide="props.canEdit" :all-tags="allTags" @upload="upload"/>
    <DocumentModal
        v-model="showDocument"
        :document="opened"
        :all-members="props.allMembers"
        :all-tags="allTags"
        :can-edit="props.canEdit"
        @members="(id, members) => act(memberDocuments.setMembers(id, members))"
        @tags="(id, tags) => act(memberDocuments.setTags(id, tags))"
        @remove="document => act(memberDocuments.remove(document.id))"
    />
  </NeutralContainer>
</template>
