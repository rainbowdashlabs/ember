/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {ai, quiz, util} from '@/api'
import {getItem} from '@/api/storage'
import {
    QuizQuestionTypes,
    type CatalogMetadata,
    type CatalogTransferProblem,
    type QuizCatalogExport,
    type QuizCatalogExportCategory,
} from '@/api/quiz'
import {createQuizCsvMapping, type ImportDraft} from '../csvimportview/quizCsvImport'
import {NO_METADATA, readCatalogFile, toTransfer, usedCategories} from './catalogImport'

export type ImportStep = 'source' | 'mapping' | 'preview' | 'done'

/**
 * Drives the import of a catalog file or a sheet, into a new catalog or an existing one.
 *
 * <p>A sheet is read by the backend into the same shape a catalog file has, which is why both
 * sources land in one preview and leave through one commit. Nothing is written until that commit,
 * so every correction made on screen is a correction to what will be created.
 */
export function useCatalogImport(catalogId: () => number | null) {
    const {t} = useI18n()

    const appending = computed(() => catalogId() !== null)

    const step = ref<ImportStep>('source')
    const loading = ref(false)
    const error = ref('')
    const problems = ref<CatalogTransferProblem[]>([])

    const file = ref<File | null>(null)
    const separator = ref(',')
    const headers = ref<string[]>([])
    const sheetText = ref('')
    const mapping = ref(createQuizCsvMapping([]))

    const drafts = ref<ImportDraft[]>([])
    const categories = ref<QuizCatalogExportCategory[]>([])

    const catalogName = ref('')
    const catalogDescription = ref('')
    const trainingEnabled = ref(false)
    const catalogMetadata = ref<CatalogMetadata>(NO_METADATA)

    const generateWrongAnswers = ref(false)
    const wrongAnswerCount = ref(3)
    const aiPrompt = ref('')
    const aiStatus = ref('')

    const hasAiKey = computed(() => !!getItem('ai_api_key'))
    const isSheet = computed(() => !!file.value && !file.value.name.toLowerCase().endsWith('.json'))
    const includedCount = computed(() => drafts.value.filter(draft => draft.included).length)

    async function loadCatalogName() {
        const id = catalogId()
        if (id === null) return
        catalogName.value = (await quiz.getCatalog(id)).name
    }

    function fail(cause: unknown) {
        const rejected = (cause as {response?: {data?: {problems?: CatalogTransferProblem[]}}})?.response?.data?.problems
        if (Array.isArray(rejected) && rejected.length > 0) {
            problems.value = rejected
            error.value = t('quiz.catalogs.importRejected')
        } else {
            error.value = cause instanceof SyntaxError ? t('quiz.catalogs.importNotReadable') : t('common.error')
        }
    }

    function selectFile(picked: File) {
        file.value = picked
        error.value = ''
        problems.value = []
    }

    function loadFromFile(transfer: QuizCatalogExport) {
        categories.value = transfer.categories ?? []
        drafts.value = (transfer.questions ?? []).map(question => ({
            question,
            rawAnswer: '',
            answerSeparator: ';',
            included: true,
        }))
        if (appending.value) return
        catalogName.value = transfer.catalog?.name ?? ''
        catalogDescription.value = transfer.catalog?.description ?? ''
        trainingEnabled.value = transfer.catalog?.trainingEnabled ?? false
        catalogMetadata.value = transfer.catalog?.metadata ?? NO_METADATA
    }

    async function advanceFromSource() {
        if (!file.value) return
        loading.value = true
        error.value = ''
        try {
            if (!isSheet.value) {
                loadFromFile(await readCatalogFile(file.value))
                step.value = 'preview'
                return
            }
            sheetText.value = await file.value.text()
            headers.value = (await util.parseCsv(file.value, separator.value)).headers
            mapping.value = {...createQuizCsvMapping(headers.value), separator: separator.value}
            step.value = 'mapping'
        } catch (cause) {
            fail(cause)
        } finally {
            loading.value = false
        }
    }

    async function advanceFromMapping() {
        if (!mapping.value.questionColumn) {
            error.value = t('quiz.csv.questionColumnRequired')
            return
        }
        loading.value = true
        error.value = ''
        try {
            const draft = await quiz.draftFromCsv(sheetText.value, mapping.value)
            categories.value = draft.categories
            drafts.value = draft.questions.map(entry => ({...entry, included: true}))
            if (!appending.value && !catalogName.value) {
                catalogName.value = file.value?.name.replace(/\.[^.]+$/, '') ?? ''
            }
            step.value = 'preview'
            await generateMissingAnswers()
        } catch (cause) {
            fail(cause)
        } finally {
            loading.value = false
        }
    }

    /**
     * Fills the wrong answers in before the preview rather than after the import, so what the model
     * invented is looked at and corrected on screen instead of landing unseen in the catalog.
     */
    async function generateMissingAnswers() {
        const apiKey = getItem('ai_api_key') ?? ''
        if (!generateWrongAnswers.value || !apiKey) return
        const catalogContext = t('quiz.csv.aiCatalogContext', {name: catalogName.value})
        const context = aiPrompt.value ? `${aiPrompt.value}\n${catalogContext}` : catalogContext
        const targets = drafts.value.filter(
            draft => draft.question.quizQuestionType === QuizQuestionTypes.MULTIPLE_CHOICE,
        )

        for (const [index, draft] of targets.entries()) {
            aiStatus.value = `${t('quiz.csv.generatingAnswers')} (${index + 1}/${targets.length})`
            try {
                const config = draft.question.config as {options?: {text: string; correct: boolean}[]}
                const options = config.options ?? []
                const wrongAnswers = await ai.generate({
                    provider: getItem('ai_provider') ?? 'openai',
                    apiKey,
                    model: getItem('ai_model') || null,
                    question: `${context}\n\n${draft.question.title}`,
                    correctAnswer: options
                        .filter(option => option.correct)
                        .map(option => option.text)
                        .join(', '),
                    count: wrongAnswerCount.value,
                })
                for (const wrong of wrongAnswers) options.push({text: wrong, correct: false})
                config.options = options
            } catch {
                continue
            }
        }
        aiStatus.value = ''
    }

    async function commit() {
        const included = drafts.value.filter(draft => draft.included)
        if (included.length === 0) {
            error.value = t('quiz.csv.noQuestionsSelected')
            return
        }
        if (!appending.value && !catalogName.value.trim()) {
            error.value = t('quiz.catalogs.nameRequired')
            return
        }
        loading.value = true
        error.value = ''
        problems.value = []
        try {
            const transfer = toTransfer(
                {
                    name: catalogName.value.trim(),
                    description: catalogDescription.value,
                    trainingEnabled: trainingEnabled.value,
                    metadata: catalogMetadata.value,
                },
                usedCategories(categories.value, included),
                included,
            )
            const id = catalogId()
            if (id !== null) await quiz.appendToCatalog(id, transfer)
            else await quiz.importCatalog(transfer)
            step.value = 'done'
        } catch (cause) {
            fail(cause)
        } finally {
            loading.value = false
        }
    }

    function goBack() {
        if (step.value === 'preview') step.value = isSheet.value ? 'mapping' : 'source'
        else if (step.value === 'mapping') step.value = 'source'
    }

    function startOver() {
        file.value = null
        drafts.value = []
        categories.value = []
        problems.value = []
        error.value = ''
        step.value = 'source'
    }

    return {
        appending,
        step,
        loading,
        error,
        problems,
        file,
        separator,
        headers,
        mapping,
        drafts,
        categories,
        catalogName,
        catalogDescription,
        trainingEnabled,
        generateWrongAnswers,
        wrongAnswerCount,
        aiPrompt,
        aiStatus,
        hasAiKey,
        isSheet,
        includedCount,
        loadCatalogName,
        selectFile,
        advanceFromSource,
        advanceFromMapping,
        commit,
        goBack,
        startOver,
    }
}
