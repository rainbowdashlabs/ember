/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'

defineProps<{
  saving: boolean
  disabled: boolean
}>()

const emit = defineEmits<{
  cancel: []
  submit: []
}>()

const {t} = useI18n()
</script>

<template>
  <ButtonRow pair align="end">
    <SecondaryButton @click="emit('cancel')">{{ t('attendanceConfig.cancel') }}</SecondaryButton>
    <PrimaryButton :disabled="saving || disabled" @click="emit('submit')">
      {{ saving ? t('common.loading') : t('attendanceConfig.save') }}
    </PrimaryButton>
  </ButtonRow>
</template>
