/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import ButtonRow from '@/components/button/ButtonRow.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import MultiSelectInput from '@/components/input/select/MultiSelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import * as lending from '@/api/lending'
import {useAsyncAction} from '@/composables/useAsyncAction'
import type {OfferRow} from './offerRows'

/**
 * What can be done with every selected row at once: offer to all partners, offer to chosen partners,
 * or withdraw. Withdrawing a product removes its rule and the offering rules of the kinds and pieces
 * the offer page lists under it; single pieces of an inventory of copies keep their rules, which live on
 * the inventory's page. Withdrawing a kind or a piece holds it back.
 */
const props = defineProps<{
  rows: OfferRow[]
  partners: {value: string; label: string}[]
  /** The rows below each product, so withdrawing a product also takes back what its kinds and pieces grant. */
  childrenOf: (row: OfferRow) => OfferRow[]
}>()

const emit = defineEmits<{
  done: []
}>()

const {t} = useI18n()
const pickerOpen = ref(false)
const chosen = ref<string[]>([])
const pickerId = useId()

const {run, running, failure} = useAsyncAction(async (change: (row: OfferRow) => Promise<unknown>) => {
  for (const row of props.rows) await change(row)
  pickerOpen.value = false
  emit('done')
})

function offerToAll() {
  void run(row => lending.setShare(row.target, row.id, {grant: 'GRANT', scope: 'ALL_PARTNERS', partnerIds: []}))
}

function offerToChosen() {
  const partnerIds = chosen.value.map(Number)
  void run(row => lending.setShare(row.target, row.id, {grant: 'GRANT', scope: 'SPECIFIC', partnerIds}))
}

async function withdrawOne(row: OfferRow) {
  if (row.target !== 'inventory') {
    await lending.setShare(row.target, row.id, {grant: 'WITHHOLD', scope: 'ALL_PARTNERS', partnerIds: []})
    return
  }
  await lending.removeShare(row.target, row.id)
  for (const child of props.childrenOf(row)) {
    if (child.share?.grant === 'GRANT') await lending.removeShare(child.target, child.id)
  }
}

function withdraw() {
  void run(withdrawOne)
}
</script>

<template>
  <div class="space-y-2 rounded-theme border border-(--border) p-3" data-testid="lending-offer-bulk">
    <MutedText tag="p">{{ rows.length === 1 ? t('lendingOffer.selectedOne') : t('lendingOffer.selected', {count: rows.length}) }}</MutedText>
    <ButtonRow>
      <PrimaryButton :icon="['fas', 'share-nodes']" :disabled="running" @click="offerToAll">{{ t('lendingOffer.bulkAll') }}</PrimaryButton>
      <SecondaryButton :icon="['fas', 'users']" :disabled="running" @click="chosen = []; pickerOpen = true">{{ t('lendingOffer.bulkSome') }}</SecondaryButton>
      <ErrorButton :icon="['fas', 'ban']" :disabled="running" data-confirm-always @click="withdraw">{{ t('lendingOffer.bulkWithdraw') }}</ErrorButton>
    </ButtonRow>
    <FailureAlert :failure="failure"/>
    <Modal v-model="pickerOpen" data-confirm-always>
      <div class="space-y-3">
        <SectionHeader>{{ t('lendingOffer.bulkSome') }}</SectionHeader>
        <FieldLabel :for="pickerId">{{ t('lendingOffer.partners') }}</FieldLabel>
        <MultiSelectInput :id="pickerId" v-model="chosen" :options="partners"/>
        <ButtonRow pair align="end">
          <SecondaryButton @click="pickerOpen = false">{{ t('common.cancel') }}</SecondaryButton>
          <PrimaryButton :disabled="running || chosen.length === 0" @click="offerToChosen">{{ t('lendingOffer.bulkApply') }}</PrimaryButton>
        </ButtonRow>
      </div>
    </Modal>
  </div>
</template>
