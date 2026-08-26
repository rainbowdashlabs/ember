/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MutedText from '@/components/typography/MutedText.vue'
import {ItemOwner} from '@/api/inventory'
import type {MovementFlow, MovementFlowBinding} from '@/api/movements'

const {t} = useI18n()

const props = defineProps<{
  bindings: MovementFlowBinding[]
  flows: MovementFlow[]
  busy: boolean
}>()

const emit = defineEmits<{rebind: [binding: MovementFlowBinding, flowId: number]}>()

/** Only a flow for the same purpose can serve a binding, and a retired one is not offered. */
function choicesFor(binding: MovementFlowBinding): MovementFlow[] {
  return props.flows.filter(flow => flow.purpose === binding.purpose && !flow.archived && !flow.ownedByCluster)
}

function ownerLabel(ownerKind: string): string {
  return ownerKind === ItemOwner.STATION ? t('inventory.edit.ownerStation') : t('inventory.edit.ownerCluster')
}
</script>

<template>
  <div class="space-y-2">
    <MutedText size="sm" tag="p">{{ t('flows.bindingHint') }}</MutedText>

    <div class="grid items-center gap-x-4 gap-y-2 text-sm sm:grid-cols-[max-content_minmax(0,24rem)]">
      <template
          v-for="binding in props.bindings"
          :key="`${binding.ownerKind}-${binding.purpose}-${binding.party}-${binding.inventoryId ?? 'all'}`"
      >
        <span>
          {{ ownerLabel(binding.ownerKind) }} · {{ t(`movements.purpose.${binding.purpose}`) }} ·
          {{ t(`flows.party.${binding.party}`) }}
        </span>
        <SelectInput
            class="w-full"
            :disabled="props.busy"
            :model-value="String(binding.flowId)"
            @update:model-value="v => emit('rebind', binding, Number(v))"
        >
          <option v-for="flow in choicesFor(binding)" :key="flow.id" :value="String(flow.id)">{{ flow.name }}</option>
        </SelectInput>
      </template>
    </div>
  </div>
</template>
