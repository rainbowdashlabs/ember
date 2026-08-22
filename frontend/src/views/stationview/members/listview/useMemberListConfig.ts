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
    useMemberData, memberDisplayName, getMemberFirstName, getMemberLastName,
    type MemberDataSource,
} from './useMemberData'
import {useSavedFilters, type MemberSortKey} from './useSavedFilters'
import {useMemberListTabs} from './useMemberListTabs'
import {useExport, type ExportColumn} from '@/composables/useExport'
import {byValue, useSortable} from '@/composables/useSortable'
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
}

/**
 * The member list, without its markup: loading, the scope tabs, the search, the column filters, the
 * sort, the saved filters, the export and where a row leads.
 *
 * @param port where the people come from and what may be done with them
 */
export function useMemberListConfig(port: MemberListPort) {
    const {t} = useI18n()
    const router = useRouter()

    const {
        members, fields, allGroups, allTags,
        memberRolesMap, memberGroupsMap, memberTagsMap, memberManagers,
        loading, error, expandedId, overviewFields,
        getFieldValue, getFieldValueAsString, getMemberType, getMemberGroups, getColumnValues,
        toggleExpand, reload,
    } = useMemberData(port.source)

    const {
        activeTab, tabStates, tabs,
        filterText, columnMultiFilters, columnEmptyFilters, sortKey, sortDirection,
        extraColumnIds, hiddenColumnIds,
        tabScopedFields, tabOverviewFields, tabNonOverviewFields, visibleColumns, toggleColumn,
        applyColumnFilter,
    } = useMemberListTabs(fields)

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

    const filteredMembers = computed(() => {
        let list = activeTab.value === 'ALL'
            ? members.value
            : members.value.filter(m => getMemberType(m.id) === activeTab.value)
        list = applyMemberFilter(list)

        const q = filterText.value.toLowerCase().trim()
        if (q) {
            list = list.filter(m => {
                if (memberDisplayName(m).toLowerCase().includes(q)) return true
                if ((m.email ?? '').toLowerCase().includes(q)) return true
                for (const f of overviewFields.value) {
                    if (getFieldValueAsString(m.id, f.id).toLowerCase().includes(q)) return true
                }
                return false
            })
        }
        for (const [key, selectedValues] of columnMultiFilters.value) {
            if (selectedValues.size === 0) continue
            const includeEmpty = columnEmptyFilters.value.has(key)
            list = list.filter(m => {
                const values = getColumnValues(m, key)
                if (values.length === 0 || values.every(v => !v)) return includeEmpty
                return values.some(v => selectedValues.has(v))
            })
        }
        for (const key of columnEmptyFilters.value) {
            if (columnMultiFilters.value.has(key) && (columnMultiFilters.value.get(key)?.size ?? 0) > 0) continue
            list = list.filter(m => {
                const values = getColumnValues(m, key)
                return values.length === 0 || values.every(v => !v)
            })
        }
        return list
    })

    const {sorted: sortedMembers, toggle: toggleSort} = useSortable<StationMember, MemberSortKey>({
        items: filteredMembers,
        initialKey: 'name',
        state: {key: sortKey, direction: sortDirection},
        comparators: key => key === 'name'
            ? byValue(memberDisplayName)
            : byValue(member => getFieldValueAsString(member.id, key)),
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

    const {
        exportMode, selectedIds, showExportModal, selectedColumns, columnOptions,
        toggleExportMode, toggleRow, toggleAllRows, toggleColumn: toggleExportColumn,
        selectColumns, openExportModal, performExport,
    } = useExport({
        rows: () => sortedMembers.value,
        rowId: m => m.id,
        columns: () => exportColumns.value,
        fileName: port.exportFileName,
        defaultColumns: ['firstName', 'lastName', 'email'],
    })

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
        memberRolesMap, memberGroupsMap, memberTagsMap, memberManagers,
        loading, error, expandedId, overviewFields,
        getFieldValue, getMemberType, toggleExpand, reload,
        activeTab, tabs, filterText, columnMultiFilters, columnEmptyFilters,
        sortKey, sortDirection, extraColumnIds, hiddenColumnIds,
        tabOverviewFields, tabNonOverviewFields, visibleColumns, toggleColumn, applyColumnFilter,
        savedFilters, saveCurrentFilter, applyFilter, deleteFilter, clearFilters,
        onMemberFilter, sortedMembers, toggleSort,
        exportMode, selectedIds, showExportModal, selectedColumns, columnOptions,
        toggleExportMode, toggleRow, toggleAllRows, toggleExportColumn,
        selectColumns, openExportModal, performExport,
        canExport: port.canExport,
        canEdit: port.canEdit,
        navigateToDetail, navigateToEdit,
    }
}
