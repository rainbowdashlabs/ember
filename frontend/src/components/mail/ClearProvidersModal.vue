/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'

/**
 * The one way to empty the provider list, which is why it asks first: a save can no longer do it
 * by accident, so this is the deliberate act it was separated out to be. Worded once for the
 * instance and the station mail settings alike.
 */
const show = defineModel<boolean>({required: true})

defineProps<{
  clearing: boolean
}>()

const emit = defineEmits<{
  confirm: []
}>()

const {t} = useI18n()
</script>

<template>
  <ConfirmDeleteModal
      v-model="show"
      :message="t('adminSettings.mailing.clearConfirm')"
      :confirm-label="t('adminSettings.mailing.clear')"
      :confirm-icon="['fas', 'trash']"
      :busy="clearing"
      @confirm="emit('confirm')"
  />
</template>
