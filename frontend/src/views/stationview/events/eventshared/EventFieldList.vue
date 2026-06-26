/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EventFieldEditor from '@/components/input/EventFieldEditor.vue'
import type {AttendanceTemplateField, EventFieldEntry, MemberGroup, StationMember} from '@/api/types'

const fields = defineModel<EventFieldEntry[]>('fields', {required: true})

defineProps<{
  attendanceFields?: AttendanceTemplateField[]
  showValue?: boolean
  allMembers?: StationMember[]
  groups?: MemberGroup[]
  groupMembers?: Map<number, StationMember[]>
}>()

const {t} = useI18n()

const quickFields = [
  {name: 'Ort', fieldType: 'STRING', overview: true, isPublic: true},
  {name: 'Treffpunkt', fieldType: 'STRING', overview: true, isPublic: true},
  {name: 'Thema', fieldType: 'STRING', overview: true, isPublic: false},
]

const existingNames = computed(() => new Set(fields.value.map(f => f.name.toLowerCase())))

function addQuickField(qf: typeof quickFields[number]) {
  fields.value = [...fields.value, {
    name: qf.name, fieldType: qf.fieldType, config: {}, value: '', overview: qf.overview, attendanceFieldId: null, isPublic: qf.isPublic,
  }]
}

function addField() {
  fields.value = [...fields.value, {name: '', fieldType: 'STRING', config: {}, value: '', overview: false, attendanceFieldId: null}]
}

function removeField(index: number) {
  const updated = [...fields.value]
  updated.splice(index, 1)
  fields.value = updated
}

function updateField(index: number, field: EventFieldEntry) {
  const updated = [...fields.value]
  updated[index] = field
  fields.value = updated
}
</script>

<template>
  <div class="flex items-center justify-between">
    <SubHeader>{{ t('events.eventFields') }}</SubHeader>
    <div class="flex items-center gap-2">
      <SecondaryButton
          v-for="qf in quickFields"
          :key="qf.name"
          :disabled="existingNames.has(qf.name.toLowerCase())"
          class="!py-1 !px-2 !text-xs"
          @click="addQuickField(qf)"
      >
        + {{ qf.name }}
      </SecondaryButton>
      <SecondaryButton :icon="['fas', 'plus']" @click="addField">
        {{ t('eventFields.addField') }}
      </SecondaryButton>
    </div>
  </div>
  <MutedText v-if="fields.length === 0" tag="div" size="sm" class="py-2">
    {{ t('events.noFields') }}
  </MutedText>
  <EventFieldEditor
      v-for="(field, index) in fields"
      :key="index"
      :model-value="field"
      :attendance-fields="attendanceFields"
      :show-value="showValue"
      :all-members="allMembers"
      :groups="groups"
      :group-members="groupMembers"
      @update:model-value="updateField(index, $event)"
      @remove="removeField(index)"
  />
</template>
