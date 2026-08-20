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
import TextInput from '@/components/input/text/TextInput.vue'
import TagPicker from '@/components/input/TagPicker.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import FileInput from '@/components/input/FileInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {DocumentUpload} from '@/api/memberDocuments'
import type {StationMember} from '@/api/types'

/**
 * Putting a document in: the file, what it is called, and what is to become of it.
 */
const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  /** Whether the reader may keep a document from the members it belongs to. */
  canHide?: boolean
  /** The members it can be put on straight away. Absent where the profile already says who. */
  members?: StationMember[]
  /** Every label written so far, offered while typing. */
  allTags?: string[]
}>()

const emit = defineEmits<{
  upload: [upload: DocumentUpload]
}>()

const {t} = useI18n()

const file = ref<File | null>(null)
const title = ref('')
const hidden = ref(false)
const keepOnArchive = ref(false)
const tags = ref<string[]>([])
const memberIds = ref<string[]>([])
const saving = ref(false)

const memberOptions = computed(() => (props.members ?? [])
    .map(member => ({value: String(member.id), label: member.name ?? String(member.id)})))

watch(modelValue, (open) => {
  if (open) return
  file.value = null
  title.value = ''
  hidden.value = false
  keepOnArchive.value = false
  tags.value = []
  memberIds.value = []
  saving.value = false
})

function onFile(chosen: File) {
  file.value = chosen
  if (!title.value) title.value = chosen.name
}

function submit() {
  if (!file.value) return
  saving.value = true
  emit('upload', {
    file: file.value,
    title: title.value.trim() || file.value.name,
    hidden: props.canHide ? hidden.value : false,
    keepOnArchive: keepOnArchive.value,
    tags: tags.value,
    memberIds: memberIds.value.map(Number),
  })
  saving.value = false
}
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader>{{ t('documents.uploadTitle') }}</SubHeader>

      <div class="space-y-1">
        <FieldLabel>{{ t('documents.file') }}</FieldLabel>
        <FileInput @select="onFile"/>
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('documents.title') }}</FieldLabel>
        <TextInput v-model="title" :placeholder="t('documents.titlePlaceholder')"/>
      </div>

      <div v-if="memberOptions.length > 0" class="space-y-1">
        <FieldLabel>{{ t('documents.boundMembers') }}</FieldLabel>
        <MultiSelectDropdown
            v-model="memberIds"
            :options="memberOptions"
            :placeholder="t('documents.bindPlaceholder')"
            searchable
        />
      </div>

      <div class="space-y-1">
        <FieldLabel>{{ t('documents.tags') }}</FieldLabel>
        <TagPicker v-model="tags" :suggestions="props.allTags" :placeholder="t('documents.tagsPlaceholder')"/>
      </div>

      <div class="flex items-center gap-2">
        <ToggleInput v-model="keepOnArchive"/>
        <span class="text-sm">{{ t('documents.keepOnArchive') }}</span>
      </div>
      <MutedText size="sm">{{ t('documents.keepOnArchiveHint') }}</MutedText>

      <template v-if="props.canHide">
        <div class="flex items-center gap-2">
          <ToggleInput v-model="hidden"/>
          <span class="text-sm">{{ t('documents.hide') }}</span>
        </div>
        <MutedText size="sm">{{ t('documents.hideHint') }}</MutedText>
      </template>

      <div class="flex justify-end gap-2">
        <SecondaryButton @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="!file || saving" @click="submit">{{ t('documents.upload') }}</PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
