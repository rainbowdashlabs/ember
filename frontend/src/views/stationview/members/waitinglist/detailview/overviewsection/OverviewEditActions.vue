/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'

const isPublic = defineModel<boolean>('isPublic', {required: true})

defineProps<{
  canSave: boolean
  save: () => Promise<void>
}>()

const emit = defineEmits<{
  cancel: []
}>()

const { t } = useI18n()
</script>

<template>
  <div class="space-y-3">
    <div class="flex items-center justify-between">
      <div>
        <FieldLabel>{{ t('waitingList.isPublic') }}</FieldLabel>
        <p class="text-xs text-(--text-muted)">{{ t('waitingList.isPublicHint') }}</p>
      </div>
      <ToggleInput v-model="isPublic" />
    </div>
    <div class="flex justify-end gap-2">
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
      <SaveButton :disabled="!canSave" :action="save" />
    </div>
  </div>
</template>
