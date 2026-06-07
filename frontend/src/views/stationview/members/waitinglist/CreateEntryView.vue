/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import { waitingList } from '@/api'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const listId = computed(() => Number(route.params.id))

const firstname = ref('')
const lastname = ref('')
const guardians = ref<{ name: string; email: string; phone: string }[]>([{ name: '', email: '', phone: '' }])
const notes = ref('')
const saving = ref(false)
const error = ref('')

function addGuardian() {
  guardians.value = [...guardians.value, { name: '', email: '', phone: '' }]
}

function removeGuardian(index: number) {
  guardians.value = guardians.value.filter((_, i) => i !== index)
}

const canSave = computed(() =>
  firstname.value.trim() && guardians.value.some(g => g.email.trim()),
)

async function save() {
  if (!canSave.value) return
  saving.value = true
  error.value = ''
  try {
    await waitingList.createEntry(listId.value, {
      firstname: firstname.value.trim(),
      lastname: lastname.value.trim(),
      guardians: guardians.value.map(g => ({ name: g.name.trim(), email: g.email.trim(), phone: g.phone.trim() })),
      notes: notes.value.trim(),
    })
    router.push({ name: 'waiting-list-detail', params: { id: listId.value } })
  } catch {
    error.value = t('common.error')
  } finally {
    saving.value = false
  }
}

function goBack() {
  router.push({ name: 'waiting-list-detail', params: { id: listId.value } })
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SecondaryButton :icon="['fas', 'chevron-left']" @click="goBack">
          {{ t('waitingList.backToList') }}
        </SecondaryButton>
      </div>

      <SectionHeader>{{ t('waitingList.addEntry') }}</SectionHeader>

      <Alert v-if="error" variant="error">{{ error }}</Alert>

      <NeutralContainer class="space-y-4">
        <div class="grid gap-4 sm:grid-cols-2">
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.firstname') }} <span class="text-error">*</span></FieldLabel>
            <TextInput v-model="firstname" :placeholder="t('waitingList.firstnamePlaceholder')" />
          </div>
          <div class="space-y-1">
            <FieldLabel>{{ t('waitingList.lastname') }}</FieldLabel>
            <TextInput v-model="lastname" :placeholder="t('waitingList.lastnamePlaceholder')" />
          </div>
        </div>

        <div class="space-y-3">
          <FieldLabel>{{ t('waitingList.guardians') }} <span class="text-error">*</span></FieldLabel>
          <NeutralContainer v-for="(g, i) in guardians" :key="i" class="space-y-2">
            <div class="flex items-center justify-between">
              <span class="text-sm font-medium">{{ t('waitingList.guardian') }} {{ i + 1 }}</span>
              <DeleteButton v-if="guardians.length > 1" @click="removeGuardian(i)" />
            </div>
            <div class="grid gap-2 sm:grid-cols-3">
              <TextInput v-model="g.name" :placeholder="t('waitingList.guardianNamePlaceholder')" />
              <TextInput v-model="g.email" :placeholder="t('waitingList.guardianEmailPlaceholder')" />
              <TextInput v-model="g.phone" :placeholder="t('waitingList.guardianPhonePlaceholder')" />
            </div>
          </NeutralContainer>
          <SecondaryButton :icon="['fas', 'plus']" @click="addGuardian">{{ t('waitingList.addGuardian') }}</SecondaryButton>
        </div>

        <div class="space-y-1">
          <FieldLabel>{{ t('waitingList.notes') }}</FieldLabel>
          <TextAreaInput v-model="notes" :placeholder="t('waitingList.notesPlaceholder')" />
        </div>
      </NeutralContainer>

      <div class="flex justify-end gap-2">
        <SecondaryButton @click="goBack">{{ t('common.cancel') }}</SecondaryButton>
        <PrimaryButton :disabled="saving || !canSave" @click="save">
          {{ saving ? t('common.loading') : t('waitingList.addEntry') }}
        </PrimaryButton>
      </div>
    </div>
  </ViewContent>
</template>
