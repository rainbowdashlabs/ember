/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import EventFieldValueInput from '@/components/input/EventFieldValueInput.vue'
import type {EventFieldEntry} from '@/api/types'
import type {BatchRow} from '@/api/events'
import {formatDate} from '@/util/format'

const {t} = useI18n()

const rows = defineModel<BatchRow[]>('rows', {required: true})

const props = defineProps<{
  fieldDefs: EventFieldEntry[]
}>()

const activeFields = () => props.fieldDefs.filter(f => f.name.trim())

function removeRow(index: number) {
  const updated = [...rows.value]
  updated.splice(index, 1)
  rows.value = updated
}

function updateRow(index: number, row: BatchRow) {
  const updated = [...rows.value]
  updated[index] = row
  rows.value = updated
}

function setAllColumn(fieldName: string, value: string) {
  rows.value = rows.value.map(row => ({
    ...row,
    fieldValues: {...(row.fieldValues ?? {}), [fieldName]: value},
  }))
}

function setAllName(value: string) {
  rows.value = rows.value.map(row => ({...row, name: value}))
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('batchCreate.stepTable') }}</SubHeader>

    <MutedText v-if="rows.length === 0" tag="div" size="sm">
      {{ t('batchCreate.noRows') }}
    </MutedText>

    <template v-else>
      <div class="flex gap-2 mb-2 items-end flex-wrap">
        <div class="space-y-1 min-w-32">
          <FieldLabel>{{ t('batchCreate.eventName') }} — {{ t('batchCreate.setAll') }}</FieldLabel>
          <TextInput model-value="" @update:model-value="setAllName($event ?? '')"/>
        </div>
        <div v-for="fd in activeFields()" :key="'setall-' + fd.name" class="space-y-1 min-w-32">
          <FieldLabel>{{ fd.name }} — {{ t('batchCreate.setAll') }}</FieldLabel>
          <EventFieldValueInput
              :field-type="fd.fieldType ?? 'STRING'"
              :config="fd.config"
              model-value=""
              @update:model-value="setAllColumn(fd.name, $event)"
          />
        </div>
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-sm border-collapse">
          <thead>
          <tr class="border-b border-(--border)">
            <th class="p-2 text-left">{{ t('batchCreate.date') }}</th>
            <th class="p-2 text-left">{{ t('batchCreate.eventName') }}</th>
            <th v-for="fd in activeFields()" :key="fd.name" class="p-2 text-left">{{ fd.name }}</th>
            <th class="p-2"></th>
          </tr>
          </thead>
          <tbody>
          <tr v-for="(row, index) in rows" :key="index" class="border-b border-(--border)">
            <td class="p-2 whitespace-nowrap">{{ formatDate(row.startTime) }}</td>
            <td class="p-2">
              <TextInput :model-value="row.name ?? ''" class="w-40"
                         @update:model-value="updateRow(index, {...row, name: $event})"/>
            </td>
            <td v-for="fd in activeFields()" :key="fd.name" class="p-2">
              <EventFieldValueInput
                  :field-type="fd.fieldType ?? 'STRING'"
                  :config="fd.config"
                  :model-value="row.fieldValues?.[fd.name] ?? ''"
                  @update:model-value="updateRow(index, {...row, fieldValues: {...(row.fieldValues ?? {}), [fd.name]: $event}})"
              />
            </td>
            <td class="p-2">
              <DeleteButton :label="t('batchCreate.removeRow')" @click="removeRow(index)"/>
            </td>
          </tr>
          </tbody>
        </table>
      </div>
    </template>
  </NeutralContainer>
</template>
