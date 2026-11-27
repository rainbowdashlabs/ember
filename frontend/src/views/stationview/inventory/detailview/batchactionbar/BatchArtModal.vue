/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import {inventoryArts} from '@/api'
import type {InventoryArt} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import ArtPicker from '../../ArtPicker.vue'

/**
 * Puts every ticked piece under one kind, or takes the kind away from all of them.
 *
 * <p>A kind typed into the picker is written down only when the change is applied, the same way the
 * dialog of a single piece does it.
 */
const open = defineModel<boolean>({required: true})

const props = defineProps<{
  inventoryId: number
  arts: InventoryArt[]
  itemIds: number[]
}>()

const emit = defineEmits<{
  done: [changed: number]
}>()

const {t} = useI18n()

const artId = ref<number | null>(null)
const draft = ref('')

watch(open, isOpen => {
  if (!isOpen) return
  artId.value = null
  draft.value = ''
})

const apply = useAsyncAction(async () => {
  const resolved = draft.value
      ? await inventoryArts.ensureArt(props.inventoryId, props.arts, draft.value)
      : artId.value
  const result = await inventoryArts.assignArt(props.inventoryId, resolved, props.itemIds)
  open.value = false
  emit('done', result.changed)
})
</script>

<template>
  <Modal v-model="open" size="sm">
    <div class="space-y-3" data-testid="batch-art-modal">
      <SubHeader>{{ t('inventory.batch.setArtTitle', {count: itemIds.length}) }}</SubHeader>
      <ArtPicker v-model:art-id="artId" v-model:draft="draft" :arts="arts"/>
      <MutedText size="sm" tag="p">{{ t('inventory.batch.setArtHint') }}</MutedText>
      <FailureAlert :failure="apply.failure.value"/>
      <ButtonRow pair align="end">
        <SecondaryButton @click="open = false">{{ t('common.cancel') }}</SecondaryButton>
        <SaveButton :action="apply.runOrReject" data-testid="batch-art-apply"/>
      </ButtonRow>
    </div>
  </Modal>
</template>
