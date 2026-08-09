/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import InfoContainer from '@/components/container/InfoContainer.vue'
import ErrorContainer from '@/components/container/ErrorContainer.vue'
import PageHeader from '@/components/typography/PageHeader.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import TabBar from '@/components/navigation/TabBar.vue'
import Modal from '@/components/feedback/Modal.vue'
import {useSession} from '@/composables/useSession'
import {useAsyncAction} from '@/composables/useAsyncAction'
import {StationPermission} from '@/api/types'
import {transfer} from '@/api'

const props = defineProps<{
  targetInstanceUrl: string
}>()

const {t} = useI18n()
const {hasPermission} = useSession()

const canDelete = computed(() => hasPermission(StationPermission.STATION_ADMINISTRATOR))

const tabs = computed(() =>
    canDelete.value
        ? [
            {key: 'info', label: t('pages.station-moved.tabInfo')},
            {key: 'delete', label: t('pages.station-moved.tabDelete')},
        ]
        : [{key: 'info', label: t('pages.station-moved.tabInfo')}],
)

const activeTab = ref<'info' | 'delete'>('info')
const confirmOpen = ref(false)

const {running: deleting, error, run: runDelete} = useAsyncAction(async () => {
  await transfer.deleteMovedStation()
  window.location.href = '/'
}, {formatError: () => t('pages.station-moved.deleteError')})

async function performDelete() {
  await runDelete()
  if (error.value) confirmOpen.value = false
}
</script>

<template>
  <div class="max-w-2xl mx-auto">
    <TabBar v-if="canDelete" v-model="activeTab" :tabs="tabs as Array<{key:string,label:string}>" class="mb-6"/>

    <section v-if="activeTab === 'info'" class="flex flex-col items-center gap-6 py-8 text-center">
      <font-awesome-icon :icon="['fas', 'map-location-dot']" class="h-16 w-16 text-accent"/>
      <PageHeader>{{ t('pages.station-moved.headline') }}</PageHeader>
      <p class="text-text/80">{{ t('pages.station-moved.body') }}</p>
      <a :href="props.targetInstanceUrl" rel="noopener" target="_blank" class="contents">
        <PrimaryButton>
          <font-awesome-icon :icon="['fas', 'right-from-bracket']" class="h-4 w-4 mr-2"/>
          {{ t('pages.station-moved.action') }}
        </PrimaryButton>
      </a>
      <p class="text-text/60 break-all">{{ props.targetInstanceUrl }}</p>
      <InfoContainer class="mt-4 text-left">
        {{ t('pages.station-moved.hint') }}
      </InfoContainer>
    </section>

    <section v-else class="flex flex-col gap-6 py-8">
      <SectionHeader>{{ t('pages.station-moved.deleteHeadline') }}</SectionHeader>
      <p class="text-text/80">{{ t('pages.station-moved.deleteBody') }}</p>
      <ErrorContainer>
        <strong>{{ t('pages.station-moved.deleteWarning') }}</strong>
      </ErrorContainer>
      <ErrorContainer v-if="error">{{ error }}</ErrorContainer>
      <div>
        <DeleteButton @click="confirmOpen = true">
          {{ t('pages.station-moved.deleteConfirm') }}
        </DeleteButton>
      </div>
    </section>

    <Modal v-model="confirmOpen" size="md">
      <SectionHeader>{{ t('pages.station-moved.deleteModalTitle') }}</SectionHeader>
      <p class="mt-4 text-text/80">{{ t('pages.station-moved.deleteModalBody') }}</p>
      <div class="mt-6 flex justify-end gap-2">
        <SecondaryButton :disabled="deleting" @click="confirmOpen = false">
          {{ t('pages.station-moved.deleteModalCancel') }}
        </SecondaryButton>
        <DeleteButton :disabled="deleting" @click="performDelete">
          {{ t('pages.station-moved.deleteModalConfirm') }}
        </DeleteButton>
      </div>
    </Modal>
  </div>
</template>
