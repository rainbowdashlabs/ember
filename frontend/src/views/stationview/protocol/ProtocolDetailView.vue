/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import Modal from '@/components/feedback/Modal.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import DecimalInput from '@/components/input/number/DecimalInput.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import { useSession } from '@/composables/useSession'
import { protocol, federation } from '@/api'
import { getItem } from '@/api/storage'
import type { TestProtocol, TestProtocolSection, TestProtocolItem } from '@/api/protocol'
import MutedText from '@/components/typography/MutedText.vue'
import MutedIcon from '@/components/display/MutedIcon.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { canManageProtocol, loaded } = useSession()

const isFederated = computed(() => {
  if (!proto.value) return false
  const currentStationId = getItem('station_id')
  return currentStationId != null && String(proto.value.stationId) !== currentStationId
})

const canEdit = computed(() => canManageProtocol() && !isFederated.value)

const protocolId = computed(() => Number(route.params.id))
const proto = ref<TestProtocol | null>(null)
const sections = ref<TestProtocolSection[]>([])
const items = ref<TestProtocolItem[]>([])
const loading = ref(true)
const error = ref('')

// Section modal
const showSectionModal = ref(false)
const editSectionId = ref<number | null>(null)
const sectionName = ref('')
const sectionDescription = ref('')
const sectionParentId = ref<number | null>(null)
const sectionMaxPoints = ref<number | undefined>(undefined)
const sectionPassThreshold = ref<number | undefined>(undefined)

// Item modal
const showItemModal = ref(false)
const editItemId = ref<number | null>(null)
const itemSectionId = ref(0)
const itemLabel = ref('')
const itemDescription = ref('')
const itemPoints = ref(1)

async function loadData() {
  loading.value = true
  try {
    const data = await protocol.getProtocol(protocolId.value)
    proto.value = data.protocol
    sections.value = data.sections
    items.value = data.items
  } catch { error.value = t('common.error') }
  finally { loading.value = false }
}

async function copyToStation() {
  if (!proto.value) return
  try {
    await federation.copyProtocol(proto.value.id)
    router.push({ name: 'protocol-list' })
  } catch { error.value = t('common.error') }
}

function topSections() { return sections.value.filter(s => !s.parentId).sort((a, b) => a.position - b.position) }
function childSections(parentId: number) { return sections.value.filter(s => s.parentId === parentId).sort((a, b) => a.position - b.position) }
function sectionItems(sectionId: number) { return items.value.filter(i => i.sectionId === sectionId).sort((a, b) => a.position - b.position) }

function sectionTotalPoints(sectionId: number): number {
  let total = sectionItems(sectionId).reduce((sum, i) => sum + i.points, 0)
  for (const child of childSections(sectionId)) {
    total += sectionTotalPoints(child.id)
  }
  return total
}

const totalProtocolPoints = computed(() => {
  return topSections().reduce((sum, s) => sum + sectionTotalPoints(s.id), 0)
})

function openAddSection(parentId: number | null = null) {
  editSectionId.value = null
  sectionName.value = ''
  sectionDescription.value = ''
  sectionParentId.value = parentId
  sectionMaxPoints.value = undefined
  sectionPassThreshold.value = undefined
  showSectionModal.value = true
}

function openEditSection(s: TestProtocolSection) {
  editSectionId.value = s.id
  sectionName.value = s.name
  sectionDescription.value = s.description
  sectionParentId.value = s.parentId
  sectionMaxPoints.value = s.maxPoints ?? undefined
  sectionPassThreshold.value = s.passThreshold ?? undefined
  showSectionModal.value = true
}

async function handleSaveSection() {
  if (!sectionName.value.trim()) return
  try {
    if (editSectionId.value) {
      await protocol.updateSection(editSectionId.value, {
        name: sectionName.value.trim(),
        description: sectionDescription.value,
        maxPoints: sectionMaxPoints.value,
        passThreshold: sectionPassThreshold.value,
        position: sections.value.find(s => s.id === editSectionId.value)?.position ?? 0,
      })
    } else {
      await protocol.createSection(protocolId.value, {
        parentId: sectionParentId.value,
        name: sectionName.value.trim(),
        description: sectionDescription.value,
        maxPoints: sectionMaxPoints.value,
        passThreshold: sectionPassThreshold.value,
        position: sections.value.length,
      })
    }
    showSectionModal.value = false
    await loadData()
  } catch { error.value = t('common.error') }
}

async function handleDeleteSection(id: number) {
  try { await protocol.deleteSection(id); await loadData() }
  catch { error.value = t('common.error') }
}

function openAddItem(sectionId: number) {
  editItemId.value = null
  itemSectionId.value = sectionId
  itemLabel.value = ''
  itemDescription.value = ''
  itemPoints.value = 1
  showItemModal.value = true
}

function openEditItem(item: TestProtocolItem) {
  editItemId.value = item.id
  itemSectionId.value = item.sectionId
  itemLabel.value = item.label
  itemDescription.value = item.description
  itemPoints.value = item.points
  showItemModal.value = true
}

async function handleSaveItem() {
  if (!itemLabel.value.trim()) return
  try {
    if (editItemId.value) {
      await protocol.updateItem(editItemId.value, {
        label: itemLabel.value.trim(),
        description: itemDescription.value,
        points: itemPoints.value,
        position: items.value.find(i => i.id === editItemId.value)?.position ?? 0,
      })
    } else {
      await protocol.createItem(itemSectionId.value, {
        label: itemLabel.value.trim(),
        description: itemDescription.value,
        points: itemPoints.value,
        position: sectionItems(itemSectionId.value).length,
      })
    }
    showItemModal.value = false
    await loadData()
  } catch { error.value = t('common.error') }
}

async function handleDeleteItem(id: number) {
  try { await protocol.deleteItem(id); await loadData() }
  catch { error.value = t('common.error') }
}

// Protocol edit modal
const showEditProtocolModal = ref(false)
const editProtoName = ref('')
const editProtoDescription = ref('')
const editProtoPassThreshold = ref<number | undefined>(undefined)

function openEditProtocol() {
  if (!proto.value) return
  editProtoName.value = proto.value.name
  editProtoDescription.value = proto.value.description
  editProtoPassThreshold.value = proto.value.passThreshold ?? undefined
  showEditProtocolModal.value = true
}

async function handleSaveProtocol() {
  if (!proto.value || !editProtoName.value.trim()) return
  try {
    await protocol.updateProtocol(proto.value.id, {
      name: editProtoName.value.trim(),
      description: editProtoDescription.value,
      passThreshold: editProtoPassThreshold.value ?? null,
    })
    showEditProtocolModal.value = false
    await loadData()
  } catch { error.value = t('common.error') }
}

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
onMounted(() => { if (loaded.value) loadData() })
</script>

<template>
  <ViewContent>
    <div class="flex items-center gap-2 mb-4">
      <SecondaryButton @click="router.push({ name: 'protocol-list' })">
        <font-awesome-icon :icon="['fas', 'chevron-left']" />
      </SecondaryButton>
      <SectionHeader>{{ proto?.name ?? '' }}</SectionHeader>
      <StationBadge v-if="isFederated" :station-name="''" />
      <EditButton v-if="canEdit" :label="t('common.edit')" @click="openEditProtocol" />
      <PrimaryButton v-if="isFederated && canManageProtocol()" @click="copyToStation">
        <font-awesome-icon :icon="['fas', 'copy']" class="mr-1" /> {{ t('federation.copyToStation') }}
      </PrimaryButton>
      <span class="text-sm text-[var(--text-muted)] ml-auto">
        <template v-if="proto?.passThreshold">{{ t('protocol.threshold') }}: {{ proto.passThreshold }}P / </template>
        {{ totalProtocolPoints }}P {{ t('protocol.total') }}
      </span>
    </div>

    <Spinner v-if="loading" />
    <Alert v-if="error" variant="error">{{ error }}</Alert>

    <template v-if="!loading && proto">
      <MutedText tag="p" size="sm" v-if="proto.description">{{ proto.description }}</MutedText>

      <!-- Sections -->
      <div class="space-y-4">
        <NeutralContainer v-for="section in topSections()" :key="section.id" class="space-y-2">
          <div class="flex items-center gap-2">
            <SubHeader>{{ section.name }}</SubHeader>
            <MutedText class="ml-auto">{{ sectionTotalPoints(section.id) }}P</MutedText>
            <template v-if="canEdit">
              <IconButton :icon="['fas', 'plus']" :label="t('protocol.addItem')" @click="openAddItem(section.id)" />
              <IconButton :icon="['fas', 'folder-plus']" :label="t('protocol.addSubsection')" @click="openAddSection(section.id)" />
              <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="openEditSection(section)" />
              <DeleteButton :label="t('common.delete')" @click="handleDeleteSection(section.id)" />
            </template>
          </div>

          <!-- Items at section level -->
          <div v-for="item in sectionItems(section.id)" :key="item.id" class="flex items-center gap-2 pl-4 text-xs">
            <MutedIcon :icon="['fas', 'square']" />
            <span class="flex-1">{{ item.label }}</span>
            <span class="text-xs text-[var(--text-muted)]">{{ item.points }}P</span>
            <template v-if="canEdit">
              <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="openEditItem(item)" />
              <DeleteButton :label="t('common.delete')" @click="handleDeleteItem(item.id)" />
            </template>
          </div>

          <!-- Subsections -->
          <div v-for="sub in childSections(section.id)" :key="sub.id" class="ml-4 border-l-2 border-[var(--border)] pl-3 space-y-1">
            <div class="flex items-center gap-2">
              <span class="font-medium text-sm">{{ sub.name }}</span>
              <MutedText class="ml-auto">{{ sectionTotalPoints(sub.id) }}P</MutedText>
              <template v-if="canEdit">
                <IconButton :icon="['fas', 'plus']" :label="t('protocol.addItem')" @click="openAddItem(sub.id)" />
                <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="openEditSection(sub)" />
                <DeleteButton :label="t('common.delete')" @click="handleDeleteSection(sub.id)" />
              </template>
            </div>
            <div v-for="item in sectionItems(sub.id)" :key="item.id" class="flex items-center gap-2 pl-4 text-xs">
              <MutedIcon :icon="['fas', 'square']" />
              <span class="flex-1">{{ item.label }}</span>
              <span class="text-xs text-[var(--text-muted)]">{{ item.points }}P</span>
              <template v-if="canEdit">
                <IconButton :icon="['fas', 'pen']" :label="t('common.edit')" @click="openEditItem(item)" />
                <DeleteButton :label="t('common.delete')" @click="handleDeleteItem(item.id)" />
              </template>
            </div>
          </div>
        </NeutralContainer>
      </div>

      <PrimaryButton v-if="canEdit" class="mt-4" @click="openAddSection()">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('protocol.addSection') }}
      </PrimaryButton>
    </template>

    <!-- Section Modal -->
    <Modal v-model="showSectionModal">
      <SubHeader class="mb-3">{{ editSectionId ? t('protocol.editSection') : t('protocol.addSection') }}</SubHeader>
      <form @submit.prevent="handleSaveSection" class="space-y-3">
        <TextInput v-model="sectionName" :placeholder="t('protocol.sectionName')" required />
        <TextAreaInput v-model="sectionDescription" :placeholder="t('protocol.description')" />
        <div>
          <label class="block text-sm mb-1">{{ t('protocol.maxPoints') }}</label>
          <NumberInput v-model="sectionMaxPoints" />
        </div>
        <div>
          <label class="block text-sm mb-1">{{ t('protocol.passThreshold') }}</label>
          <NumberInput v-model="sectionPassThreshold" />
        </div>
        <div class="flex gap-2 justify-end">
          <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
        </div>
      </form>
    </Modal>

    <!-- Item Modal -->
    <Modal v-model="showItemModal">
      <SubHeader class="mb-3">{{ editItemId ? t('protocol.editItem') : t('protocol.addItem') }}</SubHeader>
      <form @submit.prevent="handleSaveItem" class="space-y-3">
        <TextInput v-model="itemLabel" :placeholder="t('protocol.itemLabel')" required />
        <TextAreaInput v-model="itemDescription" :placeholder="t('protocol.description')" />
        <div>
          <label class="block text-sm mb-1">{{ t('protocol.points') }}</label>
          <DecimalInput v-model="itemPoints" step="0.5" />
        </div>
        <div class="flex gap-2 justify-end">
          <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
        </div>
      </form>
    </Modal>

    <!-- Edit Protocol Modal -->
    <Modal v-model="showEditProtocolModal">
      <SubHeader class="mb-3">{{ t('common.edit') }}</SubHeader>
      <form @submit.prevent="handleSaveProtocol" class="space-y-3">
        <TextInput v-model="editProtoName" :placeholder="t('protocol.name')" required />
        <TextAreaInput v-model="editProtoDescription" :placeholder="t('protocol.description')" />
        <div>
          <FieldLabel class="mb-1">{{ t('protocol.passThreshold') }}</FieldLabel>
          <NumberInput v-model="editProtoPassThreshold" />
          <p class="text-xs text-[var(--text-muted)] mt-1">{{ t('protocol.passThresholdHint') }}</p>
        </div>
        <div class="flex gap-2 justify-end">
          <PrimaryButton type="submit">{{ t('common.save') }}</PrimaryButton>
        </div>
      </form>
    </Modal>
  </ViewContent>
</template>
