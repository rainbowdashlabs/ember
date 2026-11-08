/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import Modal from '@/components/feedback/Modal.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import ExchangeReasonPicker from '@/components/movement/ExchangeReasonPicker.vue'
import HandedInChoice from './rapidexchangemodal/HandedInChoice.vue'
import type {InventorySize} from '@/api/generated/schema'
import type {Failure} from '@/util/failure'

/**
 * The exchange a check raises on the spot, filled in from what the check already knows.
 *
 * <p>Somebody walking a check has the member in front of them and the piece in their hands. Sending
 * them to the exchange screen to type in what they are looking at is how a check that found something
 * ends with nothing recorded.
 *
 * <p>Why and which size are asked the way every swap asks them. What only this screen asks is whether
 * the piece was handed in there and then.
 */
const props = defineProps<{
  itemName?: string
  /** The sizes the inventory keeps, smallest first. */
  sizes: InventorySize[]
  currentSizeId?: number | null
  busy?: boolean
  failure?: Failure | null
}>()

const show = defineModel<boolean>({required: true})

const emit = defineEmits<{
  confirm: [payload: {newSizeId: number | null; reason: string; handedIn: boolean}]
}>()

const {t} = useI18n()

const reasonText = ref('')
const newSizeId = ref<number | null>(null)
const opened = ref(0)

/**
 * Whether the piece was handed over there and then.
 *
 * <p>Asked rather than assumed, because it is the one thing about the exchange that only the person
 * standing there knows, and it decides how far the exchange has already come: a piece still on the
 * member is an exchange announced, a piece in the hand is one whose old piece is back.
 */
const handedIn = ref<boolean | null>(null)

watch(show, visible => {
  if (!visible) return
  opened.value++
  reasonText.value = ''
  handedIn.value = null
  newSizeId.value = props.currentSizeId ?? null
})

function confirm() {
  if (!reasonText.value || handedIn.value === null) return
  emit('confirm', {newSizeId: newSizeId.value, reason: reasonText.value, handedIn: handedIn.value})
}
</script>

<template>
  <Modal v-model="show">
    <SubHeader class="mb-1">{{ t('inventory.check.exchange') }}</SubHeader>
    <p class="mb-3 text-sm text-(--text-muted)">{{ props.itemName }}</p>

    <FailureAlert :failure="props.failure" class="mb-3"/>

    <div class="space-y-3">
      <ExchangeReasonPicker
          :key="opened"
          v-model:new-size-id="newSizeId"
          v-model:text="reasonText"
          :sizes="props.sizes"
          :current-size-id="props.currentSizeId"
          test-id="rapid-exchange"
      />
      <HandedInChoice v-model="handedIn"/>
    </div>

    <ButtonRow pair align="end" class="mt-4">
      <SecondaryButton @click="show = false">{{ t('common.cancel') }}</SecondaryButton>
      <PrimaryButton
          :disabled="props.busy || !reasonText || handedIn === null"
          data-testid="rapid-exchange-confirm"
          @click="confirm"
      >
        {{ t('inventory.check.exchangeCreate') }}
      </PrimaryButton>
    </ButtonRow>
  </Modal>
</template>
