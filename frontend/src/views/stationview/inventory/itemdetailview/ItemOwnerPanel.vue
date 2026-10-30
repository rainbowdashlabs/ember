/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import {inventory} from '@/api'
import {
  InventoryType,
  ItemCustody,
  ItemOwner,
  StationPermission,
  type InventoryItem,
} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * Who owns a piece of a mixed inventory, changed after it was written down.
 *
 * <p>Only in a mixed inventory: an internal or external one says by its type whose gear it holds. Only while
 * the piece rests in the station's store, because one that is with somebody, on its way or missing has a
 * chain or a record running for the owner it has. And only for whoever may write gear down for both owners.
 */
const props = defineProps<{
  item: InventoryItem
}>()

const emit = defineEmits<{
  updated: [item: InventoryItem]
}>()

const {t} = useI18n()
const {hasPermission} = useSession()

const mixed = ref(false)
const chosen = ref<ItemOwner>(props.item.ownerKind)

onMounted(async () => {
  try {
    mixed.value = (await inventory.getInventory(props.item.inventoryId)).inventoryType === InventoryType.MIXED
  } catch {
    mixed.value = false
  }
})

const mayChange = computed(() =>
  hasPermission(StationPermission.INVENTORY_CREATE_INTERNAL)
  && hasPermission(StationPermission.INVENTORY_CREATE_EXTERNAL))

const shown = computed(() => mixed.value && mayChange.value && props.item.ownerKind !== ItemOwner.PARTNER_STATION)

const atRest = computed(() =>
  (props.item.ownerKind === ItemOwner.STATION && props.item.custody === ItemCustody.WITH_OWNER)
  || (props.item.ownerKind === ItemOwner.CLUSTER && props.item.custody === ItemCustody.AT_STATION))

const {running, failure, run: save} = useAsyncAction(async () => {
  emit('updated', await inventory.changeOwner(props.item.id, chosen.value))
})
</script>

<template>
  <NeutralContainer v-if="shown" class="space-y-3" data-testid="item-owner">
    <SectionHeader>{{ t('itemDetail.ownerSwitch.title') }}</SectionHeader>
    <template v-if="atRest">
      <MutedText tag="p" size="sm">{{ t('itemDetail.ownerSwitch.hint') }}</MutedText>
      <FailureAlert :failure="failure"/>
      <div class="flex flex-wrap items-center gap-2">
        <SelectInput
            :model-value="chosen"
            class="min-w-40"
            data-testid="item-owner-choice"
            @update:model-value="(v: string | number | null | undefined) => chosen = String(v ?? '') as ItemOwner">
          <option :value="ItemOwner.STATION">{{ t('itemDetail.ownerStation') }}</option>
          <option :value="ItemOwner.CLUSTER">{{ t('itemDetail.ownerCluster') }}</option>
        </SelectInput>
        <PrimaryButton
            :disabled="running || chosen === props.item.ownerKind"
            data-testid="item-owner-save"
            @click="save">
          {{ t('itemDetail.ownerSwitch.save') }}
        </PrimaryButton>
      </div>
    </template>
    <MutedText v-else tag="p" size="sm" data-testid="item-owner-not-at-rest">
      {{ t('itemDetail.ownerSwitch.notAtRest') }}
    </MutedText>
  </NeutralContainer>
</template>
