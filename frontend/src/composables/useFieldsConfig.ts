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
    type AssignmentRequest, type AssignmentTarget,
    type ProfileField, type ProfileFieldConfig, type ProfileFieldRequest,
} from '@/api/profileFields'
import {asAsked, type ProfileFieldAssignment} from '@/util/profileFields'
import type {StationGroup} from '@/api/clusterStationGroups'
import type {MemberGroup} from '@/api/types'
import {moveWithin} from '@/util/reorder'

/**
 * Where a set of profile fields lives, and which choices whoever owns them may make.
 *
 * <p>A station declares questions asked of its own members. An association declares questions asked of
 * the members of all its stations, kept in its own table and merged into a station's profile when it is
 * read. The screens are the same screens, so the difference between the two is expressed here as data
 * rather than as a branch inside a panel.
 */
export interface FieldsPort {
    /** The questions themselves, without reference to who is asked them. */
    list(): Promise<ProfileField[]>
    /** Who each question is asked of. Read separately because one question has many audiences. */
    listAssignments(): Promise<ProfileFieldAssignment[]>
    create(field: ProfileFieldRequest): Promise<ProfileField>
    update(id: number, field: ProfileFieldRequest): Promise<unknown>
    remove(id: number): Promise<unknown>
    /** Puts a question to an audience, or changes how it is put to them. */
    assign(fieldId: number, assignment: AssignmentRequest): Promise<unknown>
    /** Stops asking an audience. The question and its answers stay. */
    unassign(fieldId: number, target: AssignmentTarget): Promise<unknown>
    /** Writes a whole form's order at once, because dragging one question moves every one below it. */
    reorder(role: string, fieldIds: number[]): Promise<unknown>
    /** The kinds of member this owner may ask, in the order the forms are listed. */
    roles: readonly string[]
    /** Which field types this owner may choose. A template naming any other does not offer itself. */
    types: readonly string[]
    /** The groups a question can be pointed at. An association has none: a group belongs to one station. */
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
 * The kinds of member a station asks, in the order their forms are listed.
 *
 * <p>A group is not one of these. It is a target of its own, because a station names its own groups and
 * no two stations name the same ones.
 */
export const STATION_ROLES = ['TRIAL', 'MEMBER', 'GUARDIAN', 'TEAM', 'MANAGER'] as const

/**
 * Who may change the answer to a question.
 *
 * <p>Two flags on the question carry this between them: {@code readonly} keeps it to the member
 * management, and {@code stationReadonly} keeps it to the association that asked. Their combinations
 * are a ladder with one rung that is nonsense, where a member may write an answer their own station
 * may not, and naming the rungs is what makes that one unreachable.
 *
 * <p>Both are the question's, for everybody asked it, which is why this takes no assignment.
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

/** Reads the pair of flags as the rung it sits on. */
export function writabilityOf(field: ProfileField): WritabilityName {
    if (!field.readonly) return Writability.EVERYONE
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
 * One audience a question is put to, as the panel beside it draws one.
 *
 * @param label what to call this audience on screen
 */
export interface Audience {
    target: AssignmentTarget
    assignment: ProfileFieldAssignment
    label: string
}

/**
 * A question together with how it is put to the audience whose form is being shown.
 *
 * <p>Used by the preview and by anything that lays a form out, where position, width and readonly are
 * the audience's answer rather than the question's.
 */
export interface FormEntry {
    field: ProfileField
    assignment: ProfileFieldAssignment
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
    /** Whether a question here can be put to a group of members, which only a station's can. */
    groups: boolean
}

const FIELDS_CAPABILITIES: InjectionKey<FieldsCapabilities> = Symbol('fieldsCapabilities')

/** What a station may draw, which is the answer for anything mounted outside one of these screens. */
const STATION_CAPABILITIES: FieldsCapabilities = {
    writability: false,
    types: Object.values(FieldTypes),
    groups: true,
}

export function useFieldsCapabilities(): FieldsCapabilities {
    return inject(FIELDS_CAPABILITIES, STATION_CAPABILITIES)
}

/**
 * The profile field editor, without its markup.
 *
 * <p>The screen it drives has two halves, and they are the two halves of the model. On one side the
 * questions a station asks, each written once. On the other, for whichever question is selected, the
 * audiences it is put to and how it is put to each of them.
 *
 * <p>It used to have one half, a tab per kind of member, and a question belonged to the tab it was
 * created in. Asking two kinds the same thing meant writing it twice, which is what put the same
 * question on a manager's profile twice and let the two copies collect different answers.
 *
 * @param port where the questions live and what may be chosen
 */
export function useFieldsConfig(port: FieldsPort) {
    const {t} = useI18n()

    provide(FIELDS_CAPABILITIES, {
        writability: port.stationReadonly,
        types: port.types,
        groups: !!port.listGroups,
    })

    const allFields = ref<ProfileField[]>([])
    const allAssignments = ref<ProfileFieldAssignment[]>([])
    const availableGroups = ref<MemberGroup[]>([])
    const availableStationGroups = ref<StationGroup[]>([])

    /** The question whose audiences the right hand panel is showing. */
    const selectedFieldId = ref<number | null>(null)
    /** Whose form the preview draws, which is one of the roles rather than a group. */
    const previewRole = ref(port.roles[0] ?? 'MEMBER')
    /**
     * Which station group's questions the screen is showing, {@code null} for the ones asked of every
     * station. An association's axis, and nothing to do with a station's member groups.
     */
    const selectedStationGroupId = ref<number | null>(null)

    const showFieldModal = ref(false)
    const editingField = ref<ProfileField | null>(null)

    async function fetchAll() {
        const [fields, assignments, groups, stationGroups] = await Promise.all([
            port.list(),
            port.listAssignments(),
            port.listGroups ? port.listGroups() : Promise.resolve([] as MemberGroup[]),
            port.listStationGroups ? port.listStationGroups() : Promise.resolve([] as StationGroup[]),
        ])
        allFields.value = fields
        allAssignments.value = assignments
        availableGroups.value = groups
        availableStationGroups.value = stationGroups
        if (selectedFieldId.value !== null && !fields.some(f => f.id === selectedFieldId.value)) {
            selectedFieldId.value = null
        }
    }

    const {loading, error, reload} = useAsyncLoader(fetchAll)

    /**
     * Reads everything back without putting the screen through its loading state.
     *
     * <p>For the writes made from the panels themselves. {@link reload} unmounts both of them while it
     * runs, which drops the reader at the top of the page; on a long form that means finding your
     * place again after every switch you flick. The modal's writes still use the loud one, because
     * there the screen is behind a dialog and the list genuinely changes underneath.
     */
    async function refresh() {
        try {
            await fetchAll()
        } catch {
            error.value = t('common.error')
        }
    }

    /** The questions this screen is about, by name. An association's are filed by group of stations. */
    const questions = computed(() => {
        const list = port.listStationGroups
            ? allFields.value.filter(f => (f.stationGroupId ?? null) === selectedStationGroupId.value)
            : allFields.value
        return [...list].sort((a, b) => (a.name ?? '').localeCompare(b.name ?? ''))
    })

    const selectedField = computed(() =>
        allFields.value.find(f => f.id === selectedFieldId.value) ?? null)

    function assignmentsOf(fieldId: number): ProfileFieldAssignment[] {
        return allAssignments.value.filter(a => a.fieldId === fieldId)
    }

    function labelOf(assignment: ProfileFieldAssignment): string {
        if (assignment.targetKind === 'GROUP') {
            const group = availableGroups.value.find(g => g.id === assignment.groupId)
            return group?.name ?? t('membersConfig.audiences.unknownGroup')
        }
        return t(`membersConfig.roles.${assignment.role}`)
    }

    function targetOf(assignment: ProfileFieldAssignment): AssignmentTarget {
        return assignment.targetKind === 'GROUP'
            ? {groupId: assignment.groupId ?? null}
            : {role: assignment.role ?? null}
    }

    /** Who the selected question is put to, roles first and then groups, each named for the screen. */
    const audiences = computed<Audience[]>(() => {
        if (selectedFieldId.value === null) return []
        return assignmentsOf(selectedFieldId.value)
            .map(assignment => ({target: targetOf(assignment), assignment, label: labelOf(assignment)}))
            .sort((a, b) => a.assignment.targetKind.localeCompare(b.assignment.targetKind)
                || a.label.localeCompare(b.label))
    })

    /** The roles the selected question is not yet put to, which is what the add control offers. */
    const unaskedRoles = computed(() => {
        if (selectedFieldId.value === null) return []
        const asked = new Set(assignmentsOf(selectedFieldId.value)
            .filter(a => a.targetKind === 'ROLE')
            .map(a => a.role))
        return port.roles.filter(role => !asked.has(role as never))
    })

    /** The groups the selected question is not yet put to. */
    const unaskedGroups = computed(() => {
        if (selectedFieldId.value === null) return []
        const asked = new Set(assignmentsOf(selectedFieldId.value)
            .filter(a => a.targetKind === 'GROUP')
            .map(a => a.groupId))
        return availableGroups.value.filter(group => !asked.has(group.id))
    })

    /**
     * A question nobody is asked. Legal and sometimes wanted while one is being written, but it reaches
     * no profile until an audience is named, so the screen says so rather than leaving it to be noticed.
     */
    const askedOfNobody = computed(() =>
        questions.value.filter(f => assignmentsOf(f.id).length === 0))

    /** One audience's form, in the order that audience sees it. */
    function formFor(role: string): FormEntry[] {
        return allAssignments.value
            .filter(a => a.targetKind === 'ROLE' && a.role === role)
            .map(a => ({field: allFields.value.find(f => f.id === a.fieldId), assignment: a}))
            .filter((entry): entry is FormEntry => entry.field !== undefined)
            .filter(entry => !port.listStationGroups
                || (entry.field.stationGroupId ?? null) === selectedStationGroupId.value)
            .sort((a, b) => a.assignment.position - b.assignment.position)
    }

    const previewForm = computed(() => formFor(previewRole.value))

    /** The same form with every override resolved, which is what the preview and the order list draw. */
    const previewFields = computed(() =>
        previewForm.value.map(entry => asAsked(entry.field, entry.assignment)))

    const dateFields = computed(() =>
        questions.value.filter(f => DATE_FIELD_TYPES.includes(f.fieldType ?? '')))

    /**
     * The station's date of birth, which decides whether another may be added.
     *
     * <p>One per station now, whoever is asked it. There used to be one per kind of member and a rule
     * about which could coexist; a question written once and assigned to everybody who is asked it needs
     * no such rule, and a second is a duplicate rather than a different question.
     */
    const birthDateField = computed(() =>
        allFields.value.find(f => f.fieldType === FieldTypes.BIRTH_DATE) ?? null)

    function select(fieldId: number | null) {
        selectedFieldId.value = fieldId
    }

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

    /**
     * Writes a question, and selects it where it is new.
     *
     * <p>A new one is put to nobody yet: naming the audiences is the next thing the screen asks for, and
     * guessing one here is how a question ends up on a form nobody meant it to be on.
     */
    async function saveField(data: ProfileFieldRequest) {
        error.value = ''
        try {
            if (editingField.value) {
                await port.update(editingField.value.id, withTarget(data))
            } else {
                const created = await port.create(withTarget(data))
                selectedFieldId.value = created?.id ?? null
            }
            showFieldModal.value = false
            await reload()
        } catch {
            error.value = t('common.error')
        }
    }

    async function toggleKeepOnArchive(field: ProfileField, value: boolean) {
        await writeField(field, {keepOnArchive: value})
    }

    async function toggleRequired(field: ProfileField, value: boolean) {
        await writeField(field, {required: value})
    }

    /** Whether only the member management may write the answer, which holds for everybody asked it. */
    async function toggleReadonly(field: ProfileField, value: boolean) {
        await writeField(field, {readonly: value})
    }

    async function toggleFieldConfig(field: ProfileField, key: string, value: boolean) {
        const config = {...parseFieldConfig(field.config)}
        if (value) config[key] = true
        else delete config[key]
        await writeField(field, {config})
    }

    /** The whole question as the API wants it back, with one part replaced. */
    function requestFor(field: ProfileField, patch: Partial<ProfileFieldRequest> = {}): ProfileFieldRequest {
        return {
            name: field.name ?? '',
            fieldType: field.fieldType ?? '',
            config: parseFieldConfig(field.config),
            required: field.required ?? false,
            readonly: field.readonly ?? false,
            width: field.width ?? null,
            keepOnArchive: field.keepOnArchive,
            stationReadonly: field.stationReadonly,
            ...(port.listStationGroups ? {stationGroupId: field.stationGroupId ?? null} : {}),
            ...patch,
        }
    }

    async function writeField(field: ProfileField, patch: Partial<ProfileFieldRequest>) {
        allFields.value = allFields.value.map(f => f.id === field.id ? {...f, ...patch} : f)
        try {
            await port.update(field.id, requestFor(field, patch))
        } catch {
            error.value = t('common.error')
            await reload()
        }
    }

    /** Puts the selected question to one more audience, at the end of that audience's form. */
    async function addAudience(target: AssignmentTarget) {
        if (selectedFieldId.value === null) return
        const role = target.role
        const position = role ? formFor(role).length : audiences.value.length
        await writeAssignment(selectedFieldId.value, {...target, position})
    }

    /** Stops asking one audience. The question stays, and so do the answers already given. */
    async function removeAudience(target: AssignmentTarget) {
        if (selectedFieldId.value === null) return
        error.value = ''
        try {
            await port.unassign(selectedFieldId.value, target)
            await refresh()
        } catch {
            error.value = t('common.error')
        }
    }

    /** Changes how the selected question is put to one audience. */
    async function setAudience(audience: Audience, patch: Partial<AssignmentRequest>) {
        if (selectedFieldId.value === null) return
        await writeAssignment(selectedFieldId.value, {
            ...audience.target,
            position: audience.assignment.position,
            widthOverride: audience.assignment.widthOverride,
            readonlyOverride: audience.assignment.readonlyOverride,
            requiredOverride: audience.assignment.requiredOverride,
            ...patch,
        })
    }

    /**
     * Moves one question to a rung of the writability ladder.
     *
     * <p>Both halves are the question's, so this is one write. An association sets the second half as
     * well; a station has nobody below it and leaves it alone.
     */
    async function setWritability(field: ProfileField, level: WritabilityName) {
        const flags = writabilityFlags(level)
        await writeField(field, port.stationReadonly
            ? {readonly: flags.readonly, stationReadonly: flags.stationReadonly}
            : {readonly: flags.readonly})
    }

    async function writeAssignment(fieldId: number, assignment: AssignmentRequest) {
        error.value = ''
        try {
            await port.assign(fieldId, assignment)
            await refresh()
        } catch {
            error.value = t('common.error')
        }
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

    /**
     * Reorders one audience's form, which leaves every other form where it was.
     *
     * <p>The new order is written into the list held here before the request goes, and nothing is
     * loaded again when it succeeds. Reloading put the whole screen back through its loading state,
     * which unmounts the panels and drops the reader at the top of the page: one drag near the
     * bottom of a long form and they had to find their place again. The server is told the same
     * order it is being shown, so there is nothing to fetch back; a refusal puts the old order back
     * and says so.
     */
    async function onReorder(role: string, fromIndex: number, toIndex: number) {
        const ordered = moveWithin(formFor(role), fromIndex, toIndex).map(entry => entry.field.id)
        const before = allAssignments.value
        allAssignments.value = allAssignments.value.map(assignment =>
            assignment.targetKind === 'ROLE' && assignment.role === role && ordered.includes(assignment.fieldId)
                ? {...assignment, position: ordered.indexOf(assignment.fieldId) + 1}
                : assignment)
        try {
            await port.reorder(role, ordered)
        } catch {
            allAssignments.value = before
            error.value = t('common.error')
        }
    }

    /**
     * The questions ticked for putting to somebody all at once.
     *
     * <p>Apart from the one selected question, which is a different act: that one says whose panel is
     * being shown, these say what the next assignment is about. A station writing down fifteen
     * questions and putting the same twelve to three kinds of member should not have to do that
     * thirty-six times.
     */
    const checkedIds = ref<Set<number>>(new Set())

    function toggleChecked(fieldId: number) {
        const next = new Set(checkedIds.value)
        if (!next.delete(fieldId)) next.add(fieldId)
        checkedIds.value = next
    }

    function clearChecked() {
        checkedIds.value = new Set()
    }

    /** Ticks every question the panel is currently showing, or unticks them when they all are. */
    function toggleAllChecked() {
        const shown = questions.value.map(field => field.id)
        checkedIds.value = shown.every(id => checkedIds.value.has(id)) ? new Set() : new Set(shown)
    }

    /**
     * Puts every ticked question to one audience, each at the end of that audience's form.
     *
     * <p>One request per question, because putting a question to somebody is what the server offers
     * and a handful of them is not worth an endpoint of its own. The list is read back once at the
     * end rather than after each, so the screen settles once.
     */
    async function assignCheckedTo(target: AssignmentTarget) {
        if (checkedIds.value.size === 0) return
        error.value = ''
        const role = target.role
        let position = role ? formFor(role).length : 0
        try {
            for (const fieldId of checkedIds.value) {
                position += 1
                await port.assign(fieldId, {...target, position})
            }
            clearChecked()
            await refresh()
        } catch {
            error.value = t('common.error')
            await refresh()
        }
    }

    /**
     * Makes one question wider or narrower on the form being arranged.
     *
     * <p>The form is one audience's, so this is that audience's override and no other form moves. The
     * width the question carries is its default, set where the question itself is written.
     */
    async function setPreviewWidth(field: ProfileField, width: string) {
        const assignment = allAssignments.value.find(a =>
            a.fieldId === field.id && a.targetKind === 'ROLE' && a.role === previewRole.value)
        if (!assignment) return
        await writeAssignment(field.id, {
            role: previewRole.value,
            position: assignment.position,
            widthOverride: width,
            readonlyOverride: assignment.readonlyOverride,
            requiredOverride: assignment.requiredOverride,
        })
    }

    /**
     * Writes a template's questions and puts them all to one audience.
     *
     * <p>A template is a set of questions somebody already decided belong together, so it names the
     * audience once rather than leaving a dozen new questions asked of nobody.
     */
    async function applyTemplate(
        template: {fields: Array<{name: string; fieldType: string; config: ProfileFieldConfig}>},
        role: string,
    ) {
        error.value = ''
        try {
            const startPosition = formFor(role).length
            for (const [i, f] of template.fields.entries()) {
                const created = await port.create(withTarget({
                    name: f.name,
                    fieldType: f.fieldType,
                    config: f.config,
                }))
                if (created?.id) {
                    await port.assign(created.id, {role, position: startPosition + i})
                }
            }
            await reload()
        } catch {
            error.value = t('common.error')
        }
    }

    return {
        allFields: allFields as Ref<ProfileField[]>,
        allAssignments: allAssignments as Ref<ProfileFieldAssignment[]>,
        availableGroups,
        availableStationGroups,
        selectedStationGroupId,
        questions,
        selectedFieldId,
        selectedField,
        audiences,
        unaskedRoles,
        unaskedGroups,
        askedOfNobody,
        previewRole,
        previewForm,
        previewFields,
        formFor,
        dateFields,
        birthDateField,
        showFieldModal,
        editingField,
        loading,
        error,
        reload,
        select,
        openAddField,
        openEditField,
        saveField,
        toggleFieldConfig,
        toggleKeepOnArchive,
        toggleRequired,
        toggleReadonly,
        addAudience,
        removeAudience,
        setAudience,
        setWritability,
        showDeleteModal,
        deleteTarget,
        requestDelete,
        confirmDelete,
        onReorder,
        setPreviewWidth,
        checkedIds,
        toggleChecked,
        toggleAllChecked,
        clearChecked,
        assignCheckedTo,
        applyTemplate,
    }
}
