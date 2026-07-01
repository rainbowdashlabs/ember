/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { protocol, federation } from '@/api'
import { getItem } from '@/api/storage'
import type { TestProtocol, TestProtocolSection, TestProtocolItem } from '@/api/protocol'
import MutedText from '@/components/typography/MutedText.vue'
import { StationPermission } from '@/api/types'
import ProtocolSectionCard from './protocoldetailview/ProtocolSectionCard.vue'
import ProtocolSectionModal from './protocoldetailview/ProtocolSectionModal.vue'
import ProtocolItemModal from './protocoldetailview/ProtocolItemModal.vue'
import ProtocolEditModal from './protocoldetailview/ProtocolEditModal.vue'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { hasPermission, loaded } = useSession()

const isFederated = computed(() => {
  if (!proto.value) return false
  const currentStationId = getItem('station_id')
  return currentStationId != null && String(proto.value.stationId) !== currentStationId
})

const canEdit = computed(() => hasPermission(StationPermission.PROTOCOL_CONFIGURE) && !isFederated.value)

const protocolId = computed(() => Number(route.params.id))
const proto = ref<TestProtocol | null>(null)
const sections = ref<TestProtocolSection[]>([])
const items = ref<TestProtocolItem[]>([])

const showSectionModal = ref(false)
const editSectionId = ref<number | null>(null)
const sectionName = ref('')
const sectionDescription = ref('')
const sectionParentId = ref<number | null>(null)
const sectionMaxPoints = ref<number | undefined>(undefined)
const sectionPassThreshold = ref<number | undefined>(undefined)

const showItemModal = ref(false)
const editItemId = ref<number | null>(null)
const itemSectionId = ref(0)
const itemLabel = ref('')
const itemDescription = ref('')
const itemPoints = ref(1)

const {loading, error, reload: loadData} = useAsyncLoader(async () => {
  const data = await protocol.getProtocol(protocolId.value)
  proto.value = data.protocol
  sections.value = data.sections
  items.value = data.items
}, {autoLoad: false})

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
</script>

<template>
  <ViewContent
      :title="t('pages.protocol-detail.title')"
      :subtitle="t('pages.protocol-detail.subtitle')"
  >
    <div class="flex items-center gap-2 mb-4">
      <SecondaryButton @click="router.push({ name: 'protocol-list' })">
        <font-awesome-icon :icon="['fas', 'chevron-left']" />
      </SecondaryButton>
      <SectionHeader>{{ proto?.name ?? '' }}</SectionHeader>
      <StationBadge v-if="isFederated" :station-name="''" />
      <EditButton v-if="canEdit" :label="t('common.edit')" @click="openEditProtocol" />
      <PrimaryButton v-if="isFederated && canEdit" @click="copyToStation">
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
      <MutedText v-if="proto.description" tag="p" size="sm">{{ proto.description }}</MutedText>

      <div class="space-y-4">
        <ProtocolSectionCard
          v-for="section in topSections()"
          :key="section.id"
          :section="section"
          :child-sections="childSections(section.id)"
          :section-items="sectionItems"
          :section-total-points="sectionTotalPoints"
          :can-edit="canEdit"
          @add-item="openAddItem"
          @add-subsection="openAddSection"
          @edit-section="openEditSection"
          @delete-section="handleDeleteSection"
          @edit-item="openEditItem"
          @delete-item="handleDeleteItem"
        />
      </div>

      <PrimaryButton v-if="canEdit" class="mt-4" @click="openAddSection()">
        <font-awesome-icon :icon="['fas', 'plus']" class="mr-1" /> {{ t('protocol.addSection') }}
      </PrimaryButton>
    </template>

    <ProtocolSectionModal
      v-model:visible="showSectionModal"
      v-model:name="sectionName"
      v-model:description="sectionDescription"
      v-model:max-points="sectionMaxPoints"
      v-model:pass-threshold="sectionPassThreshold"
      :editing="editSectionId !== null"
      @submit="handleSaveSection"
    />

    <ProtocolItemModal
      v-model:visible="showItemModal"
      v-model:label="itemLabel"
      v-model:description="itemDescription"
      v-model:points="itemPoints"
      :editing="editItemId !== null"
      @submit="handleSaveItem"
    />

    <ProtocolEditModal
      v-model:visible="showEditProtocolModal"
      v-model:name="editProtoName"
      v-model:description="editProtoDescription"
      v-model:pass-threshold="editProtoPassThreshold"
      @submit="handleSaveProtocol"
    />
  </ViewContent>
</template>
