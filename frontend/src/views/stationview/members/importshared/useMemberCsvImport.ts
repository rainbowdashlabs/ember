/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, onMounted, ref, watch, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import client from '@/api/client'
import {profileFields as profileFieldsApi} from '@/api'
import type {ParsedCsv} from '@/api/util'
import type {ProfileField} from '@/api/profileFields'
import {useSession} from '@/composables/useSession'
import {useCsvImport} from '@/composables/useCsvImport'
import {createColumnMapping, SKIP_TARGET, type ColumnMapping, type PreviewResult} from './memberImport'

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
    const managerCount = options.managerCount ?? ref(0)

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
            const response = await client.post<PreviewResult>(options.previewPath, {csv: text, separator, mappings: mapping})
            return response.data
        },
        commit: async ({text, separator, mapping}) => {
            const response = await client.post<TResult>(options.importPath, {csv: text, separator, mappings: mapping})
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

    function needsValueMap(mapping: ColumnMapping): boolean {
        if (!mapping.target.startsWith('field:')) return false
        const fieldId = parseInt(mapping.target.substring(6))
        const field = fields.value.find(candidate => candidate.id === fieldId)
        return field?.fieldType === 'BOOLEAN' || field?.fieldType === 'ENUM'
    }

    async function loadFields() {
        fields.value = await profileFieldsApi.listFields()
    }

    onMounted(() => {
        if (loaded.value) loadFields()
    })

    watch(loaded, isLoaded => {
        if (isLoaded) loadFields()
    })

    return {importer, targetOptions, fieldScopeGroups, needsValueMap}
}
