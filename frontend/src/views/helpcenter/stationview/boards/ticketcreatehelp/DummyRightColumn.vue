/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MemberSelectInput from '@/components/input/select/MemberSelectInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const {t} = useI18n()

const dummyMembers = [
    {id: 1, memberUid: 'u1', stationUid: 's1', name: 'Max Mustermann'},
    {id: 2, memberUid: 'u2', stationUid: 's1', name: 'Lisa Schmidt'},
    {id: 3, memberUid: 'u3', stationUid: 's1', name: 'Tom Müller'},
]
</script>

<template>
  <div class="space-y-4">
    <div>
      <FieldLabel class="mb-1">{{ t('boards.lanes') }}</FieldLabel>
      <SelectInput model-value="1" class="w-full">
        <option value="1">Backlog</option>
        <option value="2">{{ t('helpCenter.ticketCreate.laneToDo') }}</option>
      </SelectInput>
    </div>

    <div>
      <FieldLabel class="mb-1">{{ t('boards.priority') }}</FieldLabel>
      <SelectInput model-value="HIGH" class="w-full">
        <option value="LOWEST">{{ t('boards.priorityLowest') }}</option>
        <option value="LOW">{{ t('boards.priorityLow') }}</option>
        <option value="MEDIUM">{{ t('boards.priorityMedium') }}</option>
        <option value="HIGH">{{ t('boards.priorityHigh') }}</option>
        <option value="HIGHEST">{{ t('boards.priorityHighest') }}</option>
      </SelectInput>
    </div>

    <div>
      <FieldLabel class="mb-1">{{ t('boards.assignee') }}</FieldLabel>
      <MemberSelectInput model-value="1" :members="dummyMembers" :placeholder="t('boards.unassigned')" />
    </div>

    <div>
      <FieldLabel class="mb-1">{{ t('boards.dueDate') }}</FieldLabel>
      <DateInput model-value="2026-06-20" />
    </div>

    <div class="flex gap-2">
      <SecondaryButton class="flex-1">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton class="flex-1">{{ t('common.create') }}</PrimaryButton>
    </div>
  </div>
</template>
