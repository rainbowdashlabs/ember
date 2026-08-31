/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import MutedText from '@/components/typography/MutedText.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import SaveButton from '@/components/button/SaveButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import ColorBadge from '@/components/badge/ColorBadge.vue'
import Modal from '@/components/feedback/Modal.vue'
import Alert from '@/components/feedback/Alert.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import ConfirmDeleteModal from '@/components/feedback/ConfirmDeleteModal.vue'
import {clusterInventory, clusterStationGroups} from '@/api'
import type {ClusterInventoryTag} from '@/api/clusterInventory'
import type {StationGroup} from '@/api/clusterStationGroups'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {apiErrorMessage} from '@/util/apiError'

/**
 * The words the association recommends to its stations.
 *
 * A recommendation is an offer and a shared vocabulary, never an instruction: a station that already
 * uses the word keeps its own row and its own spelling, and one that does not is not made to take it
 * up. Withdrawing a recommendation therefore takes nothing away from anybody.
 */
const {t} = useI18n()

const tags = ref<ClusterInventoryTag[]>([])
const groups = ref<StationGroup[]>([])
const saveError = ref('')

const showModal = ref(false)
const editing = ref<ClusterInventoryTag | null>(null)
const name = ref('')
const color = ref('')
const stationGroupId = ref<number | null>(null)

const groupNames = computed(() => new Map(groups.value.map(group => [group.id, group.name])))

const {loading, error, reload} = useAsyncLoader(async () => {
  const [recommended, allGroups] = await Promise.all([
    clusterInventory.listTags(),
    clusterStationGroups.listGroups(),
  ])
  tags.value = recommended
  groups.value = allGroups
})

function openAdd() {
  editing.value = null
  name.value = ''
  color.value = ''
  stationGroupId.value = null
  showModal.value = true
}

function openEdit(tag: ClusterInventoryTag) {
  editing.value = tag
  name.value = tag.name
  color.value = tag.color ?? ''
  stationGroupId.value = tag.stationGroupId ?? null
  showModal.value = true
}

async function save() {
  saveError.value = ''
  const body = {
    name: name.value,
    color: color.value || null,
    position: editing.value?.position ?? tags.value.length,
    stationGroupId: stationGroupId.value,
  }
  try {
    if (editing.value) {
      await clusterInventory.updateTag(editing.value.id, body)
    } else {
      await clusterInventory.createTag(body)
    }
    showModal.value = false
    await reload()
  } catch (e) {
    saveError.value = apiErrorMessage(e) ?? t('common.error')
    throw e
  }
}

const {show: showDeleteModal, target: deleteTarget, requestDelete, confirm: confirmDelete} =
    useConfirmDelete<ClusterInventoryTag>({onDelete: tag => clusterInventory.deleteTag(tag.id), onSuccess: reload, error})
</script>

<template>
  <NeutralContainer class="space-y-4" data-testid="cluster-inventory-tags">
    <div class="flex items-center justify-between">
      <SectionHeader>{{ t('clusterInventory.tags.title') }}</SectionHeader>
      <SecondaryButton :icon="['fas', 'plus']" data-testid="add-cluster-tag" @click="openAdd">
        {{ t('clusterInventory.tags.add') }}
      </SecondaryButton>
    </div>
    <MutedText size="sm">{{ t('clusterInventory.tags.intro') }}</MutedText>
    <MutedText size="sm">{{ t('clusterInventory.tags.standsBeside') }}</MutedText>

    <Spinner v-if="loading" size="sm"/>
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <div
        v-for="tag in tags"
        :key="tag.id"
        class="flex items-center justify-between px-3 py-2 border-b border-bg-light-accent/50 dark:border-bg-dark-accent/50"
        data-testid="cluster-tag-row"
    >
      <ColorBadge :color="tag.color ?? undefined">{{ tag.name }}</ColorBadge>
      <div class="flex items-center gap-3">
        <MutedText size="sm">
          {{ tag.stationGroupId ? groupNames.get(tag.stationGroupId) : t('clusterInventory.tags.allStations') }}
        </MutedText>
        <EditButton @click="openEdit(tag)"/>
        <DeleteButton @click="requestDelete(tag)"/>
      </div>
    </div>

    <div v-if="!loading && tags.length === 0" class="text-center text-(--text-muted) py-4 text-sm">
      {{ t('clusterInventory.tags.empty') }}
    </div>
  </NeutralContainer>

  <Modal v-model="showModal">
    <div class="space-y-4">
      <SectionHeader>{{ editing ? t('clusterInventory.tags.edit') : t('clusterInventory.tags.add') }}</SectionHeader>
      <Alert v-if="saveError" variant="error">{{ saveError }}</Alert>
      <div class="space-y-1">
        <FieldLabel>{{ t('clusterInventory.tags.name') }}</FieldLabel>
        <TextInput v-model="name" data-testid="cluster-tag-name" :placeholder="t('clusterInventory.tags.namePlaceholder')"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('clusterInventory.tags.color') }}</FieldLabel>
        <ColorInput v-model="color"/>
      </div>
      <div class="space-y-1">
        <FieldLabel>{{ t('clusterInventory.tags.forStations') }}</FieldLabel>
        <SelectInput v-model="stationGroupId">
          <option :value="null">{{ t('clusterInventory.tags.allStations') }}</option>
          <option v-for="group in groups" :key="group.id" :value="group.id">{{ group.name }}</option>
        </SelectInput>
      </div>
      <div class="flex justify-end gap-3">
        <SecondaryButton @click="showModal = false">{{ t('common.cancel') }}</SecondaryButton>
        <SaveButton :disabled="!name.trim()" :action="save"/>
      </div>
    </div>
  </Modal>

  <ConfirmDeleteModal
      v-model="showDeleteModal"
      :message="t('clusterInventory.tags.deleteConfirm', {name: deleteTarget?.name})"
      @confirm="confirmDelete"
  />
</template>
