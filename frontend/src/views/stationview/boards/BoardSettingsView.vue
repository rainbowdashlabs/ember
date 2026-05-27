/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { ref, computed, onMounted } from 'vue'
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
import SuccessButton from '@/components/button/SuccessButton.vue'
import RestrictionPicker from '@/components/input/RestrictionPicker.vue'
import SelectInput from '@/components/input/select/SelectInput.vue'
import { boards, stationMembers, memberGroups, userTags } from '@/api'
import type { Board, BoardFieldConfig } from '@/api/boards'
import type { Role, MemberGroup, UserTag } from '@/api/types'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()

const boardId = computed(() => Number(route.params.boardId))
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
const newFieldType = ref('string')

// Access restrictions
const allRoles = ref<Role[]>([])
const allGroups = ref<MemberGroup[]>([])
const allTags = ref<UserTag[]>([])
const viewRoleIds = ref<number[]>([])
const viewGroupIds = ref<number[]>([])
const viewTagIds = ref<number[]>([])
const editRoleIds = ref<number[]>([])
const editGroupIds = ref<number[]>([])
const editTagIds = ref<number[]>([])


async function loadData() {
    loading.value = true
    try {
        const [b, l, f, r, g, tg, va, ea] = await Promise.all([
            boards.getBoard(boardId.value),
            boards.getLanes(boardId.value),
            boards.getFields(boardId.value),
            stationMembers.listAllRoles(),
            memberGroups.listGroups(),
            userTags.listTags(),
            boards.getViewAccess(boardId.value),
            boards.getEditAccess(boardId.value),
        ])
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
        viewRoleIds.value = va.roleIds ?? []
        viewGroupIds.value = va.groupIds ?? []
        viewTagIds.value = va.tagIds ?? []
        editRoleIds.value = ea.roleIds ?? []
        editGroupIds.value = ea.groupIds ?? []
        editTagIds.value = ea.tagIds ?? []
    } catch {
        error.value = t('common.error')
    } finally {
        loading.value = false
    }
}

async function save() {
    error.value = ''
    saved.value = false
    try {
        await boards.updateBoard(boardId.value, {
            name: name.value,
            description: description.value,
            hideDoneAfterDays: hideDoneAfterDays.value,
        })
        await boards.setLanes(boardId.value, lanes.value.map(l => ({ name: l.name, color: l.color })))
        if (hasBacklog.value && !board.value?.backlogLaneId) {
            await boards.enableBacklog(boardId.value)
        } else if (!hasBacklog.value && board.value?.backlogLaneId) {
            await boards.disableBacklog(boardId.value)
        }
        await boards.setFields(boardId.value, fields.value)
        await boards.setViewAccess(boardId.value, {
            roleIds: viewRoleIds.value, groupIds: viewGroupIds.value, tagIds: viewTagIds.value,
        })
        await boards.setEditAccess(boardId.value, {
            roleIds: editRoleIds.value, groupIds: editGroupIds.value, tagIds: editTagIds.value,
        })
        saved.value = true
        setTimeout(() => saved.value = false, 2000)
    } catch {
        error.value = t('common.error')
    }
}

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
    newFieldType.value = 'string'
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
    { value: 'string', label: 'boards.fieldTypeString' },
    { value: 'number', label: 'boards.fieldTypeNumber' },
    { value: 'boolean', label: 'boards.fieldTypeBoolean' },
    { value: 'enum', label: 'boards.fieldTypeEnum' },
    { value: 'date', label: 'boards.fieldTypeDate' },
    { value: 'lane_assignee', label: 'boards.fieldTypeLaneAssignee' },
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
                    <IconButton :icon="['fas', 'chevron-left']" label="Back" @click="router.push(`/station/boards/${board.id}`)" />
                    <SectionHeader>{{ t('boards.settings') }}</SectionHeader>
                    <span class="text-xs font-mono text-[var(--text-muted)] bg-[var(--bg-muted)] px-1.5 py-0.5 rounded">{{ board.shortKey }}</span>
                </div>
                <SuccessButton @click="save">
                    <font-awesome-icon :icon="['fas', 'check']" class="mr-1" />
                    {{ t('common.save') }}
                </SuccessButton>
            </div>

            <Alert v-if="saved" variant="success" class="mb-4">{{ t('common.saved') }}</Alert>
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
                            <div v-if="field.fieldType === 'enum'" class="pl-6">
                                <TextInput
                                    :model-value="field.config.options?.join(', ') ?? ''"
                                    :placeholder="t('boards.fieldOptions')"
                                    class="text-sm"
                                    @update:model-value="v => field.config = { ...field.config, options: (v as string).split(',').map(s => s.trim()).filter(Boolean) }"
                                />
                            </div>
                            <div v-if="field.fieldType === 'lane_assignee'" class="pl-6">
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
                        :selected-role-ids="viewRoleIds"
                        :selected-group-ids="viewGroupIds"
                        :selected-tag-ids="viewTagIds"
                        @update:selected-role-ids="viewRoleIds = $event"
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
                        :selected-role-ids="editRoleIds"
                        :selected-group-ids="editGroupIds"
                        :selected-tag-ids="editTagIds"
                        @update:selected-role-ids="editRoleIds = $event"
                        @update:selected-group-ids="editGroupIds = $event"
                        @update:selected-tag-ids="editTagIds = $event"
                    />
                </NeutralContainer>
            </div>
        </template>
    </ViewContent>
</template>
