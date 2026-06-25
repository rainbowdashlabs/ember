/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'

const {t} = useI18n()

const showSavePreset = defineModel<boolean>('showSavePreset', {required: true})
const presetName = defineModel<string>('presetName', {required: true})

defineProps<{
  canPreview: boolean
  previewing: boolean
  savePreset: () => Promise<void>
}>()

const emit = defineEmits<{
  preview: []
}>()
</script>

<template>
  <div class="flex items-center gap-2 flex-wrap">
    <PrimaryButton :icon="['fas', 'eye']" :disabled="!canPreview || previewing" @click="emit('preview')">
      {{ previewing ? t('common.loading') : t('attendanceReport.preview') }}
    </PrimaryButton>
    <SecondaryButton v-if="!showSavePreset" :icon="['fas', 'copy']" :disabled="!canPreview" @click="showSavePreset = true">
      {{ t('attendanceReport.savePreset') }}
    </SecondaryButton>
    <template v-if="showSavePreset">
      <TextInput v-model="presetName" :placeholder="t('attendanceReport.presetName')" class="w-48"/>
      <SaveButton :disabled="!presetName" :action="savePreset"/>
      <SecondaryButton @click="showSavePreset = false">{{ t('common.cancel') }}</SecondaryButton>
    </template>
  </div>
</template>
