/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import LockRow from './clusterlookandfeelview/LockRow.vue'
import {clusterGovernance} from '@/api'
import type {ClusterLookAndFeel} from '@/api/clusterGovernance'
import {useConfigPanel} from '@/composables/useConfigPanel'

const {t} = useI18n()

/** The feels a station can be given, matching what the station's own screen offers. */
const FEELS = ['ROUNDED', 'CORNERS']

const busy = ref(false)
const saved = ref(false)

const {config: look, loading, error, runWith} = useConfigPanel<ClusterLookAndFeel>({
  initial: {themeLocked: false, colorsLocked: false, feelLocked: false, logoLocked: false},
  fetch: () => clusterGovernance.getLookAndFeel(),
})

async function save() {
  saved.value = false
  await runWith(async () => {
    await clusterGovernance.setLookAndFeel(look.value)
    saved.value = true
    return clusterGovernance.getLookAndFeel()
  }, {busy})
}
</script>

<template>
  <ViewContent :subtitle="t('pages.cluster-look-and-feel.subtitle')" :title="t('pages.cluster-look-and-feel.title')">
    <div class="space-y-4">
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="saved" variant="success">{{ t('clusterLookAndFeel.saved') }}</Alert>

      <Spinner v-if="loading" size="lg"/>

      <template v-else>
        <p class="text-sm text-(--text-muted)">{{ t('clusterLookAndFeel.hint') }}</p>

        <NeutralContainer class="space-y-4">
          <SectionHeader>{{ t('clusterLookAndFeel.settingsTitle') }}</SectionHeader>

          <div class="space-y-1">
            <FormLabel>{{ t('clusterLookAndFeel.feelLabel') }}</FormLabel>
            <SelectInput v-model="look.defaultFeel">
              <option :value="null">{{ t('clusterLookAndFeel.noOpinion') }}</option>
              <option v-for="feel in FEELS" :key="feel" :value="feel">
                {{ t(`stationTheme.feel.${feel}`) }}
              </option>
            </SelectInput>
          </div>
        </NeutralContainer>

        <NeutralContainer class="space-y-3">
          <SectionHeader>{{ t('clusterLookAndFeel.locksTitle') }}</SectionHeader>
          <p class="text-sm text-(--text-muted)">{{ t('clusterLookAndFeel.locksHint') }}</p>

          <LockRow v-model="look.themeLocked" :label="t('clusterLookAndFeel.lockTheme')"/>
          <LockRow v-model="look.colorsLocked" :label="t('clusterLookAndFeel.lockColors')"/>
          <LockRow v-model="look.feelLocked" :label="t('clusterLookAndFeel.lockFeel')"/>
          <LockRow v-model="look.logoLocked" :label="t('clusterLookAndFeel.lockLogo')"/>
        </NeutralContainer>

        <PrimaryButton :disabled="busy" @click="save">{{ t('common.save') }}</PrimaryButton>
      </template>
    </div>
  </ViewContent>
</template>
