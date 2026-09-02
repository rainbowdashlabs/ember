/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import type {InventoryArt} from '@/api/inventoryArts'

/**
 * The kind the ticked pieces are to become: one that is already there, or a word typed in.
 *
 * <p>The word is only written down when the action runs, so opening this screen and leaving it
 * again invents no kind, and a word typed and thought better of is never recorded.
 */
defineProps<{
  arts: InventoryArt[]
}>()

const artId = defineModel<number | null>('artId', {default: null})
const name = defineModel<string>('name', {default: ''})

const {t} = useI18n()

function onArtChanged(value: string | number | null | undefined) {
  const id = value === '' || value == null ? null : Number(value)
  artId.value = id
  if (id != null) name.value = ''
}
</script>

<template>
  <NeutralContainer class="space-y-3">
    <div v-if="arts.length > 0" class="space-y-1">
      <FieldLabel>{{ t('inventory.art.existing') }}</FieldLabel>
      <SelectInput :model-value="artId ?? ''" data-testid="tidy-art-existing" @update:model-value="onArtChanged">
        <option value="">{{ t('inventory.art.newOne') }}</option>
        <option v-for="art in arts" :key="art.id" :value="art.id">{{ art.name }}</option>
      </SelectInput>
    </div>
    <div v-if="artId == null" class="space-y-1">
      <FieldLabel>{{ t('inventory.art.newName') }}</FieldLabel>
      <TextInput v-model="name" data-testid="tidy-art-name" :placeholder="t('inventory.art.namePlaceholder')"/>
      <p class="text-xs text-(--text-muted)">{{ t('inventory.art.newNameHint') }}</p>
    </div>
  </NeutralContainer>
</template>
