/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref, watch} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import EmptyState from '@/components/feedback/EmptyState.vue'
import MutedText from '@/components/typography/MutedText.vue'
import type {EventTemplate} from '@/api/types'
import {events} from '@/api'
import {useSession} from '@/composables/useSession'

const {t} = useI18n()
const router = useRouter()
const {loaded} = useSession()

const templates = ref<EventTemplate[]>([])
const loading = ref(true)
const error = ref('')

const createOpen = ref(false)
const createName = ref('')

onMounted(() => { if (loaded.value) loadData() })
watch(loaded, (v) => { if (v && loading.value) loadData() })

async function loadData() {
  loading.value = true
  try {
    templates.value = await events.listTemplates()
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

async function createTemplate() {
  if (!createName.value.trim()) return
  try {
    const tpl = await events.createTemplate({name: createName.value.trim()})
    createOpen.value = false
    createName.value = ''
    router.push({name: 'event-template-edit', params: {id: tpl.id}})
  } catch { error.value = t('common.error') }
}

async function deleteTemplate(id: number) {
  try {
    await events.deleteTemplate(id)
    await loadData()
  } catch { error.value = t('common.error') }
}
</script>

<template>
  <ViewContent>
    <div class="space-y-6">
      <div class="flex items-center justify-between">
        <SectionHeader>{{ t('eventTemplates.title') }}</SectionHeader>
        <PrimaryButton :icon="['fas', 'plus']" @click="createOpen = true">{{ t('eventTemplates.create') }}</PrimaryButton>
      </div>

      <Alert v-if="error" variant="error">{{ error }}</Alert>
      <Spinner v-if="loading" />

      <EmptyState v-if="!loading && templates.length === 0">{{ t('eventTemplates.empty') }}</EmptyState>

      <div v-if="!loading" class="space-y-2">
        <NeutralContainer v-for="tpl in templates" :key="tpl.id" class="flex items-center justify-between">
          <div>
            <span class="font-medium">{{ tpl.name }}</span>
            <MutedText v-if="tpl.title" size="sm" class="ml-2">{{ tpl.title }}</MutedText>
          </div>
          <div class="flex items-center gap-2">
            <EditButton @click="router.push({name: 'event-template-edit', params: {id: tpl.id}})"/>
            <DeleteButton @click="deleteTemplate(tpl.id)"/>
          </div>
        </NeutralContainer>
      </div>

      <!-- Create modal -->
      <Modal v-model="createOpen">
        <div class="space-y-4">
          <SubHeader>{{ t('eventTemplates.create') }}</SubHeader>
          <FieldLabel>{{ t('eventTemplates.name') }}</FieldLabel>
          <TextInput v-model="createName" :placeholder="t('eventTemplates.namePlaceholder')"/>
          <div class="flex justify-end">
            <PrimaryButton :disabled="!createName.trim()" @click="createTemplate">{{ t('eventTemplates.create') }}</PrimaryButton>
          </div>
        </div>
      </Modal>
    </div>
  </ViewContent>
</template>
