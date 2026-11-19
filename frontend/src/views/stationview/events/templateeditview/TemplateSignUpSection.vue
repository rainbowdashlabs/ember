/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import HintIcon from '@/components/typography/HintIcon.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TemplateTurnoutRules from './TemplateTurnoutRules.vue'
import {nonNegative} from './signUpRules'
import QueueOrderField from '../eventshared/QueueOrderField.vue'
import RegistrationDeadlineInput from '../eventshared/RegistrationDeadlineInput.vue'
import type {QueueOrder, RegistrationDeadline} from '@/api/generated/schema'

/**
 * The sign-up rules an event template hands the appointments made from it, laid out as the
 * appointment editor lays them out: every rule but the switch itself only while registration is on.
 */
defineProps<{
  /** Whether the template asks questions, which are answered when signing up, so sign-up stays on. */
  signUpHeld?: boolean
}>()

const requiresRegistration = defineModel<boolean>('requiresRegistration', {required: true})
const requiresConfirmation = defineModel<boolean>('requiresConfirmation', {required: true})
const registrationLimit = defineModel<number | undefined>('registrationLimit')
const queueOrder = defineModel<QueueOrder>('queueOrder', {required: true})
const registrationDeadline = defineModel<RegistrationDeadline>('registrationDeadline', {required: true})
const minRegistrations = defineModel<number | undefined>('minRegistrations')
const thresholdDays = defineModel<number | undefined>('thresholdDays')

const {t} = useI18n()
const labelId = useId()
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="template-sign-up">
    <SubHeader>{{ t('events.registration') }}</SubHeader>

    <div class="flex items-center justify-between">
      <span class="flex items-center gap-1">
        <span :id="`${labelId}-registration`" class="text-sm font-medium">{{ t('events.requiresRegistration') }}</span>
        <HintIcon v-if="signUpHeld" :text="t('events.signUpHeldByQuestions')"/>
      </span>
      <ToggleInput v-model="requiresRegistration" :disabled="signUpHeld" :aria-labelledby="`${labelId}-registration`"/>
    </div>

    <template v-if="requiresRegistration">
      <div class="flex items-center justify-between">
        <span :id="`${labelId}-confirmation`" class="text-sm font-medium">{{ t('events.requiresConfirmation') }}</span>
        <ToggleInput v-model="requiresConfirmation" :aria-labelledby="`${labelId}-confirmation`"/>
      </div>
      <p class="text-xs text-(--text-muted)">{{ t('events.requiresConfirmationHint') }}</p>

      <RegistrationDeadlineInput v-model="registrationDeadline" :allow-fixed="false"/>

      <div class="space-y-1">
        <FieldLabel>{{ t('events.registrationLimit') }}</FieldLabel>
        <NumberInput
            :model-value="registrationLimit"
            min="0"
            :placeholder="t('events.registrationLimitHint')"
            @update:model-value="value => registrationLimit = nonNegative(value)"
        />
        <p class="text-xs text-(--text-muted)">{{ t('events.registrationLimitHint') }}</p>
        <p class="text-xs text-(--text-muted)">{{ t('registrationQueue.limitHint') }}</p>
      </div>

      <QueueOrderField v-if="registrationLimit" v-model="queueOrder"/>

      <TemplateTurnoutRules
          v-model:min-registrations="minRegistrations"
          v-model:threshold-days="thresholdDays"
      />
    </template>
  </NeutralContainer>
</template>
