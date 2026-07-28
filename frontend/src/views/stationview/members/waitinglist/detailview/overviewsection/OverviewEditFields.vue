/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { computed } from 'vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FormulaInput from '@/components/input/FormulaInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { WaitingListField } from '@/api/waitingList'
import type { MemberGroup } from '@/api/types'

const name = defineModel<string>('name', { required: true })
const description = defineModel<string>('description', { required: true })
const scoringFormula = defineModel<string>('scoringFormula', { required: true })
const confirmInterval = defineModel<number>('confirmInterval', { required: true })
const testingGroupId = defineModel<number | null>('testingGroupId', { required: true })
const joinGroupId = defineModel<number | null>('joinGroupId', { required: true })
const attendanceThreshold = defineModel<number>('attendanceThreshold', { required: true })

const props = defineProps<{
  fields: WaitingListField[]
  groups: MemberGroup[]
}>()

const { t } = useI18n()

const fieldInfos = computed(() => props.fields.map(f => ({ name: f.name, type: f.fieldType })))
</script>

<template>
  <div class="space-y-3">
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.name') }}</FieldLabel>
      <TextInput v-model="name" />
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.description') }}</FieldLabel>
      <TextAreaInput v-model="description" />
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.scoringFormula') }}</FieldLabel>
      <FormulaInput
        v-model="scoringFormula"
        :placeholder="t('waitingList.scoringFormulaPlaceholder')"
        :fields="fieldInfos"
      />
      <p class="text-xs text-(--text-muted)">{{ t('waitingList.scoringFormulaHint') }}</p>
    </div>
    <div class="space-y-1">
      <FieldLabel>{{ t('waitingList.confirmInterval') }}</FieldLabel>
      <NumberInput :model-value="confirmInterval" @update:model-value="confirmInterval = $event ?? 0" />
      <p class="text-xs text-(--text-muted)">{{ t('waitingList.confirmIntervalHint') }}</p>
    </div>
    <div class="grid gap-3 sm:grid-cols-2">
      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.testingGroup') }}</FieldLabel>
        <SelectInput
          :model-value="testingGroupId != null ? String(testingGroupId) : ''"
          @update:model-value="testingGroupId = $event ? Number($event) : null"
        >
          <option value="">{{ t('waitingList.noGroup') }}</option>
          <option v-for="g in groups" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.joinGroup') }}</FieldLabel>
        <SelectInput
          :model-value="joinGroupId != null ? String(joinGroupId) : ''"
          @update:model-value="joinGroupId = $event ? Number($event) : null"
        >
          <option value="">{{ t('waitingList.noGroup') }}</option>
          <option v-for="g in groups" :key="g.id" :value="String(g.id)">{{ g.name }}</option>
        </SelectInput>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('waitingList.attendanceThreshold') }}</FieldLabel>
        <NumberInput :model-value="attendanceThreshold" @update:model-value="attendanceThreshold = $event ?? 0" />
      </div>
    </div>
  </div>
</template>
