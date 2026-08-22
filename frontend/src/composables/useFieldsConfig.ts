/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {
    DATE_FIELD_TYPES, FieldTypes, parseFieldConfig,
    type ProfileField, type ProfileFieldConfig, type ProfileFieldRequest,
} from '@/api/profileFields'
import type {MemberGroup} from '@/api/types'

/** A field of group scope belongs to a group, and only a station has those. */
export const GROUP_SCOPE = 'GROUP'

/**
 * Where a set of profile fields lives, and which choices whoever owns them may make.
 *
 * <p>A station declares fields for its own members. An association declares fields that are asked of
 * the members of all its stations, kept in its own table and merged into a station's profile when it is
 * read. The screens are the same screens, so the difference between the two is expressed here as data
 * rather than as a branch inside a panel.
 */
export interface FieldsPort {
    list(): Promise<ProfileField[]>
    create(field: ProfileFieldRequest & {scope?: string}): Promise<unknown>
    update(id: number, field: ProfileFieldRequest & {scope?: string}): Promise<unknown>
    remove(id: number): Promise<unknown>
    /** Which scopes this owner may declare, in tab order. */
    scopes: readonly string[]
    /** Which field types this owner may choose. A template naming any other does not offer itself. */
    types: readonly string[]
    /** The groups a group-scoped field can name, when this owner has group scope at all. */
    listGroups?: () => Promise<MemberGroup[]>
    /**
     * Whether this owner has a third party to lock out. An association does, and offers the three way
     * choice of who may change an answer; a station does not, and keeps its single member toggle.
     */
    stationReadonly: boolean
}

/**
 * Who may change the answer to a field.
 *
 * <p>Two flags already carry this between them: {@code config.readonly} stops the member, and
 * {@code stationReadonly} stops the station. Their combinations are a ladder with one rung that is
 * nonsense, where a member may write an answer their own station may not, and naming the rungs is what
 * makes that one unreachable.
 */
export const Writability = {
    /** Member, station and association alike. */
    EVERYONE: 'EVERYONE',
    /** The station and the association, but not the member. */
    NOT_MEMBER: 'NOT_MEMBER',
    /** Only the association that asked. */
    OWNER_ONLY: 'OWNER_ONLY',
} as const

export type WritabilityName = (typeof Writability)[keyof typeof Writability]

/** Reads the pair of flags a field carries as the rung it sits on. */
export function writabilityOf(field: ProfileField): WritabilityName {
    const readonly = !!parseFieldConfig(field.config).readonly
    if (!readonly) return Writability.EVERYONE
    return field.stationReadonly ? Writability.OWNER_ONLY : Writability.NOT_MEMBER
}

/** Writes a rung back as the pair of flags, so the nonsense combination is never produced. */
export function writabilityFlags(level: WritabilityName): {readonly: boolean; stationReadonly: boolean} {
    return {
        readonly: level !== Writability.EVERYONE,
        stationReadonly: level === Writability.OWNER_ONLY,
    }
}

/**
 * The profile field editor, without its markup.
 *
 * <p>Everything the members configuration screen does that is not drawing: loading, the active scope,
 * which fields belong to it, adding, editing, deleting, reordering, the toggles written straight from
 * a row, and applying a template. The station screen and the association screen pass different ports
 * and render the same panels.
 *
 * @param port where the fields live and what may be chosen
 */
export function useFieldsConfig(port: FieldsPort) {
    const {t} = useI18n()

    const allFields = ref<ProfileField[]>([])
    const availableGroups = ref<MemberGroup[]>([])
    const activeTab = ref(port.scopes[0] ?? 'MEMBER')
    const selectedGroupId = ref('')

    const showFieldModal = ref(false)
    const editingField = ref<ProfileField | null>(null)

    const {loading, error, reload} = useAsyncLoader(async () => {
        const [fields, groups] = await Promise.all([
            port.list(),
            port.listGroups ? port.listGroups() : Promise.resolve([] as MemberGroup[]),
        ])
        allFields.value = fields
        availableGroups.value = groups
    })

    const currentFields = computed(() => {
        if (activeTab.value !== GROUP_SCOPE) {
            return allFields.value.filter(f => f.scope === activeTab.value)
        }
        if (!selectedGroupId.value) return []
        return allFields.value.filter(f => {
            if (f.scope !== GROUP_SCOPE) return false
            return parseFieldConfig(f.config).groupId === Number(selectedGroupId.value)
        })
    })

    /**
     * Group fields that name no group. A field of this scope is only ever shown at its group, so one
     * without belongs nowhere and would stay out of reach. Opening it and saving puts it in the group
     * chosen above.
     */
    const unassignedGroupFields = computed(() => allFields.value.filter(
        f => f.scope === GROUP_SCOPE && !parseFieldConfig(f.config).groupId))

    const dateFields = computed(() =>
        currentFields.value.filter(f => DATE_FIELD_TYPES.includes(f.fieldType ?? '')))

    /** One per owner, not per scope: a member has one date of birth however the tabs are arranged. */
    const birthDateField = computed(() =>
        allFields.value.find(f => f.fieldType === FieldTypes.BIRTH_DATE) ?? null)

    function openAddField() {
        editingField.value = null
        showFieldModal.value = true
    }

    function openEditField(field: ProfileField) {
        editingField.value = field
        showFieldModal.value = true
    }

    async function saveField(data: ProfileFieldRequest & {scope?: string}) {
        error.value = ''
        try {
            if (editingField.value) {
                await port.update(editingField.value.id, data)
            } else {
                await port.create({...data, position: currentFields.value.length})
            }
            showFieldModal.value = false
            await reload()
        } catch {
            error.value = t('common.error')
        }
    }

    function updateFieldLocally(fieldId: number, patch: Partial<ProfileField>) {
        allFields.value = allFields.value.map(f => f.id === fieldId ? {...f, ...patch} : f)
    }

    /** The whole field as the API wants it back, with one part replaced. */
    function requestFor(field: ProfileField, patch: Partial<ProfileFieldRequest> = {}) {
        return {
            name: field.name ?? '',
            fieldType: field.fieldType ?? '',
            config: parseFieldConfig(field.config),
            position: field.position,
            keepOnArchive: field.keepOnArchive,
            stationReadonly: field.stationReadonly,
            ...patch,
        }
    }

    async function writeBack(field: ProfileField, patch: Partial<ProfileFieldRequest>) {
        try {
            await port.update(field.id, requestFor(field, patch))
        } catch {
            error.value = t('common.error')
            await reload()
        }
    }

    async function toggleFieldConfig(field: ProfileField, key: string, value: boolean) {
        const config = {...parseFieldConfig(field.config)}
        if (value) config[key] = true
        else delete config[key]
        updateFieldLocally(field.id, {config})
        await writeBack(field, {config})
    }

    async function toggleKeepOnArchive(field: ProfileField, value: boolean) {
        updateFieldLocally(field.id, {keepOnArchive: value})
        await writeBack(field, {keepOnArchive: value})
    }

    /**
     * Moves a field to a rung of the writability ladder, which is two flags at once. Only offered where
     * the port says there is somebody above the station to lock out.
     */
    async function setWritability(field: ProfileField, level: WritabilityName) {
        const flags = writabilityFlags(level)
        const config = {...parseFieldConfig(field.config)}
        if (flags.readonly) config.readonly = true
        else delete config.readonly
        updateFieldLocally(field.id, {config, stationReadonly: flags.stationReadonly})
        await writeBack(field, {config, stationReadonly: flags.stationReadonly})
    }

    const {
        show: showDeleteModal,
        target: deleteTarget,
        requestDelete,
        confirm: confirmDelete,
    } = useConfirmDelete<ProfileField>({
        onDelete: async (field) => { await port.remove(field.id) },
        onSuccess: () => reload(),
        error,
    })

    async function onReorder(fromIndex: number, toIndex: number) {
        const arr = [...currentFields.value]
        const [moved] = arr.splice(fromIndex, 1)
        if (!moved) return
        arr.splice(toIndex, 0, moved)
        try {
            for (const [i, field] of arr.entries()) {
                await port.update(field.id, requestFor(field, {position: i}))
            }
            await reload()
        } catch {
            error.value = t('common.error')
        }
    }

    /**
     * A field of group scope belongs to the group being configured. A template carries settings that
     * hold for every scope and cannot know which group that is, so it is told here.
     */
    function withSelectedGroup(config: ProfileFieldConfig): ProfileFieldConfig {
        if (activeTab.value !== GROUP_SCOPE || !selectedGroupId.value) return config
        return {...config, groupId: Number(selectedGroupId.value)}
    }

    async function applyTemplate(template: {fields: Array<{name: string; fieldType: string; config: ProfileFieldConfig}>}) {
        error.value = ''
        try {
            const startPosition = currentFields.value.length
            for (const [i, f] of template.fields.entries()) {
                await port.create({
                    name: f.name,
                    fieldType: f.fieldType,
                    config: withSelectedGroup(f.config),
                    position: startPosition + i,
                    scope: activeTab.value,
                })
            }
            await reload()
        } catch {
            error.value = t('common.error')
        }
    }

    return {
        allFields: allFields as Ref<ProfileField[]>,
        availableGroups,
        activeTab,
        selectedGroupId,
        currentFields,
        unassignedGroupFields,
        dateFields,
        birthDateField,
        showFieldModal,
        editingField,
        loading,
        error,
        reload,
        openAddField,
        openEditField,
        saveField,
        toggleFieldConfig,
        toggleKeepOnArchive,
        setWritability,
        showDeleteModal,
        deleteTarget,
        requestDelete,
        confirmDelete,
        onReorder,
        applyTemplate,
    }
}
