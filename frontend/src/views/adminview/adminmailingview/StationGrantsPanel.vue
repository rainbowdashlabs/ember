/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import StationGrantsTable from './StationGrantsTable.vue'
import {stationGrantColumns} from './stationGrantColumns'
import {grantInstanceMailTo, listInstanceMailStations, withdrawInstanceMailFrom} from '@/api/instanceMail'
import type {InstanceMailStation} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {useDataTable} from '@/composables/useDataTable'

/**
 * Which stations may send their own mail through the instance's providers, granted and withdrawn for
 * many at once. A single station is set on its own page as well.
 */
const {t} = useI18n()

const stations = ref<InstanceMailStation[]>([])
const selected = ref(new Set<string>())
const dailyLimit = ref<number | undefined>(undefined)

const {loading, failure} = useAsyncLoader(async (isCurrent) => {
  const listed = await listInstanceMailStations()
  if (isCurrent()) stations.value = listed
})

const table = useDataTable<InstanceMailStation>({
  id: 'instance-mail-stations',
  perStation: false,
  rows: () => stations.value,
  columns: computed(() => stationGrantColumns(t)),
  rowKey: station => station.stationUid,
})

function toggle(stationUid: string) {
  const next = new Set(selected.value)
  if (!next.delete(stationUid)) next.add(stationUid)
  selected.value = next
}

/** The limit as typed, or none where the field was left empty. */
const limit = computed(() => dailyLimit.value === undefined || Number.isNaN(dailyLimit.value) ? null : dailyLimit.value)

const {running, failure: actionFailure, run: apply} = useAsyncAction(async (grant: boolean) => {
  const uids = [...selected.value]
  stations.value = grant ? await grantInstanceMailTo(uids, limit.value) : await withdrawInstanceMailFrom(uids)
  selected.value = new Set()
})
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('instanceMail.stations.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('instanceMail.stations.hint') }}</MutedText>
    <Spinner v-if="loading" size="md"/>
    <FailureAlert :failure="failure ?? actionFailure"/>
    <StationGrantsTable v-if="!loading" :table="table" :selected="selected" @toggle="toggle"/>
    <LabelledField :label="t('instanceMail.stations.dailyLimit')" :help="t('instanceMail.stations.dailyLimitHint')" hint>
      <NumberInput v-model="dailyLimit" :placeholder="t('instanceMail.stations.noLimit')"/>
    </LabelledField>
    <ButtonRow align="end">
      <ErrorButton :disabled="running || selected.size === 0" data-testid="instance-mail-withdraw" @click="apply(false)">
        {{ t('instanceMail.stations.withdraw', {count: selected.size}) }}
      </ErrorButton>
      <PrimaryButton :disabled="running || selected.size === 0" data-testid="instance-mail-grant" @click="apply(true)">
        {{ t('instanceMail.stations.grant', {count: selected.size}) }}
      </PrimaryButton>
    </ButtonRow>
  </NeutralContainer>
</template>
