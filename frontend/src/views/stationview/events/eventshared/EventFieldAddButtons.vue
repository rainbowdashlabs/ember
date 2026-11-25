/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import {QUICK_FIELDS, type QuickField} from './eventQuestions'

/**
 * The ways to add a field to an appointment: the fields nearly every appointment has, one click each,
 * and an empty one. A quick field the appointment already has is not offered a second time, and one
 * the attendance sheet has a field of its own for is left to the sheet's button, which adds it tied
 * to the sheet.
 */
const props = defineProps<{
  /** The names of the fields already there, in lower case. */
  existingNames: Set<string>
  /** The names of the attendance sheet's fields, in lower case. */
  sheetNames?: Set<string>
}>()

const offered = computed(() => QUICK_FIELDS.filter(qf => !props.sheetNames?.has(qf.name.toLowerCase())))

const emit = defineEmits<{
  add: []
  addQuick: [field: QuickField]
}>()

const {t} = useI18n()
</script>

<template>
  <SecondaryButton
      v-for="qf in offered"
      :key="qf.name"
      :disabled="existingNames.has(qf.name.toLowerCase())"
      class="!py-1 !px-2 !text-xs"
      @click="emit('addQuick', qf)"
  >
    + {{ qf.name }}
  </SecondaryButton>
  <SecondaryButton :icon="['fas', 'plus']" @click="emit('add')">
    {{ t('eventFields.addField') }}
  </SecondaryButton>
</template>
