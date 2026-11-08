/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import DragList from '@/components/input/DragList.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import FieldLayoutPreview from '@/components/profilefields/FieldLayoutPreview.vue'
import type {FieldWidthName, PreviewField} from '@/components/profilefields/fieldLayout'
import type {MemberGroup} from '@/api/generated/schema'
import {moveWithin} from '@/util/reorder'
import FieldEditor from './FieldEditor.vue'
import {blankDraft, previewOf, type AttendanceFieldDraft} from './attendanceFieldDraft'

/**
 * The fields of an attendance sheet, each edited where it stands, as the questions of an appointment
 * are.
 *
 * <p>They were a list of names with a dialog behind each, which asked for the place in the sheet
 * as a number, and a picture of the sheet that could only be looked at. Now the list is the editor:
 * a field is written in its row, dragged to another place by its grip or in the preview, and made
 * narrower by its edge there. The whole list is saved at once.
 */
const fields = defineModel<AttendanceFieldDraft[]>('fields', {required: true})

const props = defineProps<{
  availableGroups: MemberGroup[]
  /** Writes every field as it stands; the button shows saving and saved. */
  saveFields: () => Promise<void>
}>()

const emit = defineEmits<{
  delete: [index: number]
}>()

const {t} = useI18n()

const canSave = computed(() => fields.value.every(field => field.name.trim() !== ''))

function addField() {
  fields.value = [...fields.value, blankDraft()]
}

function updateField(index: number, field: AttendanceFieldDraft) {
  fields.value = fields.value.map((existing, i) => (i === index ? field : existing))
}

function moveField(fromIndex: number, toIndex: number) {
  fields.value = moveWithin(fields.value, fromIndex, toIndex)
}

const previewFields = computed<PreviewField[]>(() => fields.value.map(previewOf))

/** Takes the width a field was dragged to in the preview into its settings. */
function resizeField(shown: PreviewField, width: FieldWidthName) {
  const index = previewFields.value.indexOf(shown)
  const field = fields.value[index]
  if (!field) return
  updateField(index, {...field, width})
}
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="attendance-fields">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('attendanceConfig.fields') }}</SectionHeader>
      <SecondaryButton :icon="['fas', 'plus']" @click="addField">
        {{ t('attendanceConfig.addField') }}
      </SecondaryButton>
    </div>

    <EmptyState v-if="fields.length === 0">{{ t('attendanceConfig.noFields') }}</EmptyState>

    <DragList :items="fields" :key-fn="(field, index) => field.id ?? `new-${index}`" @reorder="moveField">
      <template #default="{index}">
        <div class="mb-2" data-testid="attendance-field-row">
          <FieldEditor
              :available-groups="props.availableGroups"
              :model-value="fields[index]!"
              @update:model-value="updateField(index, $event)"
              @remove="emit('delete', index)"
          />
        </div>
      </template>
    </DragList>

    <FieldLayoutPreview
        :fields="previewFields"
        :hint="t('attendanceConfig.fieldsPreviewHint')"
        @reorder="moveField"
        @resize="resizeField"
    />

    <SaveButton :action="props.saveFields" :disabled="!canSave">
      {{ t('attendanceConfig.saveFields') }}
    </SaveButton>
  </NeutralContainer>
</template>
