/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {useI18n} from 'vue-i18n'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ArtPicker from '../../ArtPicker.vue'
import ItemTagPicker from '../../ItemTagPicker.vue'
import type {InventoryArt} from '@/api/inventoryArts'
import type {InventoryTag} from '@/api/inventoryTags'

/**
 * The three answers to "what is this thing": what it is called, what kind of thing it is, and what
 * it belongs to. They stand together because they are read together, and because the two pickers
 * below the name only make sense beside it.
 */
withDefaults(
    defineProps<{
      /** The kinds this inventory holds. Empty for an inventory of one thing in many copies. */
      arts?: InventoryArt[]
      /** Whether kinds are offered at all, which follows from what the inventory holds. */
      showArt?: boolean
      /** The words this station puts on its things, whatever inventory they are filed under. */
      tags?: InventoryTag[]
    }>(),
    {arts: () => [], showArt: false, tags: () => []},
)

const itemName = defineModel<string>('itemName', {default: ''})
const artId = defineModel<number | null>('artId', {default: null})
const artDraft = defineModel<string>('artDraft', {default: ''})
const tagNames = defineModel<string[]>('tagNames', {default: () => []})

const {t} = useI18n()
</script>

<template>
  <div class="space-y-4">
    <div class="space-y-1">
      <FieldLabel>{{ t('inventory.edit.itemName') }}</FieldLabel>
      <TextInput v-model="itemName" :placeholder="t('inventory.edit.itemNamePlaceholder')"/>
    </div>
    <div v-if="showArt" class="space-y-1">
      <FieldLabel>{{ t('inventory.art.field') }}</FieldLabel>
      <ArtPicker v-model:artId="artId" v-model:draft="artDraft" :arts="arts"/>
      <p class="text-xs text-(--text-muted)">{{ t('inventory.art.fieldHint') }}</p>
    </div>
    <div class="space-y-1" data-testid="item-tags">
      <FieldLabel>{{ t('inventory.tag.field') }}</FieldLabel>
      <ItemTagPicker v-model:names="tagNames" :tags="tags"/>
      <p class="text-xs text-(--text-muted)">{{ t('inventory.tag.fieldHint') }}</p>
    </div>
  </div>
</template>
