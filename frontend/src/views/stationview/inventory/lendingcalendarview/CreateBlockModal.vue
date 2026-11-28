/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, useId, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import * as lending from '@/api/lending'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {formatDate} from '@/util/format'

/** Sets the days a drag covered aside, for one product or the whole station, with a reason. */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  from: string
  to: string
  /** The product to block, or null for the whole station. */
  inventoryId: number | null
  /** What is blocked, as the reader knows it. */
  label: string
}>()

const emit = defineEmits<{
  created: []
}>()

const {t} = useI18n()
const reasonId = useId()
const reason = ref('')
watch(open, value => { if (value) reason.value = '' })

const {run, running, failure} = useAsyncAction(async () => {
  await lending.createBlock({inventoryId: props.inventoryId, itemId: null, blockFrom: props.from, blockTo: props.to, reason: reason.value})
  open.value = false
  emit('created')
})
</script>

<template>
  <Modal v-model="open" data-confirm-always>
    <div class="space-y-3" data-testid="lending-calendar-create-block">
      <SectionHeader>{{ t('lendingCalendarPage.blockTitle') }}</SectionHeader>
      <MutedText tag="p">{{ t('lendingCalendarPage.blockWhat', {what: label, from: formatDate(from), to: formatDate(to)}) }}</MutedText>
      <FieldLabel :for="reasonId">{{ t('lendingCalendarPage.reason') }}</FieldLabel>
      <TextInput :id="reasonId" v-model="reason" :placeholder="t('lendingCalendarPage.reasonPlaceholder')"/>
      <FailureAlert :failure="failure"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :icon="['fas', 'calendar-xmark']" :disabled="running" @click="run()">{{ t('lendingCalendarPage.block') }}</PrimaryButton>
      </ButtonRow>
    </div>
  </Modal>
</template>
