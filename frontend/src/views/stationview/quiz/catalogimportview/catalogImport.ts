/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {CatalogMetadata, QuizCatalogExport, QuizCatalogExportCategory} from '@/api/quiz'
import type {ImportDraft} from '../csvimportview/quizCsvImport'

const FORMAT_VERSION = 1

export const NO_METADATA: CatalogMetadata = {language: null, source: null, author: null, license: null}

/**
 * Reads a picked catalog file. Only the shape is checked here: whether the questions inside it
 * hold together is the import's answer to give, because it is the one that can name every place
 * something is wrong at once.
 *
 * @throws SyntaxError when the file is not JSON at all
 */
export async function readCatalogFile(file: File): Promise<QuizCatalogExport> {
    const parsed = JSON.parse(await file.text())
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
        throw new SyntaxError('The file is not a catalog export')
    }
    return normalize(parsed as Record<string, unknown>)
}

/** Accepts the shape earlier versions exported, which kept the catalog's fields at the top level. */
function normalize(parsed: Record<string, unknown>): QuizCatalogExport {
    if (parsed.catalog) return parsed as unknown as QuizCatalogExport
    return {
        formatVersion: FORMAT_VERSION,
        catalog: {
            name: String(parsed.name ?? ''),
            description: String(parsed.description ?? ''),
            trainingEnabled: parsed.trainingEnabled === true,
            metadata: NO_METADATA,
        },
        categories: (parsed.categories ?? []) as QuizCatalogExport['categories'],
        questions: (parsed.questions ?? []) as QuizCatalogExport['questions'],
    }
}

/** The categories at least one of the questions going in actually refers to. */
export function usedCategories(
    categories: QuizCatalogExportCategory[],
    drafts: ImportDraft[],
): QuizCatalogExportCategory[] {
    const referenced = new Set(drafts.map(draft => draft.question.categoryKey).filter(Boolean))
    return categories.filter(category => referenced.has(category.key))
}

/**
 * Assembles what the wizard settled on into the file shape the import reads. Positions are
 * renumbered over the questions actually going in, so leaving one out closes the gap it left.
 */
export function toTransfer(
    catalog: {name: string; description: string; trainingEnabled: boolean; metadata: CatalogMetadata},
    categories: QuizCatalogExportCategory[],
    drafts: ImportDraft[],
): QuizCatalogExport {
    return {
        formatVersion: FORMAT_VERSION,
        catalog,
        categories,
        questions: drafts.map((draft, position) => ({...draft.question, position})),
    }
}
