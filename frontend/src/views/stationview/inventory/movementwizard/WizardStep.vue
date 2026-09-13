/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import StepPurpose from './StepPurpose.vue'
import StepParty from './StepParty.vue'
import StepSubject from './StepSubject.vue'
import StepReason from './StepReason.vue'
import StepPreview from './StepPreview.vue'
import {startsFromAPiece, type useMovementWizard} from './useMovementWizard'
import {MovementPurpose} from '@/api/movements'

/**
 * Whichever question the wizard is on, drawn from the one state the wizard keeps.
 *
 * <p>The whole state travels rather than a dozen models: every step reads and writes a different part
 * of it, and threading each part through as its own pair would say nothing the state does not.
 */
const props = defineProps<{
  wizard: ReturnType<typeof useMovementWizard>
}>()

const fromAPiece = computed(() => startsFromAPiece(props.wizard.purpose.value))
</script>

<template>
  <StepPurpose
      v-if="props.wizard.step.value === 'purpose'"
      v-model="props.wizard.purpose.value"
      :purposes="props.wizard.purposes.value"
  />
  <StepParty
      v-else-if="props.wizard.step.value === 'party'"
      v-model:for-the-store="props.wizard.forTheStore.value"
      v-model:member-id="props.wizard.memberId.value"
  />
  <StepSubject
      v-else-if="props.wizard.step.value === 'subject'"
      v-model:inventory-id="props.wizard.inventoryId.value"
      v-model:item-id="props.wizard.itemId.value"
      v-model:new-size-id="props.wizard.newSizeId.value"
      v-model:old-size-id="props.wizard.oldSizeId.value"
      :from-a-piece="fromAPiece"
      :inventories="props.wizard.inventories.value"
      :sizes="props.wizard.sizes.value"
  />
  <StepReason
      v-else-if="props.wizard.step.value === 'reason'"
      v-model:new-size-id="props.wizard.newSizeId.value"
      v-model:reason="props.wizard.reason.value"
      :asks-for-a-size="props.wizard.purpose.value === MovementPurpose.EXCHANGE"
      :sizes="props.wizard.sizes.value"
  />
  <StepPreview
      v-else
      :preview="props.wizard.preview.value"
      :resolved="props.wizard.resolved.value"
  />
</template>
