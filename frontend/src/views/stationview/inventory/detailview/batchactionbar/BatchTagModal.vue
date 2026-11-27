/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import {inventoryTags} from '@/api'
import type {TagResponse} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import ItemTagPicker from '../../ItemTagPicker.vue'

/** Whether the dialog puts tags on the ticked pieces or takes them off. */
export type BatchTagMode = 'add' | 'remove'

/**
 * Puts tags on every ticked piece, or takes them off.
 *
 * <p>Adding may write down a word the station does not have yet; removing offers only the words it
 * has, since a word nobody wears cannot come off anything.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  inventoryId: number
  itemIds: number[]
  mode: BatchTagMode
}>()

const emit = defineEmits<{
  done: []
}>()

const {t} = useI18n()

const tags = ref<TagResponse[]>([])
const names = ref<string[]>([])

watch(open, async isOpen => {
  if (!isOpen) return
  names.value = []
  try {
    tags.value = await inventoryTags.listTags()
  } catch {
    tags.value = []
  }
})

const title = computed(() => props.mode === 'add'
    ? t('inventory.batch.addTagTitle', {count: props.itemIds.length})
    : t('inventory.batch.removeTagTitle', {count: props.itemIds.length}))

const apply = useAsyncAction(async () => {
  await inventoryTags.changeTagsOfItems(props.inventoryId, {
    itemIds: props.itemIds,
    add: props.mode === 'add' ? names.value : [],
    remove: props.mode === 'remove' ? names.value : [],
  })
  open.value = false
  emit('done')
})
</script>

<template>
  <Modal v-model="open" size="sm">
    <div class="space-y-3" data-testid="batch-tag-modal">
      <SubHeader>{{ title }}</SubHeader>
      <ItemTagPicker v-model:names="names" :creatable="mode === 'add'" :tags="tags"/>
      <FailureAlert :failure="apply.failure.value"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <SaveButton :action="apply.runOrReject" :disabled="names.length === 0" data-testid="batch-tag-apply"/>
      </ButtonRow>
    </div>
  </Modal>
</template>
