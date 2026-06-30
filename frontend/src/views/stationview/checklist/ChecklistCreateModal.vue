/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import type {ChecklistCreateRequest, MemberGroup, StationMember, UserTag} from '@/api/types'

const visible = defineModel<boolean>({required: true})

const props = defineProps<{
  creating: boolean
  groups: MemberGroup[]
  tags: UserTag[]
  members: StationMember[]
}>()

const emit = defineEmits<{
  (e: 'submit', payload: ChecklistCreateRequest): void
}>()

const {t} = useI18n()

const name = ref('')
const description = ref('')
const columns = ref<{label: string; description: string}[]>([{label: '', description: ''}])
const selectedUserTypes = ref<string[]>([])
const selectedGroupIds = ref<number[]>([])
const selectedTagIds = ref<number[]>([])
const selectedMemberIds = ref<number[]>([])
const mode = ref<'AND' | 'OR'>('AND')

function addColumn() {
  columns.value.push({label: '', description: ''})
}

function removeColumn(idx: number) {
  if (columns.value.length === 1) return
  columns.value.splice(idx, 1)
}

function reset() {
  name.value = ''
  description.value = ''
  columns.value = [{label: '', description: ''}]
  selectedUserTypes.value = []
  selectedGroupIds.value = []
  selectedTagIds.value = []
  selectedMemberIds.value = []
  mode.value = 'AND'
}

function cancel() {
  visible.value = false
  reset()
}

function submit() {
  const cleanColumns = columns.value
      .map(c => ({label: c.label.trim(), description: c.description.trim()}))
      .filter(c => c.label.length > 0)
  if (!name.value.trim() || cleanColumns.length === 0) return
  emit('submit', {
    name: name.value.trim(),
    description: description.value.trim(),
    columns: cleanColumns,
    restriction: {
      userTypes: selectedUserTypes.value,
      groupIds: selectedGroupIds.value,
      tagIds: selectedTagIds.value,
      memberIds: selectedMemberIds.value,
      mode: mode.value,
    },
  })
}

watch(visible, (value, previous) => {
  if (previous && !value) reset()
})
</script>

<template>
  <Modal v-model="visible" size="xl">
    <div class="space-y-4">
      <SubHeader>{{ t('checklist.createTitle') }}</SubHeader>

      <div>
        <FieldLabel>{{ t('checklist.name') }}</FieldLabel>
        <TextInput v-model="name" :placeholder="t('checklist.namePlaceholder')"/>
      </div>

      <div>
        <FieldLabel>{{ t('checklist.description') }}</FieldLabel>
        <TextAreaInput v-model="description" :placeholder="t('checklist.descriptionPlaceholder')" rows="2"/>
      </div>

      <div>
        <div class="flex items-center justify-between mb-2">
          <FieldLabel>{{ t('checklist.columns') }}</FieldLabel>
          <SecondaryButton @click="addColumn">
            <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
            {{ t('checklist.addColumn') }}
          </SecondaryButton>
        </div>
        <div class="space-y-2">
          <div v-for="(col, idx) in columns" :key="idx" class="flex items-start gap-2">
            <div class="flex-1 grid sm:grid-cols-2 gap-2">
              <TextInput v-model="col.label" :placeholder="t('checklist.columnLabelPlaceholder')"/>
              <TextInput v-model="col.description" :placeholder="t('checklist.columnDescriptionPlaceholder')"/>
            </div>
            <IconButton
                :icon="['fas', 'trash']"
                :label="t('checklist.removeColumn')"
                :disabled="columns.length === 1"
                @click="removeColumn(idx)"
            />
          </div>
        </div>
      </div>

      <div>
        <FieldLabel>{{ t('checklist.memberSet') }}</FieldLabel>
        <p class="text-xs text-(--text-muted) mb-2">{{ t('checklist.memberSetHelp') }}</p>
        <RestrictionPicker
            v-model:selected-user-types="selectedUserTypes"
            v-model:selected-group-ids="selectedGroupIds"
            v-model:selected-tag-ids="selectedTagIds"
            v-model:selected-member-ids="selectedMemberIds"
            v-model:mode="mode"
            :groups="groups"
            :tags="tags"
            :members="members"
            :show-members="true"
            :show-mode="true"
        />
      </div>

      <div class="flex justify-end gap-2 pt-2">
        <SecondaryButton @click="cancel">{{ t('checklist.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="creating || !name.trim()" @click="submit">
          {{ t('checklist.save') }}
        </PrimaryButton>
      </div>
    </div>
  </Modal>
</template>
