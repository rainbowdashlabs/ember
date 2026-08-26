/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import {clusterInventory} from '@/api'
import {LossReportRequirement, type LossReportRequirementName} from '@/api/inventory'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useAsyncAction} from '@/composables/useAsyncAction'

/**
 * What a station has to bring when it reports a piece of the association's gear missing.
 *
 * <p>The loss is not the association's to accept or refuse: it has happened and it is recorded. This is
 * about the replacement, and about what the association wants to read before it decides on one.
 */
const {t} = useI18n()

const requires = ref<LossReportRequirementName>(LossReportRequirement.NOTHING)

const {loading, error} = useAsyncLoader(async () => {
  requires.value = (await clusterInventory.getLossReportSettings()).requires
})

const {running: saving, error: saveError, run: save} = useAsyncAction(async (value: LossReportRequirementName) => {
  await clusterInventory.setLossReportSettings(value)
  requires.value = value
})
</script>

<template>
  <NeutralContainer class="space-y-3" data-testid="loss-report-setting">
    <SectionHeader>{{ t('clusterInventory.lossReportTitle') }}</SectionHeader>
    <p class="text-sm text-(--text-muted)">{{ t('clusterInventory.lossReportHint') }}</p>
    <Alert v-if="error || saveError" variant="error">{{ error || saveError }}</Alert>
    <SelectInput
        v-if="!loading"
        :disabled="saving"
        :model-value="requires"
        class="w-64"
        data-testid="loss-report-requires"
        @update:model-value="v => save(v as LossReportRequirementName)"
    >
      <option
          v-for="option in [LossReportRequirement.NOTHING, LossReportRequirement.NOTE, LossReportRequirement.DOCUMENT]"
          :key="option" :value="option">
        {{ t(`clusterInventory.lossReportRequires.${option}`) }}
      </option>
    </SelectInput>
  </NeutralContainer>
</template>
