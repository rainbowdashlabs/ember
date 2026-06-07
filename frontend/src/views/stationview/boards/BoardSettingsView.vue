/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute, useRouter } from 'vue-router'
import ViewContent from '@/components/layout/ViewContent.vue'
import SectionHeader from '@/components/typography/SectionHeader.vue'
import SubHeader from '@/components/typography/SubHeader.vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import IconButton from '@/components/button/IconButton.vue'
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import FieldLabel from '@/components/typography/FieldLabel.vue'
import TextInput from '@/components/input/text/TextInput.vue'
import ColorInput from '@/components/input/ColorInput.vue'
import TextAreaInput from '@/components/input/text/TextAreaInput.vue'
import ToggleInput from '@/components/input/toggle/ToggleInput.vue'
import NumberInput from '@/components/input/number/NumberInput.vue'
import Spinner from '@/components/feedback/Spinner.vue'
import Alert from '@/components/feedback/Alert.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import MultiSelectDropdown from '@/components/input/select/MultiSelectDropdown.vue'
import DeleteButton from '@/components/button/DeleteButton.vue'
import { boards, stationMembers, memberGroups, userTags, federation } from '@/api'
import type { Board, BoardFieldConfig, FederationTarget } from '@/api/boards'
import type { PermissionGrant, MemberGroup, UserTag } from '@/api/types'
import { StationUserType, StationPermission } from '@/api/types'
import { useSession } from '@/composables/useSession'
import type { PartnerResponse } from '@/api/federation'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const { hasPermission } = useSession()
const canFederate = computed(() => hasPermission(StationPermission.BOARD_FEDERATE))

const boardKey = computed(() => route.params.boardKey as string)
const board = ref<Board | null>(null)
const loading = ref(true)
const error = ref('')
const saved = ref(false)

const name = ref('')
const description = ref('')
const hideDoneAfterDays = ref(7)
const hasBacklog = ref(false)
const lanes = ref<{ name: string; color: string | null; id?: number }[]>([])
const newLaneName = ref('')
const fields = ref<{ name: string; fieldType: string; config: BoardFieldConfig }[]>([])
const newFieldName = ref('')
const newFieldType = ref('STRING')

// Access restrictions
const allRoles = ref<PermissionGrant[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const viewUserTypes = ref<string[]>([])
const viewGroupIds = ref<number[]>([])
const viewTagIds = ref<number[]>([])
const editUserTypes = ref<string[]>([])
const editGroupIds = ref<number[]>([])
const editTagIds = ref<number[]>([])

// Federation
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

const USER_TYPE_LABELS: Record<string, string> = {
    TRIAL: 'Probe',
    MEMBER: 'Mitglied',
    GUARDIAN: 'Erziehungsberechtigter',
    TEAM: 'Team',
    MANAGER: 'Manager',
}

const roleOptions = computed(() =>
    Object.values(StationUserType).map(ut => ({ value: ut, label: USER_TYPE_LABELS[ut] ?? ut }))
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


async function loadData() {
    loading.value = true
    try {
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
        } catch { /* no federation permission */ }
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
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

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

// Auto-save on any change
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

onMounted(loadData)
</script>

<template>
    <ViewContent>
        <Spinner v-if="loading" />
        <Alert v-else-if="error && !board" variant="error">{{ error }}</Alert>
        <template v-else-if="board">
            <div class="flex items-center justify-between mb-6">
                <div class="flex items-center gap-3">
                    <IconButton :icon="['fas', 'chevron-left']" label="Back" @click="router.push(`/station/boards/${board.shortKey}`)" />
                    <SectionHeader>{{ t('boards.settings') }}</SectionHeader>
                    <span class="text-xs font-mono text-[var(--text-muted)] bg-[var(--bg-muted)] px-1.5 py-0.5 rounded">{{ board.shortKey }}</span>
                </div>
                <span v-if="saving" class="text-xs text-[var(--text-muted)] flex items-center gap-1">
                    <Spinner size="sm" /> {{ t('common.saving') }}
                </span>
                <span v-else-if="saved" class="text-xs text-success flex items-center gap-1">
                    <font-awesome-icon :icon="['fas', 'check']" /> {{ t('common.saved') }}
                </span>
            </div>

            <Alert v-if="error" variant="error" class="mb-4">{{ error }}</Alert>

            <div class="space-y-6 max-w-2xl">
                <!-- General -->
                <NeutralContainer>
                    <SubHeader class="text-sm mb-3">{{ t('common.general') }}</SubHeader>
                    <div class="space-y-3">
                        <div>
                            <FieldLabel class="mb-1">{{ t('boards.boardName') }}</FieldLabel>
                            <TextInput v-model="name" />
                        </div>
                        <div>
                            <FieldLabel class="mb-1">{{ t('boards.boardDescription') }}</FieldLabel>
                            <TextAreaInput v-model="description" :rows="3" />
                        </div>
                        <div>
                            <FieldLabel class="mb-1">{{ t('boards.hideDoneAfterDays') }}</FieldLabel>
                            <NumberInput v-model="hideDoneAfterDays" :min="1" :max="365" />
                        </div>
                        <div class="flex items-center justify-between">
                            <FieldLabel>{{ t('boards.backlog') }}</FieldLabel>
                            <ToggleInput v-model="hasBacklog" />
                        </div>
                    </div>
                </NeutralContainer>

                <!-- Lanes -->
                <NeutralContainer>
                    <SubHeader class="text-sm mb-3">{{ t('boards.lanes') }}</SubHeader>
                    <div class="space-y-2">
                        <div v-for="(lane, index) in lanes" :key="index" class="flex items-center gap-2">
                            <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-[var(--text-muted)] cursor-grab" />
                            <ColorInput :model-value="lane.color ?? '#6b7280'" @update:model-value="lane.color = $event" />
                            <TextInput v-model="lane.name" class="flex-1" />
                            <IconButton :icon="['fas', 'chevron-up']" label="Move up" :disabled="index === 0" @click="moveLane(index, -1)" />
                            <IconButton :icon="['fas', 'chevron-down']" label="Move down" :disabled="index === lanes.length - 1" @click="moveLane(index, 1)" />
                            <IconButton :icon="['fas', 'xmark']" label="Remove" @click="removeLane(index)" />
                        </div>
                    </div>
                    <div class="flex gap-2 mt-3">
                        <TextInput v-model="newLaneName" :placeholder="t('boards.addLane')" class="flex-1" @keydown.enter="addLane" />
                        <SecondaryButton @click="addLane">
                            <font-awesome-icon :icon="['fas', 'plus']" />
                        </SecondaryButton>
                    </div>
                </NeutralContainer>

                <!-- Fields -->
                <NeutralContainer>
                    <SubHeader class="text-sm mb-3">{{ t('boards.fields') }}</SubHeader>
                    <div class="space-y-2">
                        <div v-for="(field, index) in fields" :key="index" class="space-y-1 border border-[var(--border)] rounded-theme p-2">
                            <div class="flex items-center gap-2">
                                <font-awesome-icon :icon="['fas', 'grip-vertical']" class="text-[var(--text-muted)] cursor-grab" />
                                <TextInput v-model="field.name" :placeholder="t('boards.fieldName')" class="flex-1" />
                                <SelectInput v-model="field.fieldType">
                                    <option v-for="ft in fieldTypeOptions" :key="ft.value" :value="ft.value">{{ t(ft.label) }}</option>
                                </SelectInput>
                                <IconButton :icon="['fas', 'chevron-up']" label="Move up" :disabled="index === 0" @click="moveField(index, -1)" />
                                <IconButton :icon="['fas', 'chevron-down']" label="Move down" :disabled="index === fields.length - 1" @click="moveField(index, 1)" />
                                <IconButton :icon="['fas', 'xmark']" label="Remove" @click="removeField(index)" />
                            </div>
                            <div v-if="field.fieldType === 'ENUM'" class="pl-6">
                                <TextInput
                                    :model-value="field.config.options?.join(', ') ?? ''"
                                    :placeholder="t('boards.fieldOptions')"
                                    class="text-sm"
                                    @update:model-value="v => field.config = { ...field.config, options: (v as string).split(',').map(s => s.trim()).filter(Boolean) }"
                                />
                            </div>
                            <div v-if="field.fieldType === 'LANE_ASSIGNEE'" class="pl-6">
                                <FieldLabel class="text-xs mb-1">{{ t('boards.fieldLane') }}</FieldLabel>
                                <SelectInput :model-value="String(field.config.laneId ?? '')" @update:model-value="(v: any) => field.config = { ...field.config, laneId: v ? Number(v) : null }">
                                    <option value="">—</option>
                                    <option v-for="lane in lanes" :key="lane.id" :value="String(lane.id)">{{ lane.name }}</option>
                                </SelectInput>
                            </div>
                        </div>
                    </div>
                    <div class="flex gap-2 mt-3">
                        <TextInput v-model="newFieldName" :placeholder="t('boards.addField')" class="flex-1" @keydown.enter="addField" />
                        <SelectInput v-model="newFieldType">
                            <option v-for="ft in fieldTypeOptions" :key="ft.value" :value="ft.value">{{ t(ft.label) }}</option>
                        </SelectInput>
                        <SecondaryButton @click="addField">
                            <font-awesome-icon :icon="['fas', 'plus']" />
                        </SecondaryButton>
                    </div>
                </NeutralContainer>

                <!-- View Access -->
                <NeutralContainer>
                    <SubHeader class="text-sm mb-3">{{ t('boards.viewAccess') }}</SubHeader>
                    <p class="text-xs text-[var(--text-muted)] mb-3">Leer = sichtbar für alle Mitglieder</p>
                    <RestrictionPicker
                        :roles="allRoles"
                        :groups="allGroups"
                        :tags="allTags"
                        :selected-user-types="viewUserTypes"
                        :selected-group-ids="viewGroupIds"
                        :selected-tag-ids="viewTagIds"
                        @update:selected-user-types="viewUserTypes = $event"
                        @update:selected-group-ids="viewGroupIds = $event"
                        @update:selected-tag-ids="viewTagIds = $event"
                    />
                </NeutralContainer>

                <!-- Edit Access -->
                <NeutralContainer>
                    <SubHeader class="text-sm mb-3">{{ t('boards.editAccess') }}</SubHeader>
                    <p class="text-xs text-[var(--text-muted)] mb-3">Leer = alle mit Lesezugriff können bearbeiten</p>
                    <RestrictionPicker
                        :roles="allRoles"
                        :groups="allGroups"
                        :tags="allTags"
                        :selected-user-types="editUserTypes"
                        :selected-group-ids="editGroupIds"
                        :selected-tag-ids="editTagIds"
                        @update:selected-user-types="editUserTypes = $event"
                        @update:selected-group-ids="editGroupIds = $event"
                        @update:selected-tag-ids="editTagIds = $event"
                    />
                </NeutralContainer>

                <!-- Federation -->
                <NeutralContainer>
                    <SubHeader class="text-sm mb-3">{{ t('boards.federation') }}</SubHeader>

                    <template v-if="canFederate">
                        <p class="text-xs text-[var(--text-muted)] mb-3">{{ t('boards.federationShareDesc') }}</p>

                        <!-- Existing share targets -->
                        <div class="space-y-2">
                            <div v-for="(target, index) in federationTargets" :key="target.partnerId" class="flex items-center gap-2 flex-wrap">
                                <span class="font-medium text-sm min-w-24">{{ partnerName(target.partnerId) }}</span>
                                <div class="flex items-center gap-1">
                                    <span class="text-xs text-[var(--text-muted)] shrink-0">{{ t('boards.accessMode') }}:</span>
                                    <SelectInput :model-value="target.shareMode" @update:model-value="(v: any) => target.shareMode = v">
                                        <option value="READ_ONLY">{{ t('boards.shareModeReadOnly') }}</option>
                                        <option value="FULL">{{ t('boards.shareModeFull') }}</option>
                                    </SelectInput>
                                </div>
                                <div class="flex items-center gap-1">
                                    <span class="text-xs text-[var(--text-muted)] shrink-0">{{ t('boards.minViewRole') }}:</span>
                                    <SelectInput :model-value="target.requiredRole" @update:model-value="(v: any) => target.requiredRole = v">
                                        <option value="USER">{{ t('boards.requiredRoleUser') }}</option>
                                        <option value="TEAM">{{ t('boards.requiredRoleTeam') }}</option>
                                        <option value="MANAGER">{{ t('boards.requiredRoleManager') }}</option>
                                    </SelectInput>
                                </div>
                                <DeleteButton @click="removePartner(index)" />
                            </div>
                        </div>

                        <!-- Add partner -->
                        <div v-if="availablePartners.length > 0" class="flex gap-2 mt-3">
                            <SelectInput :model-value="String(addPartnerId ?? '')" class="flex-1" @update:model-value="(v: any) => addPartnerId = v ? Number(v) : null">
                                <option value="">{{ t('boards.federationShare') }}...</option>
                                <option v-for="p in availablePartners" :key="p.partner.id" :value="String(p.partner.id)">
                                    {{ p.partnerStationName }}
                                </option>
                            </SelectInput>
                            <SecondaryButton :disabled="addPartnerId == null" @click="addPartner">
                                <font-awesome-icon :icon="['fas', 'plus']" />
                            </SecondaryButton>
                        </div>

                        <!-- Federated edit roles -->
                        <div v-if="hasFullMode" class="mt-4">
                            <FieldLabel class="mb-1">{{ t('boards.federatedEditRoles') }}</FieldLabel>
                            <p class="text-xs text-[var(--text-muted)] mb-2">{{ t('boards.federatedEditRolesDesc') }}</p>
                            <MultiSelectDropdown
                                :options="roleOptions"
                                :model-value="federatedEditUserTypes"
                                @update:model-value="federatedEditUserTypes = $event"
                            />
                        </div>
                    </template>

                    <!-- Read-only: no federation permission -->
                    <template v-else>
                        <p class="text-xs text-[var(--text-muted)] italic">{{ t('boards.federationNoPermission') }}</p>
                        <div v-if="federationTargets.length > 0" class="space-y-1 mt-2">
                            <div v-for="target in federationTargets" :key="target.partnerId" class="text-sm text-[var(--text-muted)]">
                                {{ partnerName(target.partnerId) }} — {{ target.shareMode }}
                            </div>
                        </div>
                    </template>
                </NeutralContainer>
            </div>
        </template>
    </ViewContent>
</template>
