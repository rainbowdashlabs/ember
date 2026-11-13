/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import DragList from '@/components/input/DragList.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import {moveWithin} from '@/util/reorder'
import type {SetRow} from './eventGroupSetRow'

/**
 * The sets of an event group, in the order members see them: a name and how many of its parts a sign-up
 * has to choose. An event group always keeps at least one set.
 */
const sets = defineModel<SetRow[]>({required: true})
const {t} = useI18n()

function add() {
  sets.value = [...sets.value, {id: null, name: '', minimum: 1}]
}

function remove(index: number) {
  sets.value = sets.value.filter((_, at) => at !== index)
}

function update(index: number, change: Partial<SetRow>) {
  sets.value = sets.value.map((set, at) => (at === index ? {...set, ...change} : set))
}
</script>

<template>
  <div class="space-y-2" data-testid="event-group-sets">
    <DragList :items="sets" :key-fn="(set, index) => set.id ?? `new-${index}`" @reorder="(from, to) => sets = moveWithin(sets, from, to)">
      <template #default="{item: set, index}">
        <div class="flex flex-wrap items-center gap-2">
          <TextInput
              :model-value="set.name"
              :aria-label="t('eventGroups.setName')"
              class="flex-1 min-w-40"
              @update:model-value="value => update(index, {name: value ?? ''})"
          />
          <NumberInput
              :model-value="set.minimum"
              :aria-label="t('eventGroups.setMinimum')"
              class="w-24"
              @update:model-value="value => update(index, {minimum: value ?? 0})"
          />
          <DeleteButton v-if="sets.length > 1" :label="t('eventGroups.removeSet')" @click="remove(index)"/>
        </div>
      </template>
    </DragList>
    <SecondaryButton :icon="['fas', 'plus']" data-testid="event-group-add-set" @click="add">{{ t('eventGroups.addSet') }}</SecondaryButton>
  </div>
</template>
