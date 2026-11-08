/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import {ItemOwner} from '@/api/generated/schema'

/**
 * The owner of a piece of a mixed inventory, as a field of its edit form. Locked with a hint while the
 * piece is not in the station's store.
 */
const props = defineProps<{
  atRest: boolean
}>()

const owner = defineModel<ItemOwner | null>({required: true})

const {t} = useI18n()

const choices = [
  {value: ItemOwner.STATION, label: 'itemDetail.ownerStation'},
  {value: ItemOwner.CLUSTER, label: 'itemDetail.ownerCluster'},
]
</script>

<template>
  <div class="space-y-1" data-testid="item-owner">
    <FieldLabel>{{ t('itemDetail.owner') }}</FieldLabel>
    <SelectInput
        :model-value="owner"
        :disabled="!props.atRest"
        data-testid="item-owner-choice"
        @update:model-value="(v: string | number | null | undefined) => owner = String(v ?? '') as ItemOwner">
      <option v-for="choice in choices" :key="choice.value" :value="choice.value">{{ t(choice.label) }}</option>
    </SelectInput>
    <MutedText tag="p" size="sm" :data-testid="props.atRest ? undefined : 'item-owner-not-at-rest'">
      {{ props.atRest ? t('itemDetail.ownerSwitch.hint') : t('itemDetail.ownerSwitch.notAtRest') }}
    </MutedText>
  </div>
</template>
