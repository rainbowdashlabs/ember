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
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {Inventory} from '@/api/inventory'

/**
 * Where the chosen pieces are going, with the sentence saying what survives the journey.
 *
 * The target is picked before the pieces on purpose: once it is known, every line of the list below
 * can say whether its size comes along.
 */
const targetId = defineModel<string>({required: true})

defineProps<{
  targets: Inventory[]
}>()

const emit = defineEmits<{
  selected: []
}>()

const {t} = useI18n()
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SubHeader>{{ t('inventory.move.target') }}</SubHeader>
    <p class="text-sm text-(--text-muted)">{{ t('inventory.move.explainer') }}</p>
    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.move.targetLabel') }}</FieldLabel>
      <SelectInput v-model="targetId" data-testid="move-target" @change="emit('selected')">
        <option disabled value="">{{ t('inventory.move.selectTarget') }}</option>
        <option v-for="target in targets" :key="target.id" :value="String(target.id)">
          {{ target.name }}
        </option>
      </SelectInput>
    </div>
  </NeutralContainer>
</template>
