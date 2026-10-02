/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

/**
 * What the question says of itself, which holds for everybody asked it. Who may write the answer is
 * one of them: it is the question's own and not something one audience is told and another is not.
 */
const required = defineModel<boolean>('required', {required: true})
const readonly = defineModel<boolean>('readonly', {required: true})
const notifyOnChange = defineModel<boolean>('notifyOnChange', {required: true})
const overview = defineModel<boolean>('overview', {required: true})
const keepOnArchive = defineModel<boolean>('keepOnArchive', {required: true})

/**
 * Whether the answer is worked out rather than given.
 *
 * <p>A worked out answer is nobody's to write, so expecting one, locking one and reporting a change
 * to one are all questions about something that cannot happen. They are left out rather than shown
 * switched off, because a switch that does nothing still reads as a choice.
 */
const props = withDefaults(defineProps<{calculated?: boolean}>(), {calculated: false})

const {t} = useI18n()
const labelId = useId()
</script>

<template>
  <div class="space-y-4">
    <template v-if="!props.calculated">
      <div class="space-y-1">
        <div class="flex items-center justify-between">
          <span :id="`${labelId}-required`" class="text-sm font-medium">{{ t('membersConfig.fieldRequired') }}</span>
          <ToggleInput v-model="required" :aria-labelledby="`${labelId}-required`"/>
        </div>
        <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldRequiredHint') }}</p>
      </div>
      <div class="space-y-1">
        <div class="flex items-center justify-between">
          <span :id="`${labelId}-readonly`" class="text-sm font-medium">{{ t('membersConfig.fieldReadonly') }}</span>
          <ToggleInput v-model="readonly" :aria-labelledby="`${labelId}-readonly`"/>
        </div>
        <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldReadonlyHint') }}</p>
      </div>
      <div class="space-y-1">
        <div class="flex items-center justify-between">
          <span :id="`${labelId}-notify`" class="text-sm font-medium">{{ t('membersConfig.fieldNotifyOnChange') }}</span>
          <ToggleInput v-model="notifyOnChange" :aria-labelledby="`${labelId}-notify`"/>
        </div>
        <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldNotifyOnChangeHint') }}</p>
      </div>
    </template>
    <div class="flex items-center justify-between">
      <span :id="`${labelId}-overview`" class="text-sm font-medium">{{ t('membersConfig.fieldOverview') }}</span>
      <ToggleInput v-model="overview" :aria-labelledby="`${labelId}-overview`"/>
    </div>
    <div class="flex items-center justify-between">
      <div>
        <span :id="`${labelId}-keep`" class="text-sm font-medium">{{ t('membersConfig.fieldKeepOnArchive') }}</span>
        <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldKeepOnArchiveHint') }}</p>
      </div>
      <ToggleInput v-model="keepOnArchive" :aria-labelledby="`${labelId}-keep`"/>
    </div>
  </div>
</template>
