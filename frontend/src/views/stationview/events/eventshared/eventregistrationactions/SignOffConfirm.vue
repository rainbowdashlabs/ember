/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'

/**
 * The question asked before somebody is signed off an appointment, worded once for the four
 * places that ask it.
 *
 * <p>A place given up is gone: whoever wants it back joins the queue again, and where the list is
 * already full that is the end of it.
 */
const open = defineModel<boolean>({required: true})

defineProps<{
  busy: boolean
}>()

const emit = defineEmits<{
  confirm: []
}>()

const {t} = useI18n()
</script>

<template>
  <ConfirmDeleteModal
      v-model="open"
      :title="t('eventsUpcoming.signOffConfirmTitle')"
      :message="t('eventsUpcoming.signOffConfirmBody')"
      :confirm-label="t('eventsUpcoming.decline')"
      :busy="busy"
      confirm-test-id="confirm-sign-off"
      @confirm="emit('confirm')"
  />
</template>
