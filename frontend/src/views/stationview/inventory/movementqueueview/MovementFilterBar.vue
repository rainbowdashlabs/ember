/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SearchInput from '@/components/input/text/SearchInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import FilterTicks from './FilterTicks.vue'
import {filterablePurposes, filterableStates, filterableTurns, type InventoryChoice} from './movementFilter'

/**
 * What the queue is narrowed by: a name or a piece, an inventory, an art, a state, and whose turn it
 * is. The last is the one somebody working the queue reaches for, which is why it is offered at all.
 */
const props = defineProps<{
  inventories: InventoryChoice[]
}>()

const search = defineModel<string>('search', {required: true})
const inventoryIds = defineModel<string[]>('inventoryIds', {required: true})
const purposes = defineModel<string[]>('purposes', {required: true})
const states = defineModel<string[]>('states', {required: true})
const turns = defineModel<string[]>('turns', {required: true})

const {t} = useI18n()

const inventoryOptions = computed(() => props.inventories.map(inv => ({value: String(inv.id), label: inv.name})))
const purposeOptions = computed(
    () => filterablePurposes.map(name => ({value: name, label: t(`movements.purpose.${name}`)})),
)
const stateOptions = computed(() => filterableStates.map(name => ({value: name, label: t(`movements.state.${name}`)})))
const turnOptions = computed(() => filterableTurns.map(name => ({value: name, label: t(`movements.actor.${name}`)})))
</script>

<template>
  <div class="flex flex-wrap items-end gap-3">
    <div class="min-w-48 flex-1 space-y-1">
      <FieldLabel>{{ t('movements.queue.filter.search') }}</FieldLabel>
      <SearchInput v-model="search" data-testid="movement-filter-search"
                   :placeholder="t('movements.queue.filter.search')"/>
    </div>
    <FilterTicks v-model="purposes" :label="t('movements.queue.filter.purpose')" :options="purposeOptions"
                 testid="movement-filter-purpose"/>
    <FilterTicks v-model="turns" :label="t('movements.queue.filter.turn')" :options="turnOptions"
                 testid="movement-filter-turn"/>
    <FilterTicks v-model="states" :label="t('movements.queue.filter.state')" :options="stateOptions"
                 testid="movement-filter-state"/>
    <FilterTicks v-model="inventoryIds" :label="t('movements.queue.filter.inventory')" :options="inventoryOptions"
                 testid="movement-filter-inventory"/>
  </div>
</template>
