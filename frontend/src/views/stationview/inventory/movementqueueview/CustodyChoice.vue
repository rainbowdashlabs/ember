/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {ItemCustody, type ItemCustodyName} from '@/api/inventory'

/**
 * Where one of a movement's two pieces is said to be, or nothing to leave it where it is.
 *
 * <p>The four places are the same for both pieces, which is why they are asked for the same way.
 */
const model = defineModel<string>({required: true})

const props = defineProps<{
  label: string
  testid: string
  disabled?: boolean
}>()

const {t} = useI18n()

const custodies: ItemCustodyName[] = [
  ItemCustody.WITH_OWNER,
  ItemCustody.AT_STATION,
  ItemCustody.IN_TRANSIT,
  ItemCustody.WITH_MEMBER,
]
</script>

<template>
  <FieldLabel>{{ props.label }}</FieldLabel>
  <SelectInput v-model="model" :data-testid="props.testid" :disabled="props.disabled">
    <option value="">{{ t('movements.queue.correctPanel.leave') }}</option>
    <option v-for="custody in custodies" :key="custody" :value="custody">
      {{ t(`itemDetail.custodyValues.${custody}`) }}
    </option>
  </SelectInput>
</template>
