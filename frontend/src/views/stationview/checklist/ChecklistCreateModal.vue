/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import {type RestrictionSelection, emptyRestriction} from '@/components/input/restriction'
import ChecklistFormModal from './checklistmodals/ChecklistFormModal.vue'
import ChecklistColumnsEditor from './checklistmodals/ChecklistColumnsEditor.vue'
import type {ChecklistColumnDraft, ChecklistCreateRequest} from '@/api/checklists'
import type {MemberGroup, StationMember, UserTag} from '@/api/types'

const visible = defineModel<boolean>({required: true})

defineProps<{
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
const columns = ref<ChecklistColumnDraft[]>([{label: '', description: ''}])
const restriction = ref<RestrictionSelection>(emptyRestriction())

function reset() {
  name.value = ''
  description.value = ''
  columns.value = [{label: '', description: ''}]
  restriction.value = emptyRestriction()
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
      userTypes: restriction.value.userTypes,
      groupIds: restriction.value.groupIds,
      tagIds: restriction.value.tagIds,
      memberIds: restriction.value.memberIds,
      mode: restriction.value.mode,
    },
  })
}

watch(visible, (value, previous) => {
  if (previous && !value) reset()
})
</script>

<template>
  <ChecklistFormModal
      v-model="visible"
      v-model:name="name"
      v-model:description="description"
      :title="t('checklist.createTitle')"
      size="xl"
      :submit-disabled="creating || !name.trim()"
      @submit="submit"
  >
    <ChecklistColumnsEditor v-model="columns"/>

    <div>
      <FieldLabel>{{ t('checklist.memberSet') }}</FieldLabel>
      <p class="text-xs text-(--text-muted) mb-2">{{ t('checklist.memberSetHelp') }}</p>
      <RestrictionPicker
          v-model="restriction"
          :groups="groups"
          :tags="tags"
          :members="members"
          :show-members="true"
          :show-mode="true"
      />
    </div>
  </ChecklistFormModal>
</template>
