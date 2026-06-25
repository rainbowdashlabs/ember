/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import BoardSettingsHeader from './boardsettingsview/BoardSettingsHeader.vue'
import BoardStructureSections from './boardsettingsview/BoardStructureSections.vue'
import BoardAccessSections from './boardsettingsview/BoardAccessSections.vue'
import type { LaneDraft } from './boardsettingsview/BoardLanesSection.vue'
import type { FieldDraft } from './boardsettingsview/BoardFieldsSection.vue'
import { boards, stationMembers, memberGroups, userTags, federation } from '@/api'
import type { Board, FederationTarget } from '@/api/boards'
import type { PermissionGrant, MemberGroup, UserTag } from '@/api/types'
import { StationUserType, StationUserTypeLabels, StationPermission } from '@/api/types'
import { useSession } from '@/composables/useSession'
import { useAsyncLoader } from '@/composables/useAsyncLoader'
import type { PartnerResponse } from '@/api/federation'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { hasPermission } = useSession()
const canFederate = computed(() => hasPermission(StationPermission.BOARD_FEDERATE))

const boardKey = computed(() => route.params.boardKey as string)
const board = ref<Board | null>(null)
const saved = ref(false)

const name = ref('')
const description = ref('')
const hideDoneAfterDays = ref(7)
const hasBacklog = ref(false)

const lanes = ref<LaneDraft[]>([])
const newLaneName = ref('')
const fields = ref<FieldDraft[]>([])
const newFieldName = ref('')
const newFieldType = ref('STRING')

const allRoles = ref<PermissionGrant[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const viewUserTypes = ref<string[]>([])
const viewGroupIds = ref<number[]>([])
const viewTagIds = ref<number[]>([])
const editUserTypes = ref<string[]>([])
const editGroupIds = ref<number[]>([])
const editTagIds = ref<number[]>([])

const allPartners = ref<PartnerResponse[]>([])
const federationTargets = ref<FederationTarget[]>([])
const federatedEditUserTypes = ref<string[]>([])
const addPartnerId = ref<number | null>(null)

const activePartners = computed(() =>
    allPartners.value.filter(p => p.partner.status === 'ACTIVE'),
)

const availablePartners = computed(() => {
    const sharedIds = new Set(federationTargets.value.map(t => t.partnerId))
    return activePartners.value.filter(p => !sharedIds.has(p.partner.id))
})

const hasFullMode = computed(() =>
    federationTargets.value.some(t => t.shareMode === 'FULL'),
)

const roleOptions = computed(() =>
    Object.values(StationUserType).map(ut => ({ value: ut, label: StationUserTypeLabels[ut] ?? ut }))
)

function partnerName(partnerId: number): string {
    const p = allPartners.value.find(p => p.partner.id === partnerId)
    return p?.partnerStationName ?? `Partner #${partnerId}`
}

function addPartner() {
    if (addPartnerId.value == null) return
    federationTargets.value.push({ partnerId: addPartnerId.value, shareMode: 'READ_ONLY', requiredRole: 'USER' })
    addPartnerId.value = null
}

function removePartner(index: number) {
    federationTargets.value.splice(index, 1)
}

const {loading, error} = useAsyncLoader(async () => {
    const [b, l, f, r, g, tg, va, ea] = await Promise.all([
        boards.getBoard(boardKey.value),
        boards.getLanes(boardKey.value),
        boards.getFields(boardKey.value),
        stationMembers.listAllPermissions(),
        memberGroups.listGroups(),
        userTags.listTags(),
        boards.getViewAccess(boardKey.value),
        boards.getEditAccess(boardKey.value),
    ])
    let fedConfig = { targets: [] as FederationTarget[], editUserTypes: [] as string[] }
    let partners: PartnerResponse[] = []
    try {
        [fedConfig, partners] = await Promise.all([
            boards.getBoardFederationConfig(boardKey.value),
            federation.listPartners(),
        ]) as [typeof fedConfig, PartnerResponse[]]
    } catch { void 0 }
    board.value = b
    name.value = b.name
    description.value = b.description ?? ''
    hideDoneAfterDays.value = b.hideDoneAfterDays
    hasBacklog.value = b.backlogLaneId !== null
    lanes.value = l.filter(l => l.id !== b.backlogLaneId).map(l => ({ name: l.name, color: l.color, id: l.id }))
    fields.value = f.map(f => ({ name: f.name, fieldType: f.fieldType, config: f.config }))
    allRoles.value = r
    allGroups.value = g
    allTags.value = tg
    viewUserTypes.value = va.userTypes ?? []
    viewGroupIds.value = va.groupIds ?? []
    viewTagIds.value = va.tagIds ?? []
    editUserTypes.value = ea.userTypes ?? []
    editGroupIds.value = ea.groupIds ?? []
    editTagIds.value = ea.tagIds ?? []
    federationTargets.value = fedConfig.targets ?? []
    federatedEditUserTypes.value = fedConfig.editUserTypes ?? []
    allPartners.value = partners
})

const saving = ref(false)
let saveDebounce: ReturnType<typeof setTimeout> | null = null

async function saveNow() {
    if (loading.value) return
    saving.value = true
    error.value = ''
    try {
        await boards.updateBoard(boardKey.value, {
            name: name.value,
            description: description.value,
            hideDoneAfterDays: hideDoneAfterDays.value,
        })
        await boards.setLanes(boardKey.value, lanes.value.map(l => ({ id: l.id, name: l.name, color: l.color })))
        if (hasBacklog.value && !board.value?.backlogLaneId) {
            await boards.enableBacklog(boardKey.value)
        } else if (!hasBacklog.value && board.value?.backlogLaneId) {
            await boards.disableBacklog(boardKey.value)
        }
        await boards.setFields(boardKey.value, fields.value)
        await boards.setViewAccess(boardKey.value, {
            userTypes: viewUserTypes.value, groupIds: viewGroupIds.value, tagIds: viewTagIds.value,
        })
        await boards.setEditAccess(boardKey.value, {
            userTypes: editUserTypes.value, groupIds: editGroupIds.value, tagIds: editTagIds.value,
        })
        if (federationTargets.value.length > 0 || federatedEditUserTypes.value.length > 0) {
            await boards.setBoardFederationConfig(boardKey.value, {
                targets: federationTargets.value,
                editUserTypes: federatedEditUserTypes.value,
            })
        }
        saved.value = true
        setTimeout(() => saved.value = false, 2000)
    } catch {
        error.value = t('common.error')
    } finally {
        saving.value = false
    }
}

function scheduleSave() {
    if (saveDebounce) clearTimeout(saveDebounce)
    saveDebounce = setTimeout(saveNow, 800)
}

watch([name, description, hideDoneAfterDays, hasBacklog, lanes, fields,
    viewUserTypes, viewGroupIds, viewTagIds,
    editUserTypes, editGroupIds, editTagIds,
    federationTargets, federatedEditUserTypes], scheduleSave, { deep: true })

function addLane() {
    if (!newLaneName.value.trim()) return
    lanes.value.push({ name: newLaneName.value.trim(), color: null })
    newLaneName.value = ''
}

function removeLane(index: number) {
    lanes.value.splice(index, 1)
}

function moveLane(index: number, dir: -1 | 1) {
    const newIndex = index + dir
    if (newIndex < 0 || newIndex >= lanes.value.length) return
    const temp = lanes.value[index]
    lanes.value[index] = lanes.value[newIndex]
    lanes.value[newIndex] = temp
}

function addField() {
    if (!newFieldName.value.trim()) return
    fields.value.push({ name: newFieldName.value.trim(), fieldType: newFieldType.value, config: { required: false, options: [] } })
    newFieldName.value = ''
    newFieldType.value = 'STRING'
}

function removeField(index: number) {
    fields.value.splice(index, 1)
}

function moveField(index: number, dir: -1 | 1) {
    const newIndex = index + dir
    if (newIndex < 0 || newIndex >= fields.value.length) return
    const temp = fields.value[index]
    fields.value[index] = fields.value[newIndex]
    fields.value[newIndex] = temp
}

const fieldTypeOptions = [
    { value: 'STRING', label: 'boards.fieldTypeString' },
    { value: 'NUMBER', label: 'boards.fieldTypeNumber' },
    { value: 'BOOLEAN', label: 'boards.fieldTypeBoolean' },
    { value: 'ENUM', label: 'boards.fieldTypeEnum' },
    { value: 'DATE', label: 'boards.fieldTypeDate' },
    { value: 'LANE_ASSIGNEE', label: 'boards.fieldTypeLaneAssignee' },
]

function goBack() {
    if (board.value) router.push(`/station/boards/${board.value.shortKey}`)
}

</script>

<template>
    <ViewContent>
        <Spinner v-if="loading" />
        <Alert v-else-if="error && !board" variant="error">{{ error }}</Alert>
        <template v-else-if="board">
            <BoardSettingsHeader :short-key="board.shortKey" :saving="saving" :saved="saved" @back="goBack" />
            <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>
            <div class="space-y-6 max-w-2xl">
                <BoardStructureSections
                    v-model:name="name"
                    v-model:description="description"
                    v-model:hide-done-after-days="hideDoneAfterDays"
                    v-model:has-backlog="hasBacklog"
                    :lanes="lanes"
                    v-model:new-lane-name="newLaneName"
                    :fields="fields"
                    v-model:new-field-name="newFieldName"
                    v-model:new-field-type="newFieldType"
                    :field-type-options="fieldTypeOptions"
                    @add-lane="addLane"
                    @remove-lane="removeLane"
                    @move-lane="moveLane"
                    @add-field="addField"
                    @remove-field="removeField"
                    @move-field="moveField"
                />
                <BoardAccessSections
                    :all-roles="allRoles"
                    :all-groups="allGroups"
                    :all-tags="allTags"
                    v-model:view-user-types="viewUserTypes"
                    v-model:view-group-ids="viewGroupIds"
                    v-model:view-tag-ids="viewTagIds"
                    v-model:edit-user-types="editUserTypes"
                    v-model:edit-group-ids="editGroupIds"
                    v-model:edit-tag-ids="editTagIds"
                    :can-federate="canFederate"
                    :federation-targets="federationTargets"
                    :available-partners="availablePartners"
                    :has-full-mode="hasFullMode"
                    v-model:add-partner-id="addPartnerId"
                    v-model:federated-edit-user-types="federatedEditUserTypes"
                    :role-options="roleOptions"
                    :partner-name="partnerName"
                    @add-partner="addPartner"
                    @remove-partner="removePartner"
                />
            </div>
        </template>
    </ViewContent>
</template>
