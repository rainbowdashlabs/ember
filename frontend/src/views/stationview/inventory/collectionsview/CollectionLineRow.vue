/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SuccessBadge from '@/components/badge/SuccessBadge.vue'
import ErrorBadge from '@/components/badge/ErrorBadge.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import type {ResolvedCollectionLine} from '@/api/inventoryCollections'

const props = defineProps<{
  line: ResolvedCollectionLine
  editable: boolean
}>()

const emit = defineEmits<{
  updateQuantity: [line: ResolvedCollectionLine, quantity: number]
  remove: [line: ResolvedCollectionLine]
}>()

const {t} = useI18n()

/**
 * The line kinds fall short differently, so they say so differently.
 *
 * A counted line short of stock is a fraction, because two of four blue radios is still worth
 * knowing. A named piece that is not here is simply gone: there is no other piece that would do, so
 * a fraction would be a number nobody can act on.
 */
const shortfall = () =>
    props.line.itemId !== null
        ? t('inventory.collections.itemMissing')
        : t('inventory.collections.fraction', {available: props.line.available, requested: props.line.requested})
</script>

<template>
  <div
      class="grid grid-cols-[1fr_auto_6rem_2.5rem] gap-2 items-center px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50"
      data-testid="collection-line"
  >
    <span class="text-sm truncate">{{ line.label }}</span>
    <div class="flex items-center gap-2">
      <SuccessBadge v-if="line.filled" data-testid="collection-line-filled">
        {{ t('inventory.collections.fraction', {available: line.available, requested: line.requested}) }}
      </SuccessBadge>
      <ErrorBadge v-else data-testid="collection-line-short">{{ shortfall() }}</ErrorBadge>
      <InfoBadge v-if="line.clusterOwned > 0" data-testid="collection-line-cluster-owned">
        {{ t('inventory.collections.clusterOwned', {count: line.clusterOwned}) }}
      </InfoBadge>
    </div>
    <NumberInput
        v-if="line.itemId === null"
        :model-value="line.requested"
        :min="1"
        :disabled="!editable"
        data-testid="collection-line-count"
        @update:model-value="emit('updateQuantity', line, $event as number)"
    />
    <span v-else/>
    <DeleteButton v-if="editable" @click="emit('remove', line)"/>
    <span v-else/>
  </div>
</template>
