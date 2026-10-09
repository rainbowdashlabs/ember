/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import CheckboxInput from '@/components/input/toggle/CheckboxInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SigningStepFrame from './SigningStepFrame.vue'
import {signerShort, type SigningDocumentGroup} from './batchFlow'

/**
 * The first screen: every signature still missing, one line per field under its document, each ticked to
 * be signed in this go. Unticking one leaves it for later; it stays among the open tasks.
 */
defineProps<{
  groups: SigningDocumentGroup[]
  position: number
  total: number
}>()

const chosen = defineModel<number[]>({required: true})

defineEmits<{next: []}>()

const {t} = useI18n()

function toggle(fieldId: number, ticked: boolean) {
  chosen.value = ticked ? [...chosen.value, fieldId] : chosen.value.filter(id => id !== fieldId)
}
</script>

<template>
  <SigningStepFrame :title="t('signing.flow.overview.heading')" :position="position" :total="total">
    <p class="text-sm">{{ t('signing.flow.overview.hint') }}</p>
    <ul class="space-y-3" data-testid="signing-overview">
      <li v-for="group in groups" :key="group.requestUid" class="space-y-1">
        <p class="font-medium">{{ group.title }}</p>
        <FieldLabel v-for="field in group.fields" :key="field.fieldId" inline class="cursor-pointer">
          <CheckboxInput
              :model-value="chosen.includes(field.fieldId)"
              :data-testid="`signing-choose-${field.fieldId}`"
              @update:model-value="ticked => toggle(field.fieldId, ticked)"
          />
          {{ t('signing.flow.overview.line', {document: group.title, who: signerShort(field, t)}) }}
        </FieldLabel>
      </li>
    </ul>
    <template #actions>
      <PrimaryButton type="button" :disabled="chosen.length === 0" data-testid="signing-start" @click="$emit('next')">
        {{ t('signing.flow.overview.start') }}
      </PrimaryButton>
    </template>
  </SigningStepFrame>
</template>
