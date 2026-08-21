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

const {t} = useI18n()
const {load: loadClusters} = useCluster()

const name = ref('')
const description = ref('')
const autoFederate = ref(true)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const saved = ref(false)

onMounted(async () => {
  try {
    const cluster = await clusters.getActive()
    name.value = cluster.name
    description.value = cluster.description ?? ''
    autoFederate.value = cluster.autoFederate
  } catch {
    error.value = t('clusterSettings.loadFailed')
  } finally {
    loading.value = false
  }
})

async function save() {
  if (!name.value.trim()) return
  saving.value = true
  error.value = ''
  saved.value = false
  try {
    await clusters.updateActive({
      name: name.value.trim(),
      description: description.value.trim() || null,
      autoFederate: autoFederate.value,
    })
    // The switcher shows the name, so it has to hear about the rename
    await loadClusters()
    saved.value = true
  } catch {
    error.value = t('clusterSettings.saveFailed')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <ViewContent :title="t('clusterSettings.title')" :subtitle="t('clusterSettings.subtitle')">
    <Spinner v-if="loading"/>

    <div v-else class="space-y-4">
      <Alert v-if="error" variant="error">{{ error }}</Alert>
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
