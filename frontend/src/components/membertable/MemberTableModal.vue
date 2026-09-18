/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ExportFieldPicker from '@/components/export/ExportFieldPicker.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import {memberTable as api} from '@/api'
import type {MemberTable, MemberTableHeader, MemberTablePreset} from '@/api/memberTable'
import {useMemberTableColumns} from '@/composables/useMemberTableColumns'
import {saveBlob} from '@/util/downloadAuthed'
import MemberTablePreview from './MemberTablePreview.vue'

const {t} = useI18n()

const modelValue = defineModel<boolean>({required: true})

const props = defineProps<{
  /** What this reader may put on the table, already cut to them by the server. */
  offered: MemberTableHeader[]
  /** Draws the table for the columns that are ticked. */
  draw: (columns: ReturnType<typeof useMemberTableColumns>['columns']['value']) => Promise<MemberTable>
  /** Fetches the same table as a file. */
  download: (
      columns: ReturnType<typeof useMemberTableColumns>['columns']['value'], format: 'csv' | 'pdf',
  ) => Promise<Blob>
  /** What the saved file is called, without its ending. */
  fileName: string
}>()

const {offered, selected, options, columns, toggle, select, apply} = useMemberTableColumns()
const table = ref<MemberTable | null>(null)
const presets = ref<MemberTablePreset[]>([])
const presetName = ref('')
const busy = ref(false)

watch(() => props.offered, value => {
  offered.value = value
}, {immediate: true})

watch(modelValue, async open => {
  if (!open) return
  table.value = null
  presets.value = await api.listPresets().catch(() => [])
})

/** Draws what is ticked, which is the same call the file is made from. */
async function preview() {
  busy.value = true
  try {
    table.value = await props.draw(columns.value)
  } finally {
    busy.value = false
  }
}

async function download(format: 'csv' | 'pdf') {
  busy.value = true
  try {
    saveBlob(await props.download(columns.value, format), `${props.fileName}.${format}`)
  } finally {
    busy.value = false
  }
}

async function savePreset() {
  if (!presetName.value.trim()) return
  const saved = await api.savePreset(presetName.value.trim(), columns.value)
  presets.value = [...presets.value.filter(p => p.id !== saved.id), saved].sort((a, b) => a.name.localeCompare(b.name))
  presetName.value = ''
}
</script>

<template>
  <Modal v-model="modelValue" size="lg">
    <div class="space-y-4">
      <SubHeader>{{ t('memberTable.title') }}</SubHeader>
      <MutedText tag="p" size="sm">{{ t('memberTable.hint') }}</MutedText>

      <ExportFieldPicker
          bulk
          :label="t('memberTable.selectColumns')"
          :options="options"
          :selected="selected"
          @toggle="toggle"
          @select="select"
      />

      <div v-if="presets.length > 0" class="space-y-2">
        <FieldLabel>{{ t('memberTable.savedSelections') }}</FieldLabel>
        <ButtonRow align="start" wrap>
          <SecondaryButton
              v-for="preset in presets"
              :key="preset.id"
              compact
              data-testid="member-table-preset"
              @click="apply(preset.columns)"
          >
            {{ preset.name }}
          </SecondaryButton>
        </ButtonRow>
      </div>

      <div class="flex items-end gap-2">
        <TextInput v-model="presetName" class="flex-1" :label="t('memberTable.saveAs')"/>
        <SecondaryButton :disabled="!presetName.trim() || columns.length === 0" @click="savePreset">
          {{ t('memberTable.save') }}
        </SecondaryButton>
      </div>

      <Spinner v-if="busy" size="sm"/>
      <MemberTablePreview v-else-if="table" :table="table"/>

      <ButtonRow align="end" wrap>
        <SecondaryButton @click="modelValue = false">{{ t('common.close') }}</SecondaryButton>
        <SecondaryButton :disabled="columns.length === 0" data-testid="member-table-preview" @click="preview">
          {{ t('memberTable.preview') }}
        </SecondaryButton>
        <SecondaryButton :disabled="columns.length === 0" data-testid="member-table-csv" @click="download('csv')">
          {{ t('memberTable.csv') }}
        </SecondaryButton>
        <PrimaryButton :disabled="columns.length === 0" data-testid="member-table-pdf" @click="download('pdf')">
          {{ t('memberTable.pdf') }}
        </PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
