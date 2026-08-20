/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import TagPicker from '@/components/input/TagPicker.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import DocumentPreview from './DocumentPreview.vue'
import {formatSize} from './documentIcon'
import {formatDate} from '@/util/format'
import {downloadAuthed} from '@/util/downloadAuthed'
import {contentUrl, type MemberDocument} from '@/api/memberDocuments'
import type {StationMember} from '@/api/types'

/**
 * A document, open: what it says, whom it belongs to, and the words it is filed under.
 *
 * <p>Whom it concerns is decided here rather than at the upload, because that is usually noticed
 * while reading it, and it is a set rather than a list to add to: somebody put on it by mistake
 * has to come off again.
 */
const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  document: MemberDocument | null
  /** Every member of the station, to name and to choose the ones a document is bound to. */
  allMembers?: StationMember[]
  /** Every label written so far, offered while typing. */
  allTags?: string[]
  canEdit?: boolean
}>()

const emit = defineEmits<{
  members: [documentId: number, memberIds: number[]]
  tags: [documentId: number, tags: string[]]
  remove: [document: MemberDocument]
}>()

const {t} = useI18n()

const tags = ref<string[]>([])
const members = ref<string[]>([])

watch(() => props.document, (document) => {
  tags.value = [...(document?.tags ?? [])]
  members.value = (document?.memberIds ?? []).map(String)
}, {immediate: true})

const memberOptions = computed(() => (props.allMembers ?? [])
    .map(member => ({value: String(member.id), label: member.name ?? String(member.id)})))

const boundNames = computed(() => (props.allMembers ?? [])
    .filter(member => props.document?.memberIds.includes(member.id))
    .map(member => member.name)
    .filter(Boolean))

function saveMembers() {
  if (!props.document) return
  emit('members', props.document.id, members.value.map(Number))
}

function saveTags() {
  if (!props.document) return
  emit('tags', props.document.id, tags.value)
}

async function download() {
  if (!props.document) return
  await downloadAuthed(contentUrl(props.document.id), props.document.fileName)
}
</script>

<template>
  <Modal v-model="modelValue" size="lg">
    <div v-if="props.document" class="space-y-4">
      <div class="space-y-1 pr-10">
        <SubHeader>{{ props.document.title }}</SubHeader>
        <MutedText size="sm">
          {{ props.document.fileName }} · {{ formatSize(props.document.sizeBytes) }}
          · {{ formatDate(props.document.createdAt) }}
        </MutedText>
      </div>

      <div class="flex items-center gap-2">
        <DownloadButton @click="download"/>
        <DeleteButton v-if="props.canEdit" @click="emit('remove', props.document)"/>
      </div>

      <DocumentPreview :document="props.document"/>

      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <FieldLabel>{{ t('documents.boundMembers') }}</FieldLabel>
          <template v-if="props.canEdit">
            <MultiSelectDropdown
                v-model="members"
                :options="memberOptions"
                :placeholder="t('documents.bindPlaceholder')"
                searchable
            />
            <SecondaryButton @click="saveMembers">{{ t('common.save') }}</SecondaryButton>
          </template>
          <template v-else>
            <MutedText v-if="boundNames.length === 0" tag="div" size="sm">
              {{ t('documents.boundToNobody') }}
            </MutedText>
            <div v-for="name in boundNames" :key="name" class="text-sm">{{ name }}</div>
          </template>
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('documents.tags') }}</FieldLabel>
          <TagPicker
              v-model="tags"
              :suggestions="props.allTags"
              :disabled="!props.canEdit"
              :placeholder="t('documents.tagsPlaceholder')"
          />
          <SecondaryButton v-if="props.canEdit" @click="saveTags">{{ t('common.save') }}</SecondaryButton>
        </div>
      </div>
    </div>
  </Modal>
</template>
