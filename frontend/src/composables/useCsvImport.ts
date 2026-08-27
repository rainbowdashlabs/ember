/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, shallowRef, type ComputedRef, type Ref} from 'vue'
import {useI18n} from 'vue-i18n'
import type {ParsedCsv} from '@/api/util'

export const CsvImportSteps = {
    UPLOAD: 'upload',
    MAPPING: 'mapping',
    PREVIEW: 'preview',
    DONE: 'done',
} as const

export type CsvImportStepName = (typeof CsvImportSteps)[keyof typeof CsvImportSteps]

/** The picked file together with the parser settings chosen for it. */
export interface CsvSource {
    file: File | null
    text: string
    separator: string
}

/** A parsed source enriched with the mapping the user configured for it. */
export interface CsvMappedSource<TMapping> extends CsvSource {
    headers: string[]
    rows: string[][]
    mapping: TMapping
}

/**
 * Everything a flow has to supply to the shared import state machine. The target entity lives
 * entirely in these callbacks, so the composable never learns which entity is imported.
 */
export interface CsvImportOptions<TMapping, TPreview, TResult> {
    /** Turns the picked source into headers and rows, either locally or through the backend. */
    parse: (source: CsvSource) => Promise<ParsedCsv>
    /** Builds the initial mapping for the given headers, called with an empty list before the first parse. */
    createMapping: (headers: string[], source: CsvSource) => TMapping
    /** Performs the actual import and returns the flow specific result. */
    commit: (source: CsvMappedSource<TMapping>) => Promise<TResult>
    /** Optional preview between mapping and commit. Without it the mapping step commits directly. */
    loadPreview?: (source: CsvMappedSource<TMapping>) => Promise<TPreview>
    /** Blocks leaving the mapping step by returning a localized message. */
    validateMapping?: (source: CsvMappedSource<TMapping>) => string | null
    /** Blocks the commit by returning a localized message. */
    validateCommit?: (source: CsvMappedSource<TMapping>) => string | null
    formatError?: (error: unknown) => string
    separator?: string
    maxFileSize?: number
}

/** The entity agnostic part of the import state, consumed by {@link CsvImportWizard}. */
export interface CsvImportController {
    step: Ref<CsvImportStepName>
    loading: Ref<boolean>
    error: Ref<string>
    separator: Ref<string>
    fileName: ComputedRef<string>
    lineCount: ComputedRef<number>
    rowCount: ComputedRef<number>
    hasFile: ComputedRef<boolean>
    selectFile: (picked: File) => Promise<void>
    parse: () => Promise<void>
    showPreview: () => Promise<void>
    commit: () => Promise<void>
    goBack: () => void
    reset: () => void
}

const DEFAULT_MAX_FILE_SIZE = 2 * 1024 * 1024
const BYTES_PER_MEGABYTE = 1024 * 1024

/** Narrows the source file for flows that hand the raw file to the backend. */
export function requireCsvFile(file: File | null): File {
    if (!file) throw new Error('CSV import requires a picked file')
    return file
}

/**
 * Drives the shared file pick → column mapping → preview → result flow of every CSV import.
 * Parsing, mapping creation, preview and commit are injected, so a flow only describes its
 * target entity and its validation instead of branching on a feature flag.
 */
export function useCsvImport<TMapping, TPreview = unknown, TResult = unknown>(
    options: CsvImportOptions<TMapping, TPreview, TResult>,
) {
    const {t} = useI18n()

    const maxFileSize = options.maxFileSize ?? DEFAULT_MAX_FILE_SIZE

    const step = ref<CsvImportStepName>(CsvImportSteps.UPLOAD)
    const file = shallowRef<File | null>(null)
    const text = ref('')
    const separator = ref(options.separator ?? ';')
    const headers = ref<string[]>([])
    const rows = ref<string[][]>([])
    const preview = ref<TPreview | null>(null)
    const result = ref<TResult | null>(null)
    const loading = ref(false)
    const error = ref('')

    function source(): CsvSource {
        return {file: file.value, text: text.value, separator: separator.value}
    }

    const mapping = ref(options.createMapping([], source())) as unknown as Ref<TMapping>

    const fileName = computed(() => file.value?.name ?? '')
    const hasFile = computed(() => file.value !== null || text.value.length > 0)
    const rowCount = computed(() => rows.value.length)
    const lineCount = computed(() => {
        const lines = text.value.split('\n').filter(line => line.trim().length > 0)
        return Math.max(lines.length - 1, 0)
    })

    function mappedSource(): CsvMappedSource<TMapping> {
        return {...source(), headers: headers.value, rows: rows.value, mapping: mapping.value}
    }

    function fail(thrown: unknown) {
        const message = (thrown as {response?: {data?: {message?: string}}})?.response?.data?.message
        error.value = options.formatError?.(thrown) ?? message ?? t('common.error')
    }

    async function runStep(action: () => Promise<void>) {
        loading.value = true
        error.value = ''
        try {
            await action()
        } catch (thrown) {
            fail(thrown)
        } finally {
            loading.value = false
        }
    }

    function rejected(message: string | null | undefined): boolean {
        if (!message) return false
        error.value = message
        return true
    }

    function clearParsed() {
        headers.value = []
        rows.value = []
        mapping.value = options.createMapping([], source())
        preview.value = null
        result.value = null
    }

    async function selectFile(picked: File) {
        if (picked.size > maxFileSize) {
            error.value = t('csvImport.fileTooLarge', {size: Math.round(maxFileSize / BYTES_PER_MEGABYTE)})
            return
        }
        error.value = ''
        file.value = picked
        text.value = await picked.text()
        step.value = CsvImportSteps.UPLOAD
        clearParsed()
    }

    async function parse() {
        if (!hasFile.value || loading.value) return
        await runStep(async () => {
            const parsed = await options.parse(source())
            headers.value = parsed.headers
            rows.value = parsed.rows
            mapping.value = options.createMapping(parsed.headers, source())
            preview.value = null
            result.value = null
            step.value = CsvImportSteps.MAPPING
        })
    }

    async function showPreview() {
        if (loading.value) return
        if (rejected(options.validateMapping?.(mappedSource()))) return
        await runStep(async () => {
            preview.value = options.loadPreview ? await options.loadPreview(mappedSource()) : null
            step.value = CsvImportSteps.PREVIEW
        })
    }

    async function commit() {
        if (loading.value) return
        if (rejected(options.validateCommit?.(mappedSource()))) return
        await runStep(async () => {
            result.value = await options.commit(mappedSource())
            step.value = CsvImportSteps.DONE
        })
    }

    function goBack() {
        error.value = ''
        if (step.value === CsvImportSteps.PREVIEW) step.value = CsvImportSteps.MAPPING
        else if (step.value === CsvImportSteps.MAPPING) step.value = CsvImportSteps.UPLOAD
    }

    function reset() {
        step.value = CsvImportSteps.UPLOAD
        file.value = null
        text.value = ''
        error.value = ''
        clearParsed()
    }

    return {
        step,
        file,
        text,
        separator,
        headers,
        rows,
        rowCount,
        lineCount,
        fileName,
        hasFile,
        mapping,
        preview,
        result,
        loading,
        error,
        selectFile,
        parse,
        showPreview,
        commit,
        goBack,
        reset,
    }
}
