/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import {nonNegative} from '../templateeditview/signUpRules'

/**
 * The places of a part of an event group. Left empty, the part takes the places per appointment of
 * its event group, which the box shows as its placeholder; a number gives the part its own, for a
 * bigger room on one day. A chosen part that is full puts the whole sign-up on the waiting list.
 */
const limit = defineModel<number | undefined>()

defineProps<{
  /** The places per appointment of the event group, null where it limits none. */
  inherited: number | null
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-2" data-testid="part-limit">
    <SubHeader>{{ t('eventGroups.partLimit') }}</SubHeader>
    <FieldLabel for="part-limit">{{ t('events.registrationLimit') }}</FieldLabel>
    <NumberInput
        id="part-limit"
        :model-value="limit"
        min="0"
        :placeholder="inherited == null ? t('events.registrationLimitHint') : String(inherited)"
        @update:model-value="value => limit = nonNegative(value) || undefined"
    />
    <FieldHint>
      {{ inherited == null ? t('eventGroups.partLimitNone') : t('eventGroups.partLimitInherited', {limit: inherited}) }}
    </FieldHint>
  </NeutralContainer>
</template>
