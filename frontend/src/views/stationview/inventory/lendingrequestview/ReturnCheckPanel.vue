/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import * as lending from '@/api/lending'
import type {LoanPiece, ReturnState} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import ReturnCheckList from './ReturnCheckList.vue'

/**
 * Takes lent gear back piece by piece: each is in order, damaged or missing, with a comment. A missing
 * piece is marked lost, and the result goes into the request's messages for the borrowing station.
 */
const props = defineProps<{
  requestId: number
}>()

const emit = defineEmits<{
  done: []
  cancel: []
}>()

const {t} = useI18n()
const pieces = ref<LoanPiece[]>([])
const states = ref(new Map<number, ReturnState>())
const comments = ref(new Map<number, string>())

const {loading, failure} = useAsyncLoader(async (isCurrent) => {
  const answer = await lending.getReturnPieces(props.requestId)
  if (!isCurrent()) return
  pieces.value = answer
  states.value = new Map(answer.map(piece => [piece.itemId, 'OK']))
})

const STATES: ReturnState[] = ['OK', 'DAMAGED', 'MISSING']
const counts = computed(() => STATES.map(state => [...states.value.values()].filter(value => value === state).length))

function set(itemId: number, state: ReturnState) {
  states.value = new Map(states.value).set(itemId, state)
}

function comment(itemId: number, text: string) {
  comments.value = new Map(comments.value).set(itemId, text)
}

const {run, running, failure: sendFailure} = useAsyncAction(async () => {
  await lending.checkReturn(props.requestId, pieces.value.map(piece => ({
    itemId: piece.itemId,
    state: states.value.get(piece.itemId) ?? 'OK',
    comment: comments.value.get(piece.itemId)?.trim() || null,
  })))
  emit('done')
})
</script>

<template>
  <NeutralContainer class="mb-4 space-y-3" data-testid="lending-return-check">
    <SubHeader>{{ t('lendingReturn.title') }}</SubHeader>
    <MutedText tag="p">{{ t('lendingReturn.hint') }}</MutedText>
    <AsyncSection :failure="failure" :loading="loading" :empty="pieces.length === 0" :empty-message="t('lendingReturn.noPieces')">
      <ReturnCheckList :pieces="pieces" :states="states" :comments="comments" @state="set" @comment="comment"/>
    </AsyncSection>
    <MutedText tag="p">{{ t('lendingReturn.counts', {ok: counts[0], damaged: counts[1], missing: counts[2]}) }}</MutedText>
    <FailureAlert :failure="sendFailure"/>
    <ButtonRow pair align="end">
      <SecondaryButton @click="emit('cancel')">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton :icon="['fas', 'check']" :disabled="running || loading || pieces.length === 0" data-testid="lending-return-submit" @click="run()">
        {{ t('lendingReturn.submit') }}
      </PrimaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
