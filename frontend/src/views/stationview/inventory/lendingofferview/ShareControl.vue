/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref, useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import LinkButton from '@/components/button/LinkButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import LendingShareModal from '@/components/lending/LendingShareModal.vue'
import * as lending from '@/api/lending'
import type {OfferShare} from '@/api/generated/schema'
import type {ShareTarget} from '@/api/lending'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {choiceOf, choicesFor, type ShareChoice} from './offerRows'

/**
 * Who a product, a kind or a piece is offered to, set with one choice: nobody (a product) or as the
 * product (a kind or piece), every partner, chosen partners, or held back. Chosen partners are picked
 * in the share dialog.
 */
const props = defineProps<{
  target: ShareTarget
  targetId: number
  targetName: string
  share: OfferShare | null
  /** Whether a piece follows its kind rather than the product where it has no rule of its own. */
  underKind?: boolean
  /** Whether the choice is only shown, as in the help centre. */
  readonly?: boolean
}>()

const emit = defineEmits<{
  changed: []
}>()

const {t} = useI18n()
const id = useId()
const choice = computed(() => choiceOf(props.share, props.target))
const pickerOpen = ref(false)

const {run, running, failure} = useAsyncAction(async (next: ShareChoice) => {
  if (next === 'none' || next === 'inherit') await lending.removeShare(props.target, props.targetId)
  else if (next === 'all') await lending.setShare(props.target, props.targetId, {grant: 'GRANT', scope: 'ALL_PARTNERS', partnerIds: []})
  else if (next === 'withheld') await lending.setShare(props.target, props.targetId, {grant: 'WITHHOLD', scope: 'ALL_PARTNERS', partnerIds: []})
  emit('changed')
})

function pick(event: Event) {
  const select = event.target as HTMLSelectElement
  const next = select.value as ShareChoice
  select.value = choice.value
  if (next === 'some') {
    pickerOpen.value = true
    return
  }
  void run(next)
}

function label(option: ShareChoice): string {
  if (option === 'some' && props.share?.scope === 'SPECIFIC') {
    return t('lendingOffer.choice.someCount', {count: props.share.partnerIds.length})
  }
  if (option === 'inherit' && props.underKind) return t('lendingOffer.choice.inheritKind')
  return t(`lendingOffer.choice.${option}`)
}
</script>

<template>
  <div class="min-w-40">
    <FieldLabel v-if="!readonly" :for="id" class="sr-only">{{ t('lendingOffer.shareFor', {name: targetName}) }}</FieldLabel>
    <MutedText v-if="readonly">{{ label(choice) }}</MutedText>
    <template v-else>
      <SelectInput :id="id" :model-value="choice" class="w-full" :disabled="running" data-testid="lending-offer-share" @change="pick">
        <option v-for="option in choicesFor(target)" :key="option" :value="option">{{ label(option) }}</option>
      </SelectInput>
      <LinkButton v-if="choice === 'some'" @click="pickerOpen = true">{{ t('lendingOffer.changePartners') }}</LinkButton>
    </template>
    <FailureAlert :failure="failure"/>
    <LendingShareModal v-model="pickerOpen" :target="target" :target-id="targetId" :target-name="targetName" @saved="emit('changed')"/>
  </div>
</template>
