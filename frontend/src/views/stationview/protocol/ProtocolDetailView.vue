/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
import { ref, computed, watch, provide } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import PrimaryButton from '@/components/button/PrimaryButton.vue'
import FailureAlert from '@/components/feedback/FailureAlert.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import EditButton from '@/components/button/EditButton.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import StationBadge from '@/components/badge/StationBadge.vue'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import { protocol } from '@/api'
import { describeFailure } from '@/util/failure'
import { getItem } from '@/api/storage'
import { StationPermission, type TestProtocol, type TestProtocolSection, type TestProtocolItem } from '@/api/generated/schema'
import MutedText from '@/components/typography/MutedText.vue'
import DragList from '@/components/input/DragList.vue'
import { moveWithin } from '@/util/reorder'
import ProtocolSectionNode from './protocoldetailview/ProtocolSectionNode.vue'
import ProtocolCutBar from './protocoldetailview/ProtocolCutBar.vue'
import { protocolEditorKey } from './protocoldetailview/protocolEditor'
import { useSectionCut } from './protocoldetailview/useSectionCut'
import { protocolTree } from './protocolTree'
import ProtocolSectionModal from './protocoldetailview/ProtocolSectionModal.vue'
import ProtocolItemModal from './protocoldetailview/ProtocolItemModal.vue'
import ProtocolEditModal from './protocoldetailview/ProtocolEditModal.vue'
import { maxPointsOf } from './protocolPoints'

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
const itemBonus = ref(false)

const {loading, failure, reload: loadData} = useAsyncLoader(async () => {
  const data = await protocol.getProtocol(protocolId.value)
  proto.value = data.protocol
  sections.value = data.sections
  items.value = data.items
}, {autoLoad: false})

/**
 * Writes one change, then reads the protocol back.
 *
 * <p>The two are answered for separately. A section the server had already stored, followed by a
 * page that would not refresh, used to say the section had been refused, and a reader told that
 * adds it a second time.
 *
 * <p>The protocol stays on screen while it is read back. Taking it away for the moment of the reload
 * shortened the page, and the browser scrolled the reader back up after every new entry.
 */
async function writeThenReload(write: () => Promise<void>) {
  failure.value = null
  try {
    await write()
  } catch (e) {
    failure.value = describeFailure(e, t)
    return
  }
  await loadData()
}

const tree = computed(() => protocolTree(sections.value, items.value))

function topSections() { return tree.value.childrenOf(null) }
function sectionItems(sectionId: number) { return tree.value.itemsOf(sectionId) }

const totalProtocolPoints = computed(() => maxPointsOf(items.value))

const sectionCut = useSectionCut(tree, (sectionId, parentId) =>
  writeThenReload(() => protocol.moveSection(sectionId, parentId)))

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
  await writeThenReload(async () => {
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
  })
}

async function handleDeleteSection(id: number) {
  await writeThenReload(() => protocol.deleteSection(id))
}

function openAddItem(sectionId: number) {
  editItemId.value = null
  itemSectionId.value = sectionId
  itemLabel.value = ''
  itemDescription.value = ''
  itemPoints.value = 1
  itemBonus.value = false
  showItemModal.value = true
}

function openEditItem(item: TestProtocolItem) {
  editItemId.value = item.id
  itemSectionId.value = item.sectionId
  itemLabel.value = item.label
  itemDescription.value = item.description
  itemPoints.value = item.points
  itemBonus.value = item.bonus
  showItemModal.value = true
}

async function handleSaveItem() {
  if (!itemLabel.value.trim()) return
  await writeThenReload(async () => {
    if (editItemId.value) {
      await protocol.updateItem(editItemId.value, {
        label: itemLabel.value.trim(),
        description: itemDescription.value,
        points: itemPoints.value,
        bonus: itemBonus.value,
        position: items.value.find(i => i.id === editItemId.value)?.position ?? 0,
      })
    } else {
      await protocol.createItem(itemSectionId.value, {
        label: itemLabel.value.trim(),
        description: itemDescription.value,
        points: itemPoints.value,
        bonus: itemBonus.value,
        position: sectionItems(itemSectionId.value).length,
      })
    }
    showItemModal.value = false
  })
}

async function handleDeleteItem(id: number) {
  await writeThenReload(() => protocol.deleteItem(id))
}

/**
 * Moves one entry of a level, on screen at once and then on the server, which takes the whole level
 * in its new order. Nothing is read back after a move that went through, so the page stays as it is;
 * a move the server refused reads the protocol back, so the screen shows what is stored.
 */
async function reorderLevel(
    level: { id: number, position: number }[],
    fromIndex: number,
    toIndex: number,
    save: (ids: number[]) => Promise<void>,
) {
  const ordered = moveWithin(level, fromIndex, toIndex)
  ordered.forEach((entry, index) => { entry.position = index })
  failure.value = null
  try {
    await save(ordered.map(entry => entry.id))
  } catch (e) {
    failure.value = describeFailure(e, t)
    await loadData()
  }
}

function reorderSections(parentId: number | null, fromIndex: number, toIndex: number) {
  if (!proto.value) return
  const protocolShown = proto.value.id
  void reorderLevel(tree.value.childrenOf(parentId), fromIndex, toIndex, ids => protocol.reorderSections(protocolShown, ids))
}

function reorderItems(sectionId: number, fromIndex: number, toIndex: number) {
  void reorderLevel(sectionItems(sectionId), fromIndex, toIndex, ids => protocol.reorderItems(sectionId, ids))
}

provide(protocolEditorKey, {
  tree,
  canEdit,
  cut: sectionCut.cut,
  addItem: openAddItem,
  addSubsection: openAddSection,
  editSection: openEditSection,
  deleteSection: handleDeleteSection,
  editItem: openEditItem,
  deleteItem: handleDeleteItem,
  reorderItems,
  reorderSections,
  cutSection: sectionCut.start,
  canPasteInto: sectionCut.canPasteInto,
  pasteInto: sectionCut.pasteInto,
})

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
  await writeThenReload(async () => {
    await protocol.updateProtocol(proto.value!.id, {
      name: editProtoName.value.trim(),
      description: editProtoDescription.value,
      passThreshold: editProtoPassThreshold.value ?? null,
    })
    showEditProtocolModal.value = false
  })
}

/**
 * The protocol's own name at the head of the page, because "Protokoll" is the word above every one
 * of them and it is what the tab, the history and a bookmark carry. It holds the place while the
 * protocol loads and where it cannot be loaded at all.
 */
const pageTitle = computed(() => proto.value?.name || t('pages.protocol-detail.title'))

watch(loaded, (v) => { if (v) loadData() }, { immediate: true })
</script>

<template>
  <ViewContent
      :title="pageTitle"
      :subtitle="t('pages.protocol-detail.subtitle')"
  >
    <div class="flex items-center gap-2 mb-4">
      <SecondaryButton @click="router.push({ name: 'protocol-list' })">
        <font-awesome-icon :icon="['fas', 'chevron-left']" />
      </SecondaryButton>
      <SectionHeader>{{ proto?.name ?? '' }}</SectionHeader>
      <StationBadge v-if="isFederated" :station-name="''" />
      <EditButton v-if="canEdit" :label="t('common.edit')" @click="openEditProtocol" />
      <span class="text-sm text-[var(--text-muted)] ml-auto">
        <template v-if="proto?.passThreshold">{{ t('protocol.threshold') }}: {{ proto.passThreshold }}P / </template>
        {{ totalProtocolPoints }}P {{ t('protocol.total') }}
      </span>
    </div>

    <Spinner v-if="loading && !proto" />
    <FailureAlert :failure="failure"/>

    <template v-if="proto">
      <MutedText v-if="proto.description" tag="p" size="sm">{{ proto.description }}</MutedText>

      <ProtocolCutBar v-if="sectionCut.cut.value" class="mb-4" :section="sectionCut.cut.value"
                      :can-paste-at-top="sectionCut.canPasteInto(null)"
                      @paste-at-top="sectionCut.pasteInto(null)" @cancel="sectionCut.cancel()"/>

      <DragList
          :items="topSections()"
          :key-fn="(section) => section.id"
          :disabled="!canEdit"
          class="space-y-4"
          @reorder="(from, to) => reorderSections(null, from, to)"
      >
        <template #default="{item: section}">
          <ProtocolSectionNode :section="section" :depth="0"/>
        </template>
      </DragList>

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
      v-model:bonus="itemBonus"
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
