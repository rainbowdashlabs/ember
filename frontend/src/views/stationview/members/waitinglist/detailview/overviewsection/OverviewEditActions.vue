/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import SaveButton from '@/components/button/SaveButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'

const isPublic = defineModel<boolean>('isPublic', {required: true})
const sendsMail = defineModel<boolean>('sendsMail', {required: true})

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
    <ToggleSetting
      v-model="isPublic"
      :hint="t('waitingList.isPublicHint')"
      :label="t('waitingList.isPublic')"
      data-testid="waitlist-public-toggle"
    />
    <ToggleSetting
      v-model="sendsMail"
      :hint="t('waitingList.sendsMailHint')"
      :label="t('waitingList.sendsMail')"
      data-testid="waitlist-mail-toggle"
    />
    <div class="flex justify-end gap-2">
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
      <SaveButton :disabled="!canSave" :action="save" />
    </div>
  </div>
</template>
