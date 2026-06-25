/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import SelectionToggleButton from '@/components/button/SelectionToggleButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import InfoBadge from '@/components/badge/InfoBadge.vue'
import { useSession } from '@/composables/useSession'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { procedures } from '@/api'
import { StationPermission } from '@/api/types'
import type { ProcedureTemplate } from '@/api/procedures'

const { t } = useI18n()
const router = useRouter()
const { hasPermission, loaded } = useSession()

const canManage = computed(() => hasPermission(StationPermission.PROCEDURE_MANAGER))

const templates = ref<ProcedureTemplate[]>([])
const showArchived = ref(false)

const showCreateModal = ref(false)
const newName = ref('')
const newDescription = ref('')

const {loading, error, reload} = useAsyncLoader(async () => {
  templates.value = await procedures.getTemplates()
}, {autoLoad: false})

const {
  show: showArchiveModal,
  target: archiveTarget,
  request: requestArchive,
  confirm: handleArchive,
} = useConfirmAction<ProcedureTemplate>({
  onConfirm: tpl => procedures.archiveTemplate(tpl.id),
  onSuccess: () => reload(),
  error,
})

const filteredTemplates = computed(() => {
  if (showArchived.value) return templates.value
  return templates.value.filter(t => !t.archived)
})

async function handleCreate() {
  if (!newName.value.trim()) return
  try {
    const created = await procedures.createTemplate({
      name: newName.value.trim(),
      description: newDescription.value || undefined,
    })
    showCreateModal.value = false
    newName.value = ''
    newDescription.value = ''
    router.push({ name: 'procedure-template-edit', params: { id: created.id } })
  } catch {
    error.value = t('common.error')
  }
}

watch(loaded, (v) => { if (v) reload() }, { immediate: true })
</script>

<template>
  <ViewContent>
    <div class="flex items-center justify-between mb-4">
      <SectionHeader>{{ t('procedures.templateTitle') }}</SectionHeader>
      <PrimaryButton v-if="canManage" @click="showCreateModal = true">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('procedures.createTemplate') }}
      </PrimaryButton>
    </div>

    <div class="flex items-center gap-2 mb-4">
      <SelectionToggleButton :selected="showArchived" @toggle="showArchived = !showArchived">
        {{ t('procedures.showArchived') }}
      </SelectionToggleButton>
    </div>

    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <div v-if="!loading && filteredTemplates.length === 0" class="text-center text-[var(--text-muted)] py-8">
      {{ t('procedures.emptyTemplates') }}
    </div>

    <div class="space-y-2">
      <NeutralContainer
        v-for="tpl in filteredTemplates"
        :key="tpl.id"
        class="flex items-center gap-3 cursor-pointer hover:border-[var(--primary)] transition-colors group"
        :class="{ 'opacity-60': tpl.archived }"
        @click="router.push({ name: 'procedure-template-edit', params: { id: tpl.id } })"
      >
        <div class="flex-1 min-w-0">
          <div class="flex items-center gap-2">
            <span class="font-medium">{{ tpl.name }}</span>
            <InfoBadge v-if="tpl.archived">{{ t('boards.archived') }}</InfoBadge>
          </div>
          <div v-if="tpl.description" class="text-sm text-[var(--text-muted)] truncate">{{ tpl.description }}</div>
        </div>
        <div v-if="canManage" class="flex gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <EditButton :label="t('common.edit')" @click.stop="router.push({ name: 'procedure-template-edit', params: { id: tpl.id } })" />
          <IconButton
            v-if="!tpl.archived"
            :icon="['fas', 'box-archive']"
            :label="t('procedures.archiveTemplate')"
            @click.stop="requestArchive(tpl)"
          />
        </div>
      </NeutralContainer>
    </div>

    <!-- Create Modal -->
    <Modal v-model="showCreateModal">
      <SubHeader class="mb-3">{{ t('procedures.createTemplate') }}</SubHeader>
      <form @submit.prevent="handleCreate" class="space-y-3">
        <TextInput v-model="newName" :placeholder="t('procedures.templateName')" required />
        <TextAreaInput v-model="newDescription" :placeholder="t('procedures.templateDescription')" />
        <div class="flex gap-2 justify-end">
          <PrimaryButton type="submit">{{ t('procedures.createTemplate') }}</PrimaryButton>
        </div>
      </form>
    </Modal>

    <!-- Archive Confirm Modal -->
    <Modal v-model="showArchiveModal">
      <SubHeader class="mb-3">{{ t('procedures.archiveConfirm') }}</SubHeader>
      <p class="mb-4">{{ archiveTarget?.name }}</p>
      <div class="flex gap-2 justify-end">
        <PrimaryButton @click="handleArchive">{{ t('procedures.archiveTemplate') }}</PrimaryButton>
      </div>
    </Modal>
  </ViewContent>
</template>
