/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import AppLink from '@/components/navigation/AppLink.vue'
import * as carts from '@/api/lendingCart'
import type {BorrowAgainResult} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {useLendingCart} from '@/composables/useLendingCart'
import {useRouter} from 'vue-router'

/**
 * Puts the lines of an answered request of this station back in the cart. The days stay the cart's;
 * what the lending station no longer offers is named below.
 */
const props = defineProps<{
  requestId: number
}>()

const {t} = useI18n()
const routes = useInventoryRoutes()
const router = useRouter()
const cart = useLendingCart()
const result = ref<BorrowAgainResult | null>(null)

const {run, running, failure} = useAsyncAction(async () => {
  result.value = await carts.borrowAgain(props.requestId)
  await cart.reload()
})

const cartHref = routes.lendingCart ? router.resolve({name: routes.lendingCart}).href : ''
</script>

<template>
  <div class="mb-4 space-y-2">
    <SecondaryButton :icon="['fas', 'rotate']" :disabled="running" data-testid="lending-borrow-again" @click="run()">
      {{ t('lendingCart.borrowAgain') }}
    </SecondaryButton>
    <FailureAlert :failure="failure"/>
    <Alert v-if="result" :variant="result.skipped.length > 0 ? 'info' : 'success'" data-testid="lending-borrow-again-result">
      <p>
        {{ result.added === 1 ? t('lendingCart.againAddedOne') : t('lendingCart.againAdded', {count: result.added}) }}
        <AppLink v-if="cartHref" :href="cartHref">{{ t('lendingCart.openCart') }}</AppLink>
      </p>
      <p v-if="result.skipped.length > 0">{{ t('lendingCart.againSkipped', {names: result.skipped.join(', ')}) }}</p>
    </Alert>
  </div>
</template>
