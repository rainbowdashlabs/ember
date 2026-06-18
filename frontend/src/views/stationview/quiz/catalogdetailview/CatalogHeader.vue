/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import SecondaryBadge from '@/components/badge/SecondaryBadge.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import type { QuizCatalogDetail } from '@/api/types'

const props = defineProps<{
  catalog: QuizCatalogDetail
  isFederated: boolean
  readonly?: boolean
}>()

const emit = defineEmits<{
  save: [payload: { name: string; description: string; trainingEnabled: boolean }]
  copyToStation: []
}>()

const { t } = useI18n()

const editName = ref(props.catalog.name)
const editDescription = ref(props.catalog.description)
const editTrainingEnabled = ref(props.catalog.trainingEnabled)
const catalogDirty = ref(false)

function markCatalogDirty() {
  catalogDirty.value = true
}

function saveCatalog() {
  if (!editName.value.trim()) return
  emit('save', {
    name: editName.value.trim(),
    description: editDescription.value.trim(),
    trainingEnabled: editTrainingEnabled.value,
  })
  catalogDirty.value = false
}

function resetForm() {
  editName.value = props.catalog.name
  editDescription.value = props.catalog.description
  editTrainingEnabled.value = props.catalog.trainingEnabled
  catalogDirty.value = false
}

defineExpose({ resetForm })
</script>

<template>
  <!-- Federated: Copy to station button -->
  <NeutralContainer v-if="isFederated">
    <div class="space-y-4">
      <div class="flex items-center gap-2 flex-wrap">
        <PageHeader>{{ catalog.name }}</PageHeader>
        <StationBadge :station-name="''" />
      </div>
      <p v-if="catalog.description" class="text-sm text-(--text-muted)">{{ catalog.description }}</p>
      <div v-if="catalog.questionTypeCounts && Object.keys(catalog.questionTypeCounts).length > 0" class="flex flex-wrap gap-2">
        <InfoBadge v-for="(count, type) in catalog.questionTypeCounts" :key="type">
          {{ t(`quiz.questionTypes.${type}`) }}: {{ count }}
        </InfoBadge>
        <SecondaryBadge>{{ t('quiz.catalogs.questionCount', { count: catalog.questionCount }) }}</SecondaryBadge>
      </div>
      <div class="flex justify-end">
        <PrimaryButton :icon="['fas', 'copy']" @click="emit('copyToStation')">
          {{ t('federation.copyToStation') }}
        </PrimaryButton>
      </div>
    </div>
  </NeutralContainer>

  <!-- Catalog Metadata -->
  <NeutralContainer v-else>
    <div class="space-y-4">
      <PageHeader>{{ catalog.name }}</PageHeader>
      <div v-if="catalog.questionTypeCounts && Object.keys(catalog.questionTypeCounts).length > 0" class="flex flex-wrap gap-2">
        <InfoBadge v-for="(count, type) in catalog.questionTypeCounts" :key="type">
          {{ t(`quiz.questionTypes.${type}`) }}: {{ count }}
        </InfoBadge>
        <SecondaryBadge>{{ t('quiz.catalogs.questionCount', { count: catalog.questionCount }) }}</SecondaryBadge>
      </div>
      <p v-if="readonly && catalog.description" class="text-sm text-(--text-muted)">{{ catalog.description }}</p>
      <template v-if="!readonly">
        <TextInput v-model="editName" :placeholder="t('quiz.catalogs.name')" @update:model-value="markCatalogDirty" />
        <TextAreaInput v-model="editDescription" :placeholder="t('quiz.catalogs.description')" @update:model-value="markCatalogDirty" />
        <FieldLabel inline>
          <ToggleInput v-model="editTrainingEnabled" @update:model-value="markCatalogDirty" />
          {{ t('quiz.catalogs.trainingEnabled') }}
        </FieldLabel>
        <div v-if="catalogDirty" class="flex justify-end">
          <SaveButton :action="saveCatalog"/>
        </div>
      </template>
    </div>
  </NeutralContainer>
</template>
