/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {type RestrictionSelection, emptyRestriction} from '@/components/input/restriction'
import ChecklistFormModal from './checklistmodals/ChecklistFormModal.vue'
import ChecklistColumnsEditor from './checklistmodals/ChecklistColumnsEditor.vue'
import ChecklistMembershipEditor from './checklistmodals/ChecklistMembershipEditor.vue'
import type {ChecklistColumnDraft, ChecklistCreateRequest, ChecklistSourceRequest} from '@/api/checklists'
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
const follows = ref<'FILTER' | 'EVENT'>('FILTER')
const occurrence = ref<ChecklistSourceRequest | null>(null)

/**
 * A list that is meant to follow an evening but names none would be created following nothing, so
 * the choice has to be finished before it can be saved.
 */
const membershipIncomplete = computed(() => follows.value === 'EVENT' && occurrence.value === null)

function reset() {
  name.value = ''
  description.value = ''
  columns.value = [{label: '', description: ''}]
  restriction.value = emptyRestriction()
  follows.value = 'FILTER'
  occurrence.value = null
}

function submit() {
  const cleanColumns = columns.value
      .map(c => ({label: c.label.trim(), description: c.description.trim()}))
      .filter(c => c.label.length > 0)
  if (!name.value.trim() || cleanColumns.length === 0 || membershipIncomplete.value) return
  const followsEvent = follows.value === 'EVENT' && occurrence.value !== null
  emit('submit', {
    name: name.value.trim(),
    description: description.value.trim(),
    columns: cleanColumns,
    restriction: followsEvent
        ? {userTypes: [], groupIds: [], tagIds: [], memberIds: [], mode: restriction.value.mode}
        : {
          userTypes: restriction.value.userTypes,
          groupIds: restriction.value.groupIds,
          tagIds: restriction.value.tagIds,
          memberIds: restriction.value.memberIds,
          mode: restriction.value.mode,
        },
    ...(followsEvent && occurrence.value ? {source: occurrence.value} : {}),
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
      :submit-disabled="creating || !name.trim() || membershipIncomplete"
      @submit="submit"
  >
    <ChecklistColumnsEditor v-model="columns"/>

    <ChecklistMembershipEditor
        v-model:follows="follows"
        v-model:restriction="restriction"
        v-model:occurrence="occurrence"
        :groups="groups"
        :tags="tags"
        :members="members"
    />
  </ChecklistFormModal>
</template>
