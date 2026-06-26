/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const required = defineModel<boolean>('required', {required: true})
const readonly = defineModel<boolean>('readonly', {required: true})
const notifyOnChange = defineModel<boolean>('notifyOnChange', {required: true})
const overview = defineModel<boolean>('overview', {required: true})
const keepOnArchive = defineModel<boolean>('keepOnArchive', {required: true})

const {t} = useI18n()

function onReadonlyChange(v: boolean) {
  if (v) required.value = false
}
</script>

<template>
  <div class="space-y-4">
    <div class="flex items-center justify-between">
      <label class="text-sm font-medium" :class="{ 'opacity-50': readonly }">{{ t('membersConfig.fieldRequired') }}</label>
      <ToggleInput v-model="required" :disabled="readonly"/>
    </div>
    <div class="space-y-1">
      <div class="flex items-center justify-between">
        <label class="text-sm font-medium">{{ t('membersConfig.fieldReadonly') }}</label>
        <ToggleInput v-model="readonly" @update:model-value="onReadonlyChange"/>
      </div>
      <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldReadonlyHint') }}</p>
    </div>
    <div class="space-y-1">
      <div class="flex items-center justify-between">
        <label class="text-sm font-medium">{{ t('membersConfig.fieldNotifyOnChange') }}</label>
        <ToggleInput v-model="notifyOnChange"/>
      </div>
      <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldNotifyOnChangeHint') }}</p>
    </div>
    <div class="flex items-center justify-between">
      <label class="text-sm font-medium">{{ t('membersConfig.fieldOverview') }}</label>
      <ToggleInput v-model="overview"/>
    </div>
    <div class="flex items-center justify-between">
      <div>
        <label class="text-sm font-medium">{{ t('membersConfig.fieldKeepOnArchive') }}</label>
        <p class="text-xs text-(--text-muted)">{{ t('membersConfig.fieldKeepOnArchiveHint') }}</p>
      </div>
      <ToggleInput v-model="keepOnArchive"/>
    </div>
  </div>
</template>
