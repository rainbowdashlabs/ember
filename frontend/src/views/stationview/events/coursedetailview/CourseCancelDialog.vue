/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'

/** Asks before a course is called off for good, with an optional reason the people signed up are told. */
defineProps<{
  busy?: boolean
}>()

const open = defineModel<boolean>({required: true})
const emit = defineEmits<{
  confirm: [reason: string | null]
}>()

const {t} = useI18n()
const reason = ref('')

watch(open, value => { if (value) reason.value = '' })
</script>

<template>
  <ConfirmDeleteModal
      v-model="open"
      :title="t('courses.cancelTitle')"
      :message="t('courses.cancelText')"
      :confirm-label="t('courses.cancel')"
      :busy="busy"
      confirm-test-id="course-cancel-confirm"
      @confirm="emit('confirm', reason.trim() === '' ? null : reason.trim())"
  >
    <FieldLabel for="course-cancel-reason">{{ t('courses.cancelReason') }}</FieldLabel>
    <TextInput id="course-cancel-reason" v-model="reason"/>
  </ConfirmDeleteModal>
</template>
