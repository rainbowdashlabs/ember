/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import UserAvatar from '@/components/avatar/UserAvatar.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import LabelSelectInput from '@/components/input/select/LabelSelectInput.vue'

const {t} = useI18n()

const dummyLabels = [
  {id: 1, boardId: 1, name: 'Dringend', color: '#ec2929'},
  {id: 2, boardId: 1, name: 'Ausrüstung', color: '#3694FF'},
]

const dummySelectedLabels = [
  {id: 1, boardId: 1, name: 'Dringend', color: '#ec2929'},
]
</script>

<template>
  <div>
    <FieldLabel class="mb-1">{{ t('boards.labels') }}</FieldLabel>
    <LabelSelectInput :labels="dummyLabels" :selected="dummySelectedLabels"
                      :placeholder="t('boards.labelsPlaceholder')" :empty-text="t('boards.noLabelsFound')"/>
  </div>

  <div>
    <FieldLabel class="mb-1">{{ t('boards.priority') }}</FieldLabel>
    <div class="flex items-center gap-2 rounded-theme px-2 py-1 text-sm cursor-pointer hover:bg-(--bg-accent)">
      <font-awesome-icon :icon="['fas', 'angle-up']" class="text-orange-500"/>
      <span>{{ t('boards.priorityHigh') }}</span>
    </div>
  </div>

  <div>
    <FieldLabel class="mb-1">{{ t('boards.assignee') }}</FieldLabel>
    <div class="flex items-center gap-2 rounded-theme px-2 py-1 text-sm cursor-pointer hover:bg-(--bg-accent)">
      <UserAvatar :identity="{stationUid: 's1', memberUid: 'u1', displayTag: {name: 'Max Mustermann', color: ''}}"
                  size="sm"/>
      <span>Max Mustermann</span>
    </div>
  </div>

  <div>
    <FieldLabel class="mb-1">{{ t('boards.dueDate') }}</FieldLabel>
    <div class="rounded-theme px-2 py-1 text-sm cursor-pointer hover:bg-(--bg-accent)">
      20.06.2026
    </div>
  </div>

  <div class="border-t border-(--border) pt-4">
    <FieldLabel class="mb-1">{{ t('helpCenter.ticketDetail.customFieldExample') }}</FieldLabel>
    <TextInput model-value="3"/>
  </div>
</template>
