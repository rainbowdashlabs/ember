/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import DetailLabel from '@/components/typography/DetailLabel.vue'
import type {MovementFlowBinding} from '@/api/movements'
import {itemOwnerLabel} from '@/util/inventoryType'

const {t} = useI18n()

const props = defineProps<{binding: MovementFlowBinding}>()

/**
 * The combination a chain serves, as three named facts.
 *
 * <p>Named rather than run together behind separators: the counterparty written as a bare value
 * beside the other two reads as a place the movement ends at instead of as who is at the other end.
 */
const facts = computed(() => [
  {key: 'owner', label: t('flows.serves.owner'), value: itemOwnerLabel(t, props.binding.ownerKind)},
  {key: 'purpose', label: t('flows.serves.purpose'), value: t(`movements.purpose.${props.binding.purpose}`)},
  {key: 'party', label: t('flows.serves.party'), value: t(`flows.party.${props.binding.party}`)},
])
</script>

<template>
  <div class="flex flex-wrap items-baseline gap-x-4 gap-y-1 text-sm">
    <span v-for="fact in facts" :key="fact.key" class="inline-flex items-baseline gap-1">
      <DetailLabel>{{ fact.label }}</DetailLabel>
      {{ fact.value }}
    </span>
  </div>
</template>
