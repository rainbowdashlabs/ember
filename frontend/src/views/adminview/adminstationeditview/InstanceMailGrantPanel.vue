/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import MutedText from '@/components/typography/MutedText.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ToggleSetting from '@/components/input/toggle/ToggleSetting.vue'
import LabelledField from '@/components/input/LabelledField.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import ButtonRow from '@/components/button/ButtonRow.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import {getInstanceMailStation, grantInstanceMail, withdrawInstanceMail} from '@/api/instanceMail'
import type {InstanceMailStation} from '@/api/generated/schema'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {describeFailure, type Failure} from '@/util/failure'

/**
 * Whether one station may send its own mail through the instance's providers, after its own, and how
 * much a day. An empty limit leaves the station only the share all stations have together.
 */
const props = defineProps<{
  stationUid: string
}>()

const {t} = useI18n()

const station = ref<InstanceMailStation | null>(null)
const granted = ref(false)
const dailyLimit = ref<number | undefined>(undefined)
const saveFailure = ref<Failure | null>(null)

function show(loaded: InstanceMailStation) {
  station.value = loaded
  granted.value = loaded.granted
  dailyLimit.value = loaded.dailyLimit ?? undefined
}

const {loading, failure} = useAsyncLoader(async (isCurrent) => {
  const loaded = await getInstanceMailStation(props.stationUid)
  if (isCurrent()) show(loaded)
})

async function save() {
  saveFailure.value = null
  try {
    const limit = dailyLimit.value === undefined || Number.isNaN(dailyLimit.value) ? null : dailyLimit.value
    show(granted.value
        ? await grantInstanceMail(props.stationUid, limit)
        : await withdrawInstanceMail(props.stationUid))
  } catch (e) {
    saveFailure.value = describeFailure(e, t)
    throw e
  }
}
</script>

<template>
  <NeutralContainer class="space-y-4">
    <SectionHeader>{{ t('instanceMail.grant.title') }}</SectionHeader>
    <MutedText tag="p" size="sm">{{ t('instanceMail.grant.hint') }}</MutedText>
    <Spinner v-if="loading" size="md"/>
    <FailureAlert :failure="failure ?? saveFailure"/>
    <template v-if="station">
      <ToggleSetting v-model="granted" :label="t('instanceMail.grant.toggle')"/>
      <LabelledField v-if="granted" :label="t('instanceMail.grant.dailyLimit')" :help="t('instanceMail.grant.dailyLimitHint')" hint>
        <NumberInput v-model="dailyLimit" :placeholder="t('instanceMail.grant.noLimit')"/>
      </LabelledField>
      <MutedText v-if="station.granted" tag="p" size="sm" data-testid="instance-mail-sent-today">
        {{ t('instanceMail.grant.sentToday', {count: station.sentToday}) }}
      </MutedText>
      <ButtonRow align="end">
        <SaveButton :action="save"/>
      </ButtonRow>
    </template>
  </NeutralContainer>
</template>
