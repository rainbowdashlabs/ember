/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

/** The buttons at the foot of the form editor: leave without saving, or save the form. */
defineProps<{
  save: () => Promise<unknown> | unknown
}>()

const emit = defineEmits<{
  cancel: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="flex justify-end gap-3">
    <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    <SaveButton :action="save"/>
  </div>
</template>
