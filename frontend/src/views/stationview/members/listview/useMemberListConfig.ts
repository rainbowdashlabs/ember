/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, onMounted, type ComputedRef} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRouter} from 'vue-router'
import type {StationMember} from '@/api/types'
import {
    useMemberData, getMemberFirstName, getMemberLastName,
    type MemberDataSource,
} from './useMemberData'
import {useSavedFilters} from './useSavedFilters'
import {useMemberListTabs} from './useMemberListTabs'
import {memberColumns, roleOf} from './memberColumns'
import {toCellValue} from '@/components/table/tableColumn'
import {useExport, type ExportColumn, type ExportFormatName} from '@/composables/useExport'
import {useDataTable} from '@/composables/useDataTable'
import {memberTable} from '@/api'
import type {MemberTableColumn} from '@/api/memberTable'
import {presentDocument} from '@/util/documentView'
import {useMemberFilter} from '@/composables/useMemberFilter'

/**
 * Where a member list's people come from and what may be done with them.
 *
 * <p>A station lists its own roll and reaches its own member screens. An association lists across the
 * stations it governs and reaches its own. The table, the filters, the column picker and the export
 * are the same in both, so the difference between them lives here rather than in the panels.
 */
export interface MemberListPort {
    source: MemberDataSource
    /** Where a click on a row lands. A route the port does not name is an action not offered. */
    routes: {detail?: string; edit?: string}
    canExport: ComputedRef<boolean>
    canEdit: ComputedRef<boolean>
    /** The name of the file an export produces, without an extension. */
    exportFileName: string
    /** Names the list where its chosen columns are remembered, one set per tab. */
    tableId: string
    /**
     * Whether groups and tags are columns at all. They are a station's own, so across the stations of
     * an association most rows would have nothing under either.
     */
    stationLocalColumns?: boolean
    /** Narrows the people further than the tabs and filters do, such as to one station. */
    keeps?: (member: StationMember) => boolean
}

/**
 * The member list, without its markup: loading, the scope tabs, the table with its search, column
 * filters and sort, the saved filters, the export and where a row leads.
 *
 * @param port where the people come from and what may be done with them
 */
export function useMemberListConfig(port: MemberListPort) {
    const {t} = useI18n()
    const router = useRouter()

    const {
        members, fields, assignments, allGroups, allTags,
        memberRolesMap, memberGroupsMap, memberTagsMap, memberManagers,
        loading, error, expandedId, overviewFields,
        getFieldValue, getFieldValueAsString, getMemberType, getMemberGroups, getMemberTags,
        toggleExpand, reload,
    } = useMemberData(port.source)

    const {activeTab, tabStates, currentTabState, tabs, tabScopedFields, isAskedOf} =
        useMemberListTabs(fields, assignments)

    const {savedFilters, loadSavedFilters, saveCurrentFilter, applyFilter, deleteFilter, clearFilters} =
        useSavedFilters(tabStates, activeTab)

    const {
        onFilter: onMemberFilter,
        applyFilter: applyMemberFilter,
    } = useMemberFilter(
        () => members.value,
        () => memberGroupsMap.value,
        () => memberTagsMap.value,
        () => allGroups.value,
        () => allTags.value,
    )

    const tabMembers = computed(() => {
        const onTab = activeTab.value === 'ALL'
            ? members.value
            : members.value.filter(m => getMemberType(m.id) === activeTab.value)
        const restricted = applyMemberFilter(onTab)
        return port.keeps ? restricted.filter(port.keeps) : restricted
    })

    /**
     * Every member's answers as cells, read once per load rather than on every sort and filter. A
     * question not put to a member has no entry at all, which tells it apart from one left open.
     */
    const answerCells = computed(() => new Map(members.value.map(member => {
        const role = roleOf(memberRolesMap.value.get(member.id) ?? [])
        const asked = fields.value.filter(field => isAskedOf(field.id, role))
        return [member.id, new Map(asked.map(field => [field.id, toCellValue(getFieldValue(member.id, field.id))]))]
    })))

    const columns = computed(() => memberColumns(tabScopedFields.value, {
        t,
        groupsOf: getMemberGroups,
        tagsOf: getMemberTags,
        answerOf: (memberId, fieldId) => answerCells.value.get(memberId)?.get(fieldId) ?? null,
        stationLocalColumns: port.stationLocalColumns ?? true,
    }))

    const table = useDataTable<StationMember>({
        id: () => `${port.tableId}:${activeTab.value}`,
        rows: tabMembers,
        columns,
        rowKey: member => member.id,
        state: currentTabState,
        searchText: member => member.email ?? '',
    })

    const exportColumns = computed((): ExportColumn<StationMember>[] => [
        {key: 'firstName', label: t('membersList.export.colFirstName'), value: getMemberFirstName},
        {key: 'lastName', label: t('membersList.export.colLastName'), value: getMemberLastName},
        {key: 'email', label: t('membersList.export.colEmail'), value: m => m.email ?? ''},
        {key: 'groups', label: t('membersList.export.colGroups'), value: m => getMemberGroups(m.id).join(', ')},
        ...tabScopedFields.value.map(f => ({
            key: `field:${f.id}`,
            label: f.name ?? '',
            value: (m: StationMember) => getFieldValueAsString(m.id, f.id),
        })),
    ])

    const exporting = useExport({
        rows: () => table.rows,
        rowId: m => m.id,
        columns: () => exportColumns.value,
        fileName: port.exportFileName,
        defaultColumns: ['firstName', 'lastName', 'email'],
    })

    /**
     * The chosen columns as the server names them, for the sheet it renders.
     *
     * <p>The rest of the export is built here from what the screen is holding, which cannot produce a
     * PDF and has no business deciding what a reader may see. So that one asks the server, naming the
     * people and the columns and letting it cut them down to what this reader may actually read.
     */
    function chosenServerColumns(): MemberTableColumn[] {
        const chosen: MemberTableColumn[] = [{kind: 'BUILTIN', key: 'name', fieldId: null}]
        for (const column of exportColumns.value) {
            if (!exporting.selectedColumns.value.has(column.key)) continue
            if (column.key === 'groups') chosen.push({kind: 'BUILTIN', key: 'groups', fieldId: null})
            if (column.key === 'email') chosen.push({kind: 'BUILTIN', key: 'email', fieldId: null})
            if (column.key.startsWith('field:')) {
                chosen.push({kind: 'PROFILE_FIELD', key: null, fieldId: Number(column.key.slice(6))})
            }
        }
        return chosen
    }

    /** Hands the export to whoever can make it: the sheet to the server, everything else to the screen. */
    async function performExport(format: ExportFormatName = 'csv') {
        if (format !== 'pdf') {
            exporting.performExport(format)
            return
        }
        const memberIds = exporting.selectedRows.value.map(member => member.id)
        const file = await memberTable.exportMemberTable(memberIds, chosenServerColumns(), 'pdf')
        presentDocument(file, `${port.exportFileName ?? 'export'}.pdf`)
        exporting.cancelExport()
    }

    /** Opens a person's screen, or does nothing where this reader has no such screen to open. */
    function navigateTo(routeName: string | undefined, member: StationMember, event: Event) {
        event.stopPropagation()
        if (!routeName) return
        router.push({name: routeName, params: {id: member.id}})
    }

    const navigateToDetail = (member: StationMember, event: Event) =>
        navigateTo(port.routes.detail, member, event)
    const navigateToEdit = (member: StationMember, event: Event) =>
        navigateTo(port.routes.edit, member, event)

    onMounted(() => {
        loadSavedFilters()
    })

    return {
        members, fields, allGroups, allTags,
        memberRolesMap, memberManagers,
        loading, error, expandedId, overviewFields,
        getFieldValue, toggleExpand, reload,
        activeTab, tabs, table, answerCells,
        isAskedOf,
        savedFilters, saveCurrentFilter, applyFilter, deleteFilter, clearFilters,
        onMemberFilter,
        exporting, performExport,
        canExport: port.canExport,
        canEdit: port.canEdit,
        navigateToDetail, navigateToEdit,
    }
}

export type MemberListConfig = ReturnType<typeof useMemberListConfig>
