/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'

/**
 * The question asked before a profile question is deleted, for a station's and an association's alike.
 *
 * Where other questions are shown only under this one, it names them and its button removes their
 * condition in the same step, so the deletion is never refused for them. They keep their answers and
 * are shown to everybody afterwards.
 */
const {t} = useI18n()

const modelValue = defineModel<boolean>({required: true})

defineProps<{
  /** The question about to go. */
  name: string
  /** The questions shown only under it, by name. */
  dependents: string[]
}>()

const emit = defineEmits<{
  confirm: []
}>()
</script>

<template>
  <ConfirmDeleteModal
      v-model="modelValue"
      :message="t('membersConfig.deleteConfirm', {name})"
      :confirm-label="dependents.length > 0 ? t('membersConfig.deleteUnlinkConfirm') : undefined"
      @confirm="emit('confirm')"
  >
    <div v-if="dependents.length > 0" class="space-y-2 text-sm" data-testid="field-dependents">
      <p>{{ t('membersConfig.deleteDependentsIntro', {name}) }}</p>
      <ul class="list-disc pl-5">
        <li v-for="dependent in dependents" :key="dependent">{{ dependent }}</li>
      </ul>
      <p>{{ t('membersConfig.deleteDependentsAfter') }}</p>
    </div>
  </ConfirmDeleteModal>
</template>
