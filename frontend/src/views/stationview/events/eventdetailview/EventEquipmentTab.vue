/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EquipmentNeedRow from './equipmenttab/EquipmentNeedRow.vue'
import EquipmentNeedModal from './equipmenttab/EquipmentNeedModal.vue'
import {useEquipmentNeeds} from '@/composables/useEquipmentNeeds'
import {useRouter} from 'vue-router'
import {DEFAULT_LEAD_MINUTES} from '@/api/equipment'

const props = defineProps<{
  eventId: number
  /** The evening the panel answers for, which is what a claim can hold stock over. */
  effectiveDate: string | null
  recurring: boolean
  canEdit: boolean
}>()

const {t} = useI18n()
const router = useRouter()

const eventId = computed(() => props.eventId)
const date = computed(() => props.effectiveDate)
const needs = useEquipmentNeeds(eventId, date)

const showModal = ref(false)
const kind = ref<'item' | 'art' | 'inventory'>('art')
const itemId = ref('')
const artId = ref('')
const inventoryId = ref('')
const quantity = ref(1)
const leadHours = ref(DEFAULT_LEAD_MINUTES / 60)
const trailHours = ref(DEFAULT_LEAD_MINUTES / 60)
const thisEveningOnly = ref(false)

const missingTotal = computed(() => needs.coverage.value.reduce((sum, line) => sum + line.missing, 0))

onMounted(async () => {
  await needs.loadCoverage()
  if (props.canEdit) await needs.loadPickers()
})

watch(date, () => needs.loadCoverage())

function openModal() {
  itemId.value = ''
  artId.value = ''
  inventoryId.value = ''
  quantity.value = 1
  thisEveningOnly.value = false
  showModal.value = true
}

/** Carries the appointment and the evening into the browser, which is what the request is for. */
function borrow() {
  router.push({path: '/station/inventory/lending/collect', query: {eventId: props.eventId, date: props.effectiveDate}})
}

async function submit() {
  const done = await needs.add({
    kind: kind.value,
    itemId: itemId.value,
    artId: artId.value,
    inventoryId: inventoryId.value,
    quantity: quantity.value,
    leadHours: leadHours.value,
    trailHours: trailHours.value,
    thisEveningOnly: thisEveningOnly.value,
  })
  if (done) showModal.value = false
}
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="event-equipment">
    <div class="flex flex-wrap items-center justify-between gap-2">
      <SubHeader>{{ t('eventEquipment.title') }}</SubHeader>
      <PrimaryButton v-if="canEdit" data-testid="equipment-add" @click="openModal">
        {{ t('eventEquipment.addLine') }}
      </PrimaryButton>
    </div>

    <Alert v-if="needs.error.value" variant="error">{{ needs.error.value }}</Alert>
    <Spinner v-if="needs.loading.value" size="sm"/>

    <p v-else-if="!effectiveDate" class="text-sm text-(--text-muted)">{{ t('eventEquipment.noDate') }}</p>

    <p v-else-if="needs.coverage.value.length === 0" class="text-sm text-(--text-muted)" data-testid="equipment-empty">
      {{ t('eventEquipment.empty') }}
    </p>

    <div v-else>
      <EquipmentNeedRow
          v-for="line in needs.coverage.value"
          :key="line.need.id"
          :coverage="line"
          :editable="canEdit"
          @remove="needs.remove"
      />
    </div>

    <SecondaryButton v-if="missingTotal > 0" data-testid="equipment-borrow" @click="borrow">
      {{ t('eventEquipment.borrowMissing', {count: missingTotal}) }}
    </SecondaryButton>

    <EquipmentNeedModal
        v-model:show="showModal"
        v-model:kind="kind"
        v-model:item-id="itemId"
        v-model:art-id="artId"
        v-model:inventory-id="inventoryId"
        v-model:quantity="quantity"
        v-model:lead-hours="leadHours"
        v-model:trail-hours="trailHours"
        v-model:this-evening-only="thisEveningOnly"
        :inventories="needs.inventories.value"
        :items="needs.items.value"
        :arts="needs.arts.value"
        :recurring="recurring"
        :saving="needs.saving.value"
        @submit="submit"
    />
  </NeutralContainer>
</template>
