/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import ViewContent from '@/components/layout/ViewContent.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type { StationMember } from '@/api/types'
import { stationMembers } from '@/api'
import Th from '@/components/table/Th.vue'
import Td from '@/components/table/Td.vue'
import THead from '@/components/table/THead.vue'
import TRow from '@/components/table/TRow.vue'

const { t } = useI18n()

const members = ref<StationMember[]>([])
const loading = ref(true)
const error = ref('')
const success = ref('')

const showReactivateModal = ref(false)
const reactivateTarget = ref<StationMember | null>(null)
const reactivating = ref(false)

function memberDisplayName(m: StationMember): string {
  return m.name && m.name.trim() ? m.name : m.email ?? `#${m.id}`
}

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    members.value = await stationMembers.listFormerMembers()
  } catch {
    error.value = t('common.error')
  } finally {
    loading.value = false
  }
}

function openReactivate(member: StationMember) {
  reactivateTarget.value = member
  showReactivateModal.value = true
}

async function confirmReactivate() {
  if (!reactivateTarget.value) return
  reactivating.value = true
  error.value = ''
  try {
    await stationMembers.reactivateMember(reactivateTarget.value.id)
    showReactivateModal.value = false
    success.value = t('formerMembers.reactivated')
    await loadData()
  } catch {
    error.value = t('common.error')
  } finally {
    reactivating.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <SectionHeader>{{ t('formerMembers.title') }}</SectionHeader>

      <Spinner v-if="loading" size="lg" />
      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Alert v-if="success" variant="success">{{ success }}</Alert>

      <template v-if="!loading">
        <EmptyState v-if="members.length === 0">{{ t('formerMembers.empty') }}</EmptyState>

        <NeutralContainer v-if="members.length > 0" class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <THead>
                <Th>{{ t('membersList.colName') }}</Th>
                <Th>{{ t('membersList.colEmail') }}</Th>
                <th class="px-3 py-2"></th>
              </THead>
            </thead>
            <tbody>
              <TRow v-for="member in members" :key="member.id">
                <Td class="font-medium text-(--text-muted)">{{ memberDisplayName(member) }}</Td>
                <Td muted>{{ member.email ?? '' }}</Td>
                <Td align="right">
                  <PrimaryButton :icon="['fas', 'user-check']" @click="openReactivate(member)">
                    {{ t('formerMembers.reactivate') }}
                  </PrimaryButton>
                </Td>
              </TRow>
            </tbody>
          </table>
        </NeutralContainer>

        <p v-if="members.length > 0" class="text-xs text-(--text-muted)">
          {{ members.length }} {{ t('formerMembers.count') }}
        </p>
      </template>

      <Modal v-model="showReactivateModal">
        <div class="space-y-4">
          <SectionHeader>{{ t('formerMembers.reactivateTitle') }}</SectionHeader>
          <p class="text-sm">
            {{ t('formerMembers.reactivateConfirm', { name: reactivateTarget ? memberDisplayName(reactivateTarget) : '' }) }}
          </p>
          <div class="flex justify-end gap-2">
            <SecondaryButton @click="showReactivateModal = false">{{ t('common.cancel') }}</SecondaryButton>
            <PrimaryButton :disabled="reactivating" @click="confirmReactivate">
              {{ reactivating ? t('common.loading') : t('formerMembers.reactivate') }}
            </PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
