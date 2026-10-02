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
import {startsFromAPiece, useProvidedMovementWizard} from './useMovementWizard'
import {MovementPurpose} from '@/api/movements'

/**
 * Whichever question the wizard is on, drawn from the one state the wizard keeps and provides.
 */
const wizard = useProvidedMovementWizard()

const fromAPiece = computed(() => startsFromAPiece(wizard.purpose.value))
</script>

<template>
  <StepPurpose
      v-if="wizard.step.value === 'purpose'"
      v-model="wizard.purpose.value"
      :purposes="wizard.purposes.value"
  />
  <StepParty
      v-else-if="wizard.step.value === 'party'"
      v-model:for-the-store="wizard.forTheStore.value"
      v-model:member-id="wizard.memberId.value"
  />
  <StepSubject
      v-else-if="wizard.step.value === 'subject'"
      v-model:inventory-id="wizard.inventoryId.value"
      v-model:item-id="wizard.itemId.value"
      v-model:new-size-id="wizard.newSizeId.value"
      v-model:old-size-id="wizard.oldSizeId.value"
      :from-a-piece="fromAPiece"
      :inventories="wizard.inventories.value"
      :sizes="wizard.sizes.value"
  />
  <StepReason
      v-else-if="wizard.step.value === 'reason'"
      v-model:new-size-id="wizard.newSizeId.value"
      v-model:reason="wizard.reason.value"
      :asks-for-a-size="wizard.purpose.value === MovementPurpose.EXCHANGE"
      :sizes="wizard.sizes.value"
  />
  <StepPreview
      v-else
      :preview="wizard.preview.value"
      :resolved="wizard.resolved.value"
  />
</template>
