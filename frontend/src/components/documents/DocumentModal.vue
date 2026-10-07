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
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import {fromMember} from '@/components/input/select/memberOption'
import TagPicker from '@/components/input/TagPicker.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DownloadButton from '@/components/button/DownloadButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import FileView from '@/components/documents/FileView.vue'
import SealedVersionList from '@/components/documents/SealedVersionList.vue'
import {formatDate, formatSize} from '@/util/format'
import {downloadAuthed} from '@/util/downloadAuthed'
import {contentUrl as stationContentUrl} from '@/api/documents'
import type {MemberDocumentResponse} from '@/api/generated/schema'
import type {MemberLike} from '@/components/input/select/memberOption'

/** A member a document can be bound to, carrying when they left where they have. */
type BoundMember = MemberLike & {formerAt?: string | null}

/**
 * A document, open: what it says, whom it belongs to, and the words it is filed under.
 *
 * <p>Whom it concerns is decided here rather than at the upload, because that is usually noticed
 * while reading it, and it is a set rather than a list to add to: somebody put on it by mistake
 * has to come off again. A sealed document is locked: it offers neither removing nor choosing its
 * members, and lists its sealed versions instead.
 */
const modelValue = defineModel<boolean>({required: true})

const props = withDefaults(defineProps<{
  document: MemberDocumentResponse | null
  /**
   * Every member of the station, to name and to choose the ones a document is bound to. Members who
   * have left are named as such where they are among them.
   */
  allMembers?: BoundMember[]
  /** Every label written so far, offered while typing. */
  allTags?: string[]
  canEdit?: boolean
  /** Where the document itself is served from, which depends on the door the reader comes through. */
  contentUrl?: (documentId: number) => string
}>(), {
  allMembers: undefined,
  allTags: undefined,
  canEdit: false,
  contentUrl: stationContentUrl,
})

const emit = defineEmits<{
  members: [documentId: number, memberIds: number[]]
  tags: [documentId: number, tags: string[]]
  remove: [document: MemberDocumentResponse]
}>()

const {t} = useI18n()

const tags = ref<string[]>([])
const members = ref<string[]>([])

watch(() => props.document, (document) => {
  tags.value = [...(document?.tags ?? [])]
  members.value = (document?.memberIds ?? []).map(String)
}, {immediate: true})

const memberOptions = computed(() => (props.allMembers ?? []).map(fromMember))

/** Whether the reader may remove the document and choose its members, which a seal takes from everybody. */
const canChange = computed(() => props.canEdit && !props.document?.sealed)

/**
 * Whom the document names: the members bound to it, marked where they have left, and the names of
 * members who were deleted while it was kept for them.
 */
const boundNames = computed(() => {
  const bound = (props.allMembers ?? [])
      .filter(member => props.document?.memberIds.includes(member.id) && member.name)
      .map(member => member.formerAt ? t('documents.formerMember', {name: member.name}) : member.name as string)
  const deleted = (props.document?.departedNames ?? []).map(name => t('documents.deletedMember', {name}))
  return [...bound, ...deleted]
})

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
  await downloadAuthed(props.contentUrl(props.document.id), props.document.fileName)
}
</script>

<template>
  <Modal v-model="modelValue" size="lg">
    <div v-if="props.document" class="space-y-4">
      <div class="space-y-1 pr-10">
        <SubHeader>{{ props.document.title }}</SubHeader>
        <MutedText size="sm" tag="p">
          {{ props.document.fileName }} · {{ formatSize(props.document.sizeBytes) }}
          · {{ formatDate(props.document.createdAt) }}
        </MutedText>
        <MutedText v-if="props.document.uploaderName" size="sm" tag="p" data-testid="document-uploader">
          {{ t('documents.uploadedBy', {name: props.document.uploaderName}) }}
        </MutedText>
      </div>

      <div class="flex items-center gap-2">
        <DownloadButton @click="download"/>
        <DeleteButton v-if="canChange" @click="emit('remove', props.document)"/>
      </div>

      <SealedVersionList v-if="props.document.sealed" :versions="props.document.sealedVersions"/>

      <FileView
          :source="props.contentUrl(props.document.id)"
          :title="props.document.title"
          :mime-type="props.document.mimeType"
      />

      <div class="grid gap-4 sm:grid-cols-2">
        <div class="space-y-1">
          <FieldLabel>{{ t('documents.boundMembers') }}</FieldLabel>
          <template v-if="canChange">
            <MemberSelectInput
                v-model:selected="members"
                multiple
                :members="memberOptions"
                :placeholder="t('documents.bindPlaceholder')"
            />
            <div v-for="name in props.document.departedNames" :key="name" class="text-sm">
              {{ t('documents.deletedMember', {name}) }}
            </div>
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
