/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import ErrorButton from '@/components/button/ErrorButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import Modal from '@/components/feedback/Modal.vue'
import PagesList from './PagesList.vue'
import CreatePageModal from './CreatePageModal.vue'
import type {StationPage} from '@/api/pageManage'

interface FlatPageEntry {
  page: StationPage
  depth: number
}

const showCreateModal = defineModel<boolean>('showCreateModal', {required: true})
const newTitle = defineModel<string>('newTitle', {required: true})
const newParentId = defineModel<string>('newParentId', {required: true})
const showDeleteModal = defineModel<boolean>('showDeleteModal', {required: true})

const props = defineProps<{
  canEdit: boolean
  canManage: boolean
  loading: boolean
  error: string
  flatPages: FlatPageEntry[]
  landingPageId: number | null
  topLevelPages: StationPage[]
  deleteTarget: StationPage | null
}>()

const emit = defineEmits<{
  (e: 'open-create'): void
  (e: 'confirm-create'): void
  (e: 'reorder', fromIndex: number, toIndex: number): void
  (e: 'edit', page: StationPage): void
  (e: 'duplicate', page: StationPage): void
  (e: 'toggle-publish', page: StationPage): void
  (e: 'set-landing', page: StationPage): void
  (e: 'request-delete', page: StationPage): void
  (e: 'confirm-delete'): void
}>()

const {t} = useI18n()
</script>

<template>
  <div class="space-y-6">
    <div class="flex items-center justify-between flex-wrap gap-2">
      <SectionHeader>{{ t('stationPages.title') }}</SectionHeader>
      <PrimaryButton v-if="props.canEdit" @click="emit('open-create')">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1"/>
        {{ t('stationPages.createPage') }}
      </PrimaryButton>
    </div>

    <Spinner v-if="props.loading" size="lg"/>
    <Alert v-if="props.error" variant="error">{{ props.error }}</Alert>

    <EmptyState v-if="!props.loading && props.flatPages.length === 0">
      {{ t('stationPages.empty') }}
    </EmptyState>

    <PagesList
        v-if="!props.loading && props.flatPages.length > 0"
        :flat-pages="props.flatPages"
        :can-edit="props.canEdit"
        :can-manage="props.canManage"
        :landing-page-id="props.landingPageId"
        @reorder="(from: number, to: number) => emit('reorder', from, to)"
        @edit="(p: StationPage) => emit('edit', p)"
        @duplicate="(p: StationPage) => emit('duplicate', p)"
        @toggle-publish="(p: StationPage) => emit('toggle-publish', p)"
        @set-landing="(p: StationPage) => emit('set-landing', p)"
        @delete="(p: StationPage) => emit('request-delete', p)"
    />

    <CreatePageModal
        v-model="showCreateModal"
        v-model:title="newTitle"
        v-model:parent-id="newParentId"
        :top-level-pages="props.topLevelPages"
        @confirm="emit('confirm-create')"
    />

    <Modal v-model="showDeleteModal">
      <div class="space-y-4">
        <p>{{ t('stationPages.deleteConfirm', {title: props.deleteTarget?.title}) }}</p>
        <div class="flex justify-end gap-3">
          <SecondaryButton @click="showDeleteModal = false">{{ t('common.cancel') }}</SecondaryButton>
          <ErrorButton @click="emit('confirm-delete')">{{ t('common.delete') }}</ErrorButton>
        </div>
      </div>
    </Modal>
  </div>
</template>
