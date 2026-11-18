/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SubHeader from '@/components/typography/SubHeader.vue'

/**
 * The question asked before something is removed for good: a sentence saying what goes, a cancel
 * and a red button that does it. A question that removes nothing, such as one before saving, asks
 * with the primary button instead and names it through `confirmLabel`.
 *
 * <p>Every "are you sure" in the application is this one dialog. What differs between them is data:
 * a heading, the sentence, the label of the button, whether the removal is already under way, and
 * whatever a caller has to add below the sentence (a hint, what else the removal takes with it),
 * which goes in the default slot.
 */
const {t} = useI18n()

const modelValue = defineModel<boolean>({required: true})

withDefaults(defineProps<{
  message: string
  /** The heading above the sentence, left out where the sentence says it all. */
  title?: string
  /** What the confirming button says. Defaults to "Delete", so a neutral question always names its own. */
  confirmLabel?: string
  /** An icon before the confirming button's label, named the way `AppIcon` takes it. */
  confirmIcon?: string[]
  /** Set while the removal runs, so neither button can be pressed twice. */
  busy?: boolean
  /** A test id for the confirming button, for the stories that press it by name. */
  confirmTestId?: string
  /** A question that deletes nothing, such as one before saving: its button is the primary one, not a red one. */
  neutral?: boolean
}>(), {
  title: undefined,
  confirmLabel: undefined,
  confirmIcon: undefined,
  busy: false,
  confirmTestId: undefined,
  neutral: false,
})

const emit = defineEmits<{
  confirm: []
}>()
</script>

<template>
  <Modal v-model="modelValue">
    <div class="space-y-4">
      <SubHeader v-if="title">{{ title }}</SubHeader>
      <p>{{ message }}</p>
      <slot/>
      <ButtonRow pair align="end">
        <SecondaryButton data-cancel :disabled="busy" @click="modelValue = false">{{ t('common.cancel') }}</SecondaryButton>
        <component :is="neutral ? PrimaryButton : ErrorButton" data-confirm :data-testid="confirmTestId" :icon="confirmIcon"
                   :disabled="busy" @click="emit('confirm')">
          {{ confirmLabel ?? t('common.delete') }}
        </component>
      </ButtonRow>
    </div>
  </Modal>
</template>
