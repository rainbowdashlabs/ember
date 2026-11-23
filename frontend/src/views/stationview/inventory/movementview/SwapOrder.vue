/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {procurement} from '@/api'
import {StationModule, StationPermission} from '@/api/generated/schema'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'

/**
 * Ordering the replacement of a swap when nothing fitting is free in storage, and the swap waiting for
 * that order once it is placed.
 *
 * <p>Ordering is offered only where the shelf has nothing in the size asked for, and only to whoever
 * may order: the server refuses an order while a fitting piece is free, so offering it then would only
 * lead to a refusal. Once ordered, the swap waits: delivering the order names the new piece, and
 * picking one by hand means deleting the order first.
 */
const props = defineProps<{
  movementId: number
  /** The order the swap waits for, or nothing while it waits for none. */
  orderId: number | null
  /** Whether the shelf holds nothing in the size the swap asks for. */
  nothingFits: boolean
  busy: boolean
  /** Called once an order was placed or deleted, so whoever shows the swap reads it again. */
  onChanged?: () => void
}>()

const {t} = useI18n()
const {hasPermission, isModuleEnabled} = useSession()
const routes = useInventoryRoutes()

const mayOrder = computed(() =>
    hasPermission(StationPermission.INVENTORY_PROCUREMENT) && isModuleEnabled(StationModule.INVENTORY_PROCUREMENT))

const {running, failure, run: order} = useAsyncAction(async () => {
  await procurement.procureForSwap(props.movementId)
  props.onChanged?.()
})

const {show: confirmingDelete, requestDelete, confirm: deleteOrder, failure: deleteFailure} = useConfirmDelete<number>({
  onDelete: async id => {
    await procurement.deleteProcurement(id)
  },
  onSuccess: () => props.onChanged?.(),
})
</script>

<template>
  <div class="space-y-2" data-testid="swap-order">
    <FailureAlert :failure="failure ?? deleteFailure"/>

    <InfoContainer v-if="props.orderId != null" data-testid="swap-order-waiting">
      <div class="flex flex-wrap items-center justify-between gap-2">
        <p class="text-sm">
          {{ t('movements.order.waiting') }}
          <NuxtLink v-if="mayOrder" :to="{name: routes.procurement}" class="underline" data-testid="swap-order-link">
            {{ t('movements.order.open') }}
          </NuxtLink>
        </p>
        <DeleteButton
            v-if="mayOrder"
            :disabled="props.busy"
            data-testid="swap-order-delete"
            @click="requestDelete(props.orderId)"
        />
      </div>
    </InfoContainer>

    <div v-else-if="props.nothingFits && mayOrder" class="space-y-1" data-testid="swap-order-offer">
      <MutedText tag="p" size="sm">{{ t('movements.order.nothingFits') }}</MutedText>
      <ButtonRow>
        <SecondaryButton
            :disabled="props.busy || running"
            :icon="['fas', 'box-open']"
            data-testid="swap-order-place"
            @click="order()"
        >
          {{ t('movements.order.place') }}
        </SecondaryButton>
      </ButtonRow>
    </div>

    <ConfirmDeleteModal
        v-model="confirmingDelete"
        :message="t('movements.order.deleteConfirm')"
        @confirm="deleteOrder"
    />
  </div>
</template>
