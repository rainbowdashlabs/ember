/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FormLabel from '@/components/input/FormLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import {clusters} from '@/api'
import {useCluster} from '@/composables/useCluster'
import {describeFailure, type Failure} from '@/util/failure'

const {t} = useI18n()
const {load: loadClusters} = useCluster()

const name = ref('')
const description = ref('')
const autoFederate = ref(true)
const loading = ref(true)
const saving = ref(false)
const failure = ref<Failure | null>(null)
const saved = ref(false)

onMounted(async () => {
  try {
    const cluster = await clusters.getActive()
    name.value = cluster.name
    description.value = cluster.description ?? ''
    autoFederate.value = cluster.autoFederate
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('clusterSettings.loadFailed')}
  } finally {
    loading.value = false
  }
})

/**
 * Stores the association's details, then tells the switcher, which shows the name and would
 * otherwise keep the old one after a rename.
 *
 * <p>The two are answered for separately: a rename that was stored and a switcher that then failed
 * to hear about it used to report a failed save, and a reader told that saves the same name again.
 */
async function save() {
  if (!name.value.trim()) return
  saving.value = true
  failure.value = null
  saved.value = false
  try {
    await clusters.updateActive({
      name: name.value.trim(),
      description: description.value.trim() || null,
      autoFederate: autoFederate.value,
    })
    saved.value = true
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('clusterSettings.saveFailed')}
    return
  } finally {
    saving.value = false
  }
  try {
    await loadClusters()
  } catch (e) {
    failure.value = {...describeFailure(e, t), message: t('failure.staleAfterAction')}
  }
}
</script>

<template>
  <ViewContent :title="t('clusterSettings.title')" :subtitle="t('clusterSettings.subtitle')">
    <Spinner v-if="loading"/>

    <div v-else class="space-y-4">
      <FailureAlert :failure="failure"/>
      <Alert v-if="saved" variant="success">{{ t('clusterSettings.saved') }}</Alert>

      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('clusterSettings.generalTitle') }}</SectionHeader>

        <div class="space-y-1">
          <FormLabel>{{ t('clusterSettings.nameLabel') }}</FormLabel>
          <TextInput v-model="name" :placeholder="t('clusterSettings.namePlaceholder')"/>
        </div>

        <div class="space-y-1">
          <FormLabel>{{ t('clusterSettings.descriptionLabel') }}</FormLabel>
          <TextAreaInput v-model="description" :placeholder="t('clusterSettings.descriptionPlaceholder')"/>
        </div>

        <PrimaryButton :disabled="saving || !name.trim()" @click="save">
          {{ t('common.save') }}
        </PrimaryButton>
      </NeutralContainer>

      <NeutralContainer class="space-y-4">
        <SectionHeader>{{ t('clusterSettings.federationTitle') }}</SectionHeader>

        <div class="flex items-start justify-between gap-4">
          <div>
            <FormLabel>{{ t('clusterSettings.autoFederateLabel') }}</FormLabel>
            <p class="text-sm text-(--text-muted)">{{ t('clusterSettings.autoFederateHint') }}</p>
          </div>
          <ToggleInput v-model="autoFederate"/>
        </div>

        <p class="text-sm text-(--text-muted)">{{ t('clusterSettings.autoFederateOffHint') }}</p>
      </NeutralContainer>
    </div>
  </ViewContent>
</template>
