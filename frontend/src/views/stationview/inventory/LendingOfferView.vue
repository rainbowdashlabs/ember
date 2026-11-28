/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref, useId, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import * as federation from '@/api/federation'
import * as lending from '@/api/lending'
import {StationPermission, type CatalogProduct} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useRouteQueryRef} from '@/composables/useRouteQueryRef'
import {useSession} from '@/composables/useSession'
import BulkActions from './lendingofferview/BulkActions.vue'
import CatalogCard from './lendingofferview/CatalogCard.vue'
import DescriptionDrawer from './lendingofferview/DescriptionDrawer.vue'
import {CATALOG_FILTERS, matchesFilter, rowsOf, type CatalogFilter, type CatalogRow} from './lendingofferview/catalogRows'

/**
 * The station's offer as partners see it, with the controls to change it: who each product, kind and
 * piece is offered to, its text and photo, several rows at once, and the offer as one partner sees it.
 */
const {t} = useI18n()
const {loaded, hasPermission} = useSession()
const editable = computed(() => hasPermission(StationPermission.INVENTORY_EDIT))

const filterRaw = useRouteQueryRef('filter', 'all')
const filter = computed<CatalogFilter>(() => (CATALOG_FILTERS as readonly string[]).includes(filterRaw.value) ? filterRaw.value as CatalogFilter : 'all')
const viewAs = useRouteQueryRef('viewAs')
const viewAsId = useId()

const products = ref<CatalogProduct[]>([])
const partners = ref<{value: string; label: string}[]>([])
const selected = ref(new Set<string>())

const {loading, failure, reload} = useAsyncLoader(async (isCurrent) => {
  if (!loaded.value) return
  const [catalog, partnerList] = await Promise.all([
    lending.getCatalog(viewAs.value ? Number(viewAs.value) : undefined),
    federation.listPartners(),
  ])
  if (!isCurrent()) return
  products.value = catalog
  partners.value = partnerList.map(entry => ({value: String(entry.partner.id), label: entry.partnerStationName}))
})
watch([loaded, viewAs], () => { void reload() })

const shown = computed(() => products.value.filter(product => matchesFilter(product, filter.value)))
const selectedRows = computed<CatalogRow[]>(() => shown.value.flatMap(rowsOf).filter(row => selected.value.has(row.key)))
watch(filter, () => { selected.value = new Set() })

function childrenOf(row: CatalogRow): CatalogRow[] {
  const product = products.value.find(entry => entry.inventoryId === row.inventoryId)
  return product ? rowsOf(product).filter(child => child.depth > 0) : []
}

function toggle(key: string, value: boolean) {
  const next = new Set(selected.value)
  if (value) next.add(key)
  else next.delete(key)
  selected.value = next
}

async function bulkDone() {
  selected.value = new Set()
  await reload()
}

const editing = ref<CatalogRow | null>(null)
const drawerOpen = computed({
  get: () => editing.value !== null,
  set: (value: boolean) => { if (!value) editing.value = null },
})
</script>

<template>
  <ViewContent :title="t('pages.inventory-lending-offer.title')" :subtitle="t('pages.inventory-lending-offer.subtitle')">
    <div class="mb-4 flex flex-wrap items-end justify-between gap-3">
      <div class="flex flex-wrap gap-1" role="group" :aria-label="t('lendingOffer.filterLabel')">
        <SelectionToggleButton v-for="option in CATALOG_FILTERS" :key="option" :selected="filter === option"
                               :data-testid="`lending-offer-filter-${option}`" @toggle="filterRaw = option">
          {{ t(`lendingOffer.filter.${option}`) }}
        </SelectionToggleButton>
      </div>
      <div class="space-y-1">
        <FieldLabel :for="viewAsId">{{ t('lendingOffer.viewAs') }}</FieldLabel>
        <SelectInput :id="viewAsId" v-model="viewAs" class="w-full sm:w-64" data-testid="lending-offer-view-as">
          <option value="">{{ t('lendingOffer.viewAsOwn') }}</option>
          <option v-for="partner in partners" :key="partner.value" :value="partner.value">{{ partner.label }}</option>
        </SelectInput>
      </div>
    </div>
    <BulkActions v-if="selectedRows.length > 0" :rows="selectedRows" :partners="partners" :children-of="childrenOf" class="mb-4"
                 @done="bulkDone"/>
    <AsyncSection :failure="failure" :loading="loading" :empty="shown.length === 0" :empty-message="t('lendingOffer.empty')">
      <div class="space-y-3">
        <CatalogCard v-for="product in shown" :key="product.inventoryId" :product="product" :selected="selected" :editable="editable"
                     @toggle="toggle" @edit="editing = $event" @changed="reload()"/>
      </div>
    </AsyncSection>
    <DescriptionDrawer v-if="editing" v-model="drawerOpen" :row="editing" @changed="reload()"/>
  </ViewContent>
</template>
