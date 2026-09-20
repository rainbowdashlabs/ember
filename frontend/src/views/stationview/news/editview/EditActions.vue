/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'

/** The two ways out of writing a news entry: leave it, or put it away. */
defineProps<{
  canSave: boolean
  save: () => Promise<void> | void
}>()

const emit = defineEmits<{cancel: []}>()

const {t} = useI18n()
</script>

<template>
  <div class="flex justify-end gap-3">
    <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
    <SaveButton :disabled="!canSave" :action="save"/>
  </div>
</template>
