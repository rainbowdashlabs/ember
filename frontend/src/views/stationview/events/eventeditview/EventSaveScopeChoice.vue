/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import RadioInput from '@/components/input/toggle/RadioInput.vue'
import DateInput from '@/components/input/datetime/DateInput.vue'
import {EditScopeKind} from '@/api/generated/schema'
import {todayIsoDate} from '@/util/format'

/** Which dates of a series a change applies to, and the date it applies to or from. */
const kind = defineModel<EditScopeKind>('kind', {required: true})
const date = defineModel<string>('date', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-2">
    <FieldLabel>{{ t('events.saveDialog.scope') }}</FieldLabel>
    <FieldLabel inline class="cursor-pointer">
      <RadioInput v-model="kind" :value="EditScopeKind.THIS"/>
      {{ t('events.saveDialog.scopeThis') }}
    </FieldLabel>
    <FieldLabel inline class="cursor-pointer">
      <RadioInput v-model="kind" :value="EditScopeKind.THIS_AND_LATER"/>
      {{ t('events.saveDialog.scopeLater') }}
    </FieldLabel>
    <FieldLabel inline class="cursor-pointer">
      <RadioInput v-model="kind" :value="EditScopeKind.ALL"/>
      {{ t('events.saveDialog.scopeAll') }}
    </FieldLabel>
    <div v-if="kind !== EditScopeKind.ALL" class="space-y-1">
      <FieldLabel>{{ t('events.saveDialog.date') }}</FieldLabel>
      <DateInput v-model="date" :min="todayIsoDate()"/>
      <p class="text-xs text-(--text-muted)">
        {{ kind === EditScopeKind.THIS ? t('events.saveDialog.thisHint') : t('events.saveDialog.laterHint') }}
      </p>
    </div>
  </div>
</template>
