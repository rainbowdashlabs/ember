/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {LoanPiece, ReturnState} from '@/api/generated/schema'

/** The pieces of a loan, each set to in order, damaged or missing, with a comment where not in order. */
defineProps<{
  pieces: LoanPiece[]
  states: ReadonlyMap<number, ReturnState>
  comments: ReadonlyMap<number, string>
}>()

const emit = defineEmits<{
  state: [itemId: number, state: ReturnState]
  comment: [itemId: number, text: string]
}>()

const {t} = useI18n()
const groupId = useId()
const STATES: ReturnState[] = ['OK', 'DAMAGED', 'MISSING']

/** The longest comment the lending station keeps for a piece. */
const MAX_COMMENT = 500
</script>

<template>
  <ul class="divide-y divide-(--border)">
    <li v-for="piece in pieces" :key="piece.itemId" class="flex flex-wrap items-center gap-2 py-2" data-testid="lending-return-piece">
      <span :id="`${groupId}-${piece.itemId}`" class="min-w-0 flex-1 font-medium">
        {{ piece.name }} <MutedText v-if="piece.internalId">({{ piece.internalId }})</MutedText>
      </span>
      <div class="flex gap-1" role="group" :aria-labelledby="`${groupId}-${piece.itemId}`">
        <SelectionToggleButton v-for="state in STATES" :key="state" :selected="states.get(piece.itemId) === state"
                               :data-testid="`lending-return-${state}`" @toggle="emit('state', piece.itemId, state)">
          {{ t(`lendingReturn.state.${state}`) }}
        </SelectionToggleButton>
      </div>
      <TextInput v-if="states.get(piece.itemId) !== 'OK'" class="w-full sm:w-64" :model-value="comments.get(piece.itemId) ?? ''"
                 :maxlength="MAX_COMMENT" :placeholder="t('lendingReturn.comment')" :aria-label="t('lendingReturn.commentFor', {name: piece.name})"
                 @update:model-value="text => emit('comment', piece.itemId, String(text ?? ''))"/>
    </li>
  </ul>
</template>
