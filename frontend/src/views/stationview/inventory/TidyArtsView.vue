/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import AsyncSection from '@/components/feedback/AsyncSection.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Alert from '@/components/feedback/Alert.vue'
import type {InventoryDetail, InventoryItem} from '@/api/inventory'
import type {InventoryArt, ItemNameCount} from '@/api/inventoryArts'
import {inventory, inventoryArts} from '@/api'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useInventoryRoutes} from '@/composables/useInventoryRoutes'
import TidyNamesTable from './tidyartsview/TidyNamesTable.vue'
import TidyArtTarget from './tidyartsview/TidyArtTarget.vue'

/**
 * Tidying the names written on the pieces into kinds.
 *
 * <p>Eighteen rows say nothing. Six names with a six, a five, a four and three ones say where the
 * typo is, and that is what this screen shows first.
 *
 * <p>It does more than hand out kinds, because handing out a kind on its own corrects nothing
 * anybody can see: the name is what every list, both exports and the notification texts read, so
 * {@code Funkgerät organge} would go on reading {@code Funkgerät organge} under a heading that says
 * otherwise. Merging therefore rewrites the names of the pieces it merges. That is a destructive
 * edit, which is why it belongs to somebody choosing it here and never to a migration.
 */
const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const routes = useInventoryRoutes()

const inventoryId = computed(() => Number(route.params.id))

const detail = ref<InventoryDetail | null>(null)
const names = ref<ItemNameCount[]>([])
const items = ref<InventoryItem[]>([])
const arts = ref<InventoryArt[]>([])
const selectedNames = ref<Set<string>>(new Set())
const targetArtId = ref<number | null>(null)
const targetName = ref('')
const done = ref(0)

const {loading, error, reload} = useAsyncLoader(async () => {
  const [inv, counts, invItems, allArts] = await Promise.all([
    inventory.getInventory(inventoryId.value),
    inventoryArts.itemNames(inventoryId.value),
    inventory.listItems(inventoryId.value),
    inventoryArts.listArts(inventoryId.value),
  ])
  detail.value = inv
  names.value = counts
  items.value = invItems
  arts.value = allArts
})

/** Kinds live only in an inventory that holds a drawer of different things. */
const heterogeneous = computed(() => detail.value?.homogeneous === false)

/** The pieces behind the ticked names, which is what a tidying write actually takes. */
const selectedItemIds = computed(() =>
    items.value.filter(item => selectedNames.value.has(item.name ?? '')).map(item => item.id),
)

const selectedPieces = computed(() => selectedItemIds.value.length)

/** The name the merge would write, which is the picked kind's or the one typed in. */
const effectiveName = computed(() => {
    const picked = arts.value.find(art => art.id === targetArtId.value)
    return picked ? picked.name : targetName.value.trim()
})

const canTidy = computed(() => selectedPieces.value > 0 && effectiveName.value !== '')

function toggleName(name: string) {
  const next = new Set(selectedNames.value)
  if (next.has(name)) next.delete(name)
  else next.add(name)
  selectedNames.value = next
  // The commonest ticked name is the likeliest right spelling, so it is offered rather than imposed.
  if (!targetArtId.value && !targetName.value && next.size > 0) {
    targetName.value = names.value.find(row => next.has(row.name))?.name ?? ''
  }
}

/** The kind the action needs, written down only now that somebody has chosen to act. */
async function resolveArt(): Promise<number> {
  if (targetArtId.value) return targetArtId.value
  const id = await inventoryArts.ensureArt(inventoryId.value, arts.value, targetName.value)
  if (id == null) throw new Error('no art')
  return id
}

async function refresh() {
  selectedNames.value = new Set()
  targetArtId.value = null
  targetName.value = ''
  await reload()
}

const {running: merging, error: mergeError, run: runMerge} = useAsyncAction(async () => {
  const artId = await resolveArt()
  const result = await inventoryArts.mergeIntoArt(inventoryId.value, artId, selectedItemIds.value)
  done.value = result.changed
  await refresh()
  return true
})

const {running: assigning, error: assignError, run: runAssign} = useAsyncAction(async () => {
  const artId = await resolveArt()
  const result = await inventoryArts.assignArt(inventoryId.value, artId, selectedItemIds.value)
  done.value = result.changed
  await refresh()
  return true
})
</script>

<template>
  <ViewContent
      :title="t('pages.inventory-tidy.title')"
      :subtitle="t('pages.inventory-tidy.subtitle')"
  >
    <div class="space-y-6">
      <SecondaryButton :icon="['fas', 'chevron-left']"
                       @click="router.push({name: routes.edit, params: {id: inventoryId}})">
        {{ t('inventory.art.backToInventory') }}
      </SecondaryButton>

      <AsyncSection :loading="loading" :error="error">
        <EmptyState v-if="!heterogeneous" :message="t('inventory.art.onlyForDrawers')"/>
        <div v-else class="space-y-6">
          <p class="text-sm text-(--text-muted)">{{ t('inventory.art.tidyIntro') }}</p>

          <Alert v-if="mergeError || assignError" variant="error">{{ mergeError || assignError }}</Alert>
          <Alert v-if="done > 0" variant="success" data-testid="tidy-done">
            {{ t('inventory.art.tidied', {count: done}) }}
          </Alert>

          <EmptyState v-if="names.length === 0" :message="t('inventory.art.nothingToTidy')"/>

          <template v-else>
            <TidyNamesTable :names="names" :selected="selectedNames" @toggle="toggleName"/>

            <TidyArtTarget
                v-model:artId="targetArtId"
                v-model:name="targetName"
                :arts="arts"
            />

            <div class="space-y-2">
              <p class="text-sm">{{ t('inventory.art.willRename', {count: selectedPieces, name: effectiveName}) }}</p>
              <div class="flex flex-wrap gap-2">
                <PrimaryButton :disabled="!canTidy || merging" :icon="['fas', 'broom']"
                               data-testid="tidy-merge" @click="runMerge()">
                  {{ t('inventory.art.mergeSubmit') }}
                </PrimaryButton>
                <SecondaryButton :disabled="!canTidy || assigning" :icon="['fas', 'tags']"
                                 data-testid="tidy-assign" @click="runAssign()">
                  {{ t('inventory.art.assignSubmit') }}
                </SecondaryButton>
              </div>
              <p class="text-xs text-(--text-muted)">{{ t('inventory.art.assignHint') }}</p>
            </div>
          </template>
        </div>
      </AsyncSection>
    </div>
  </ViewContent>
</template>
