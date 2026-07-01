/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import ErrorButton from '@/components/button/ErrorButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import ViewContent from '@/components/layout/ViewContent.vue'
import Modal from '@/components/feedback/Modal.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import {useStations} from '@/composables/useStations'
import {transfer} from '@/api'

const {t} = useI18n()
const {activeStation} = useStations()

const stationName = computed(() => activeStation.value?.stationName ?? '')

const confirmOpen = ref(false)
const typedName = ref('')
const deleting = ref(false)
const error = ref<string | null>(null)

const matchesName = computed(() => typedName.value.trim() === stationName.value.trim() && stationName.value !== '')

function openConfirm() {
  typedName.value = ''
  error.value = null
  confirmOpen.value = true
}

async function performDelete() {
  if (!matchesName.value) return
  deleting.value = true
  error.value = null
  try {
    await transfer.deleteMovedStation()
    window.location.href = '/'
  } catch (e) {
    error.value = t('pages.station-moved.deleteError')
    deleting.value = false
    confirmOpen.value = false
  }
}
</script>

<template>
  <ViewContent :title="t('pages.station-moved-delete.title')" :subtitle="t('pages.station-moved-delete.subtitle')">
    <div class="flex flex-col gap-6 py-8 max-w-2xl mx-auto">
      <p class="text-text/80">{{ t('pages.station-moved.deleteBody') }}</p>
      <ErrorContainer>
        <strong>{{ t('pages.station-moved.deleteWarning') }}</strong>
      </ErrorContainer>
      <ErrorContainer v-if="error">{{ error }}</ErrorContainer>
      <div>
        <ErrorButton @click="openConfirm">
          <font-awesome-icon :icon="['fas', 'trash']" class="h-4 w-4 mr-2"/>
          {{ t('pages.station-moved.deleteConfirm') }}
        </ErrorButton>
      </div>

      <Modal v-model="confirmOpen" size="md">
        <SectionHeader>{{ t('pages.station-moved.deleteModalTitle') }}</SectionHeader>
        <p class="mt-4 text-text/80">{{ t('pages.station-moved.deleteModalBody') }}</p>
        <p class="mt-4 text-text/80">
          {{ t('pages.station-moved.deleteModalTypeName', {name: stationName}) }}
        </p>
        <TextInput v-model="typedName" :placeholder="stationName" class="mt-2"/>
        <div class="mt-6 flex justify-end gap-2">
          <SecondaryButton :disabled="deleting" @click="confirmOpen = false">
            {{ t('pages.station-moved.deleteModalCancel') }}
          </SecondaryButton>
          <ErrorButton :disabled="deleting || !matchesName" @click="performDelete">
            <font-awesome-icon :icon="['fas', 'trash']" class="h-4 w-4 mr-2"/>
            {{ t('pages.station-moved.deleteModalConfirm') }}
          </ErrorButton>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
