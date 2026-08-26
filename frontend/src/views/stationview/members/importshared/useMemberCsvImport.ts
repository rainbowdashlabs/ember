/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, onMounted, ref, watch, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import client from '@/api/client'
import {memberGroups as memberGroupsApi, profileFields as profileFieldsApi} from '@/api'
import type {ParsedCsv} from '@/api/util'
import {parseFieldConfig, type ProfileField} from '@/api/profileFields'
import type {MemberGroup} from '@/api/types'
import {useSession} from '@/composables/useSession'
import {useCsvImport} from '@/composables/useCsvImport'
import {createColumnMapping, SKIP_TARGET, type ColumnMapping, type PreviewResult} from './memberImport'

/** One answer a target allows: what is stored, and what the reader picks it by. */
export interface TargetValue {
    value: string
    label: string
}

export interface MemberCsvImportOptions {
    /** Backend route the mapped CSV is previewed against. */
    previewPath: string
    /** Backend route the mapped CSV is imported through. */
    importPath: string
    /** Scope profile fields fall back to when the backend does not provide one. */
    defaultScope: string
    /** Label of the option group holding the core fields of the imported entity. */
    primaryGroup: () => string
    /** Number of contact persons offered as mapping targets. Zero for flows without contacts. */
    managerCount?: Ref<number>
}

/**
 * Shared member CSV import wiring: parses through the member import endpoint, offers the profile
 * fields of the station as mapping targets and hands preview and import over to the flow specific
 * routes. Member and team imports differ only in those routes, their group label and whether
 * contact persons can be mapped.
 */
export function useMemberCsvImport<TResult>(options: MemberCsvImportOptions) {
    const {t} = useI18n()
    const {loaded} = useSession()

    const fields = ref<ProfileField[]>([])
    const groups = ref<MemberGroup[]>([])
    const managerCount = options.managerCount ?? ref(0)

    /**
     * The rows struck out in the preview, by where they came from in the file.
     *
     * <p>A list always holds somebody who does not belong in this import: the person who left, the
     * duplicate line, the row that is really a heading. Striking that one out beats editing the file
     * and starting again.
     */
    const ignoredRows = ref<number[]>([])

    async function toggleRow(row: number) {
        ignoredRows.value = ignoredRows.value.includes(row)
            ? ignoredRows.value.filter(candidate => candidate !== row)
            : [...ignoredRows.value, row]
        await importer.showPreview()
    }

    const importer = useCsvImport<ColumnMapping[], PreviewResult, TResult>({
        parse: async ({text, separator}) => {
            const [parsed, loadedFields] = await Promise.all([
                client.post<ParsedCsv>('/members/import/parse', {csv: text, separator}),
                profileFieldsApi.listFields(),
            ])
            fields.value = loadedFields
            return parsed.data
        },
        createMapping: headers => headers.map((header, index) => createColumnMapping(header, guessTarget(header), index)),
        loadPreview: async ({text, separator, mapping}) => {
            const response = await client.post<PreviewResult>(options.previewPath,
                {csv: text, separator, mappings: mapping, ignoredRows: ignoredRows.value})
            return response.data
        },
        commit: async ({text, separator, mapping}) => {
            const response = await client.post<TResult>(options.importPath,
                {csv: text, separator, mappings: mapping, ignoredRows: ignoredRows.value})
            return response.data
        },
    })

    const scopeLabels = computed<Record<string, string>>(() => ({
        MEMBER: t('memberImport.scopeMember'),
        GUARDIAN: t('memberImport.scopeMemberManager'),
        TEAM: t('memberImport.scopeTeam'),
        GROUP: t('memberImport.scopeGroup'),
    }))

    function scopeLabel(field: ProfileField): string {
        const scope = field.scope ?? options.defaultScope
        return scopeLabels.value[scope] ?? scope
    }

    const targetOptions = computed(() => {
        const group = options.primaryGroup()
        const result: {value: string; label: string; group?: string}[] = [
            {value: SKIP_TARGET, label: t('memberImport.targetSkip')},
            {value: 'firstName', label: t('memberImport.targetFirstName'), group},
            {value: 'lastName', label: t('memberImport.targetLastName'), group},
            {value: 'email', label: t('memberImport.targetEmail'), group},
            {value: 'group', label: t('memberImport.targetGroup'), group},
        ]
        for (let manager = 1; manager <= managerCount.value; manager++) {
            const managerGroup = t('memberImport.groupManager', {n: manager})
            result.push({value: `manager:${manager}:firstName`, label: t('memberImport.managerFirstName'), group: managerGroup})
            result.push({value: `manager:${manager}:lastName`, label: t('memberImport.managerLastName'), group: managerGroup})
            result.push({value: `manager:${manager}:phone`, label: t('memberImport.managerPhone'), group: managerGroup})
            result.push({value: `manager:${manager}:email`, label: t('memberImport.managerEmail'), group: managerGroup})
        }
        for (const field of fields.value) {
            result.push({value: `field:${field.id}`, label: `${field.name} (${field.fieldType})`, group: scopeLabel(field)})
        }
        return result
    })

    const fieldScopeGroups = computed(() => [...new Set(fields.value.map(scopeLabel))])

    function guessTarget(header: string): string {
        const name = header.toLowerCase().trim()
        if (name === 'vorname' || name === 'first name' || name === 'firstname') return 'firstName'
        if (name === 'name' || name === 'nachname' || name === 'last name' || name === 'lastname') return 'lastName'
        if (name === 'email' || name === 'e-mail') return 'email'
        if (name === 'gruppe' || name === 'group') return 'group'
        for (let manager = 1; manager <= managerCount.value; manager++) {
            if (name.includes(`kontakt ${manager}`) || name.includes(`contact ${manager}`)) return `manager:${manager}:firstName`
            if (name === `email ${manager}`) return `manager:${manager}:email`
        }
        const phoneDigit = name.match(/(?:telefon|phone).*?(\d)/)?.[1]
        if (phoneDigit) {
            const manager = parseInt(phoneDigit)
            if (manager >= 1 && manager <= managerCount.value) return `manager:${manager}:phone`
        }
        const field = fields.value.find(candidate => candidate.name?.toLowerCase() === name)
        return field ? `field:${field.id}` : SKIP_TARGET
    }

    /**
     * Whether a column is worth translating value by value.
     *
     * <p>Everything with a fixed set of answers is: a yes-or-no question, an enumeration, and the
     * group column, where the file says "JF 1" and the station calls it "Jugendfeuerwehr". Without
     * the group in that list, a file whose names differ from the station's silently created a second
     * set of groups beside the ones that were already there.
     */
    function needsValueMap(mapping: ColumnMapping): boolean {
        if (mapping.target === 'group') return true
        if (!mapping.target.startsWith('field:')) return false
        const fieldId = parseInt(mapping.target.substring(6))
        const field = fields.value.find(candidate => candidate.id === fieldId)
        return field?.fieldType === 'BOOLEAN' || field?.fieldType === 'ENUM'
    }

    /**
     * The answers a target allows, for the editor that says what a value in the file becomes.
     *
     * <p>A yes-or-no question allows exactly those two, an enumeration the options it was written
     * with, and a group column the groups the station keeps. Offering them beats typing them: a
     * target spelled even slightly differently matches no answer, and the value then arrives as it
     * stood in the file with nothing to say why. Everything else allows anything at all.
     */
    function valuesForTarget(target: string): TargetValue[] {
        if (target === 'group') {
            return groups.value
                .filter(group => !!group.name)
                .map(group => ({value: String(group.name), label: String(group.name)}))
        }
        if (!target.startsWith('field:')) return []
        const field = fields.value.find(candidate => candidate.id === parseInt(target.substring(6)))
        if (field?.fieldType === 'BOOLEAN') {
            return [
                {value: 'true', label: t('memberImport.valueYes')},
                {value: 'false', label: t('memberImport.valueNo')},
            ]
        }
        if (field?.fieldType !== 'ENUM') return []
        const options = parseFieldConfig(field.config).options
        return Array.isArray(options) ? options.map(option => ({value: String(option), label: String(option)})) : []
    }

    /** What a question is called, for the preview, which carries identifiers rather than names. */
    function fieldLabel(fieldId: string): string {
        const field = fields.value.find(candidate => String(candidate.id) === fieldId)
        return field?.name ?? `#${fieldId}`
    }

    async function loadFields() {
        const [loadedFields, loadedGroups] = await Promise.all([
            profileFieldsApi.listFields(),
            memberGroupsApi.listGroups(),
        ])
        fields.value = loadedFields
        groups.value = loadedGroups
    }

    onMounted(() => {
        if (loaded.value) loadFields()
    })

    watch(loaded, isLoaded => {
        if (isLoaded) loadFields()
    })

    return {importer, targetOptions, fieldScopeGroups, needsValueMap, valuesForTarget, ignoredRows, toggleRow, fieldLabel}
}
