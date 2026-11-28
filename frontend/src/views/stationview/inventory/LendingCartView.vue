/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import * as carts from '@/api/lendingCart'
import type {CartDetails, CartLineCheck, CartView, CheckoutView} from '@/api/generated/schema'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import {useLendingCart} from '@/composables/useLendingCart'
import CartDetailsForm from './lendingcartview/CartDetailsForm.vue'
import CartLineRow from './lendingcartview/CartLineRow.vue'
import CheckoutOutcome from './lendingcartview/CheckoutOutcome.vue'
import {checkoutBlock, groupByStation} from './lendingcartview/cartGroups'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const routes = useInventoryRoutes()
const cart = useLendingCart()

const checks = ref<Map<number, CartLineCheck> | null>(null)
const outcome = ref<CheckoutView | null>(null)

const {loading, failure} = useAsyncLoader(async (isCurrent) => {
  const loaded = await cart.load()
  if (!isCurrent()) return
  await adoptShopDays(loaded)
  void recheck()
})

/**
 * Takes the days chosen in the shop into a cart that has none yet, so the cart is checked and sent
 * for the days its gear was picked for.
 */
async function adoptShopDays(loaded: CartView) {
  const from = typeof route.query.from === 'string' ? route.query.from : ''
  if (loaded.dateFrom || !from) return
  const to = typeof route.query.to === 'string' ? route.query.to : ''
  await cart.setDetails({dateFrom: from, dateTo: to || null, occasion: loaded.occasion, note: loaded.note})
      .catch(() => undefined)
}

const {run: recheck, running: checking, failure: checkFailure} = useAsyncAction(async () => {
  checks.value = null
  if (cart.lines.value.length === 0) return
  const answer = await carts.checkCart()
  checks.value = new Map(answer.map(check => [check.lineId, check]))
}, {coalesce: true})

const {run: change, running: changing, failure: changeFailure} = useAsyncAction(async (step: () => Promise<unknown>) => {
  outcome.value = null
  await step()
  void recheck()
})

const {run: checkout, running: sending, failure: sendFailure} = useAsyncAction(async () => {
  try {
    outcome.value = await carts.checkoutCart()
    cart.replace(await carts.getCart())
  } finally {
    void recheck()
  }
})

const groups = computed(() => groupByStation(cart.lines.value))
const details = computed(() => ({
  dateFrom: cart.cart.value?.dateFrom ?? '',
  dateTo: cart.cart.value?.dateTo ?? '',
  occasion: cart.cart.value?.occasion ?? '',
  note: cart.cart.value?.note ?? '',
}))
const block = computed(() => checking.value ? 'unchecked' : checkoutBlock(cart.lines.value, details.value.dateFrom || null, checks.value))
const blockHint = computed(() => {
  switch (block.value) {
    case 'noDay': return t('lendingCartPage.needsDay')
    case 'notFree': return t('lendingCartPage.needsFree')
    case 'unchecked': return checkFailure.value ? t('lendingCartPage.checkFailed') : t('lendingCartPage.checking')
    default: return ''
  }
})
const busy = computed(() => changing.value || sending.value)

function saveDetails(next: CartDetails) {
  void change(() => cart.setDetails(next))
}

function backToShop() {
  void router.push({name: routes.lendingShop, query: route.query})
}

</script>

<template>
  <ViewContent :title="t('pages.inventory-lending-cart.title')" :subtitle="t('pages.inventory-lending-cart.subtitle')">
    <SecondaryButton :icon="['fas', 'chevron-left']" class="mb-4" @click="backToShop">{{ t('lendingProduct.back') }}</SecondaryButton>
    <CheckoutOutcome v-if="outcome" :outcome="outcome" class="mb-4"/>
    <AsyncSection :failure="failure" :loading="loading" :empty="cart.lines.value.length === 0"
                  :empty-message="t('lendingCartPage.empty')">
      <div class="space-y-6">
        <section v-for="group in groups" :key="group.stationId" class="space-y-2" data-testid="lending-cart-station">
          <div class="flex flex-wrap items-center gap-2">
            <SubHeader>{{ t('lendingCartPage.requestTo') }}</SubHeader>
            <StationBadge :station-name="group.stationName"/>
          </div>
          <ul class="divide-y divide-(--border) rounded-theme border border-(--border)">
            <CartLineRow v-for="line in group.lines" :key="line.id" :line="line" :check="checks?.get(line.id)" :busy="busy"
                         @quantity="quantity => change(() => cart.setQuantity(line.id, quantity))"
                         @remove="change(() => cart.remove(line.id))"/>
          </ul>
        </section>
        <section class="space-y-2">
          <SubHeader>{{ t('lendingCartPage.forAll') }}</SubHeader>
          <CartDetailsForm :details="details" :disabled="busy" @change="saveDetails"/>
        </section>
        <FailureAlert :failure="changeFailure ?? checkFailure ?? sendFailure"/>
        <MutedText v-if="blockHint" tag="p" data-testid="lending-cart-block">{{ blockHint }}</MutedText>
        <ButtonRow align="end">
          <SecondaryButton :icon="['fas', 'rotate']" :disabled="checking || busy" @click="recheck()">
            {{ t('lendingCartPage.recheck') }}
          </SecondaryButton>
          <PrimaryButton :icon="['fas', 'paper-plane']" :disabled="block !== null || busy" data-testid="lending-cart-send"
                         @click="checkout()">
            {{ groups.length === 1 ? t('lendingCartPage.sendOne') : t('lendingCartPage.send', {count: groups.length}) }}
          </PrimaryButton>
        </ButtonRow>
      </div>
    </AsyncSection>
  </ViewContent>
</template>
