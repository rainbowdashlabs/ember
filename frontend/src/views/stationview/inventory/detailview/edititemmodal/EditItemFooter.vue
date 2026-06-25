/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

defineProps<{
  saveDisabled: boolean
  save: () => Promise<void> | void
}>()

const emit = defineEmits<{
  cancel: []
}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex justify-end gap-3">
    <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    <SaveButton :disabled="saveDisabled" :action="save"/>
  </div>
</template>
