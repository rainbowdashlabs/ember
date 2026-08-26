/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, inject, provide, ref, type InjectionKey, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useAsyncLoader} from '@/composables/useAsyncLoader'
import {useConfirmDelete} from '@/composables/useConfirmDelete'
import {
    DATE_FIELD_TYPES, FieldTypes, parseFieldConfig,
    type ProfileField, type ProfileFieldConfig, type ProfileFieldRequest,
} from '@/api/profileFields'
import type {StationGroup} from '@/api/clusterStationGroups'
import type {MemberGroup} from '@/api/types'
import {moveWithin} from '@/util/reorder'

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
    /** Writes a whole order at once, because dragging one field moves every field below it. */
    reorder(fieldIds: number[]): Promise<unknown>
    /** Which scopes this owner may declare, in tab order. */
    scopes: readonly string[]
    /** Which field types this owner may choose. A template naming any other does not offer itself. */
    types: readonly string[]
    /** The groups a group-scoped field can name, when this owner has group scope at all. */
    listGroups?: () => Promise<MemberGroup[]>
    /** The station groups a question can be pointed at, when this owner files its stations at all. */
    listStationGroups?: () => Promise<StationGroup[]>
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
 * What the panels below the editor may draw, which follows from who owns the fields.
 *
 * <p>Injected rather than handed down through four components that have no use for it themselves. The
 * same trick the news and events screens use to learn where a click lands.
 */
export interface FieldsCapabilities {
    /** Whether to offer the three way choice of who may change an answer in place of one toggle. */
    writability: boolean
    /** The types this owner may choose. A template naming any other does not offer itself. */
    types: readonly string[]
}

const FIELDS_CAPABILITIES: InjectionKey<FieldsCapabilities> = Symbol('fieldsCapabilities')

/** What a station may draw, which is the answer for anything mounted outside one of these screens. */
const STATION_CAPABILITIES: FieldsCapabilities = {
    writability: false,
    types: Object.values(FieldTypes),
}

export function useFieldsCapabilities(): FieldsCapabilities {
    return inject(FIELDS_CAPABILITIES, STATION_CAPABILITIES)
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

    provide(FIELDS_CAPABILITIES, {writability: port.stationReadonly, types: port.types})

    const allFields = ref<ProfileField[]>([])
    const availableGroups = ref<MemberGroup[]>([])
    const availableStationGroups = ref<StationGroup[]>([])
    const activeTab = ref(port.scopes[0] ?? 'MEMBER')
    const selectedGroupId = ref('')
    /**
     * Which station group's questions the screen is showing, {@code null} for the ones asked of every
     * station. Never confuse it with {@code selectedGroupId}, which is a station's member group and the
     * axis of the GROUP scope: two kinds of group on one screen.
     */
    const selectedStationGroupId = ref<number | null>(null)

    const showFieldModal = ref(false)
    const editingField = ref<ProfileField | null>(null)

    const {loading, error, reload} = useAsyncLoader(async () => {
        const [fields, groups, stationGroups] = await Promise.all([
            port.list(),
            port.listGroups ? port.listGroups() : Promise.resolve([] as MemberGroup[]),
            port.listStationGroups ? port.listStationGroups() : Promise.resolve([] as StationGroup[]),
        ])
        allFields.value = fields
        availableGroups.value = groups
        availableStationGroups.value = stationGroups
    })

    const currentFields = computed(() => {
        if (activeTab.value !== GROUP_SCOPE) {
            return allFields.value.filter(f => f.scope === activeTab.value
                && (f.stationGroupId ?? null) === selectedStationGroupId.value)
        }
        if (!selectedGroupId.value) return []
        return allFields.value.filter(f => {
            if (f.scope !== GROUP_SCOPE) return false
            return parseFieldConfig(f.config).groupId === Number(selectedGroupId.value)
        })
    })

    /**
     * What a person of the active kind at a station in the active group is actually shown: the questions
     * asked of every station plus the ones asked of this one, in position order. The table above lists
     * only the tab's own questions, because that is the set a click can act on.
     */
    const previewFields = computed(() => {
        if (activeTab.value === GROUP_SCOPE || selectedStationGroupId.value === null) return currentFields.value
        return allFields.value
            .filter(f => f.scope === activeTab.value
                && ((f.stationGroupId ?? null) === null || f.stationGroupId === selectedStationGroupId.value))
            .sort((a, b) => a.position - b.position)
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

    /**
     * A question is asked of whichever station group the screen is showing. A port that files no station
     * groups never sends the key, because its endpoint neither expects nor reads it.
     */
    function withTarget<T extends ProfileFieldRequest>(data: T): T {
        if (!port.listStationGroups) return data
        return {...data, stationGroupId: selectedStationGroupId.value}
    }

    async function saveField(data: ProfileFieldRequest & {scope?: string}) {
        error.value = ''
        try {
            if (editingField.value) {
                await port.update(editingField.value.id, withTarget(data))
            } else {
                await port.create(withTarget({...data, position: currentFields.value.length}))
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
            ...(port.listStationGroups ? {stationGroupId: field.stationGroupId ?? null} : {}),
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
        const arr = moveWithin(currentFields.value, fromIndex, toIndex)
        try {
            await port.reorder(arr.map(field => field.id))
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
                await port.create(withTarget({
                    name: f.name,
                    fieldType: f.fieldType,
                    config: withSelectedGroup(f.config),
                    position: startPosition + i,
                    scope: activeTab.value,
                }))
            }
            await reload()
        } catch {
            error.value = t('common.error')
        }
    }

    return {
        allFields: allFields as Ref<ProfileField[]>,
        availableGroups,
        availableStationGroups,
        activeTab,
        selectedGroupId,
        selectedStationGroupId,
        currentFields,
        previewFields,
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
