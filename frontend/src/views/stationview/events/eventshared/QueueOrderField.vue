/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useId} from 'vue'
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FieldHint from '@/components/typography/FieldHint.vue'
import {QueueOrder} from '@/api/generated/schema'

/**
 * The order the waiting list of an appointment follows: by the time people signed up, or by their
 * fairness score. The appointment editor and the template editor both offer it next to the
 * participant limit, since only an appointment with a limit keeps a waiting list.
 */
const order = defineModel<QueueOrder>({required: true})

const {t} = useI18n()
const id = useId()

/** The select hands back a string, which is only taken where it names one of the orders. */
function choose(value: string | number | null | undefined) {
  const chosen = Object.values(QueueOrder).find(candidate => candidate === value)
  if (chosen) order.value = chosen
}
</script>

<template>
  <div class="space-y-1" data-testid="queue-order">
    <FieldLabel :for="id">{{ t('registrationQueue.order') }}</FieldLabel>
    <SelectInput :id="id" :model-value="order" @update:model-value="choose">
      <option :value="QueueOrder.TIME">{{ t('registrationQueue.orderTime') }}</option>
      <option :value="QueueOrder.FAIRNESS">{{ t('registrationQueue.orderFairness') }}</option>
    </SelectInput>
    <FieldHint>{{ t('registrationQueue.orderHint') }}</FieldHint>
  </div>
</template>
