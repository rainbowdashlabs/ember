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
import SectionHeader from '@/components/typography/SectionHeader.vue'
import Modal from '@/components/feedback/Modal.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type { StationMember } from '@/api/types'
import { stationMembers } from '@/api'

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
        <div v-if="members.length === 0" class="text-center text-(--text-muted) py-8">
          {{ t('formerMembers.empty') }}
        </div>

        <NeutralContainer v-if="members.length > 0" class="overflow-x-auto">
          <table class="w-full text-sm">
            <thead>
              <tr class="border-b border-bg-light-accent dark:border-bg-dark-accent text-left">
                <th class="px-3 py-2 font-medium">{{ t('membersList.colName') }}</th>
                <th class="px-3 py-2 font-medium">{{ t('membersList.colEmail') }}</th>
                <th class="px-3 py-2"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in members" :key="member.id"
                  class="border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50">
                <td class="px-3 py-2.5 font-medium text-(--text-muted)">{{ memberDisplayName(member) }}</td>
                <td class="px-3 py-2.5 text-(--text-muted)">{{ member.email ?? '' }}</td>
                <td class="px-3 py-2.5 text-right">
                  <PrimaryButton class="text-xs" @click="openReactivate(member)">
                    <font-awesome-icon :icon="['fas', 'user-check']" class="mr-1" />
                    {{ t('formerMembers.reactivate') }}
                  </PrimaryButton>
                </td>
              </tr>
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
