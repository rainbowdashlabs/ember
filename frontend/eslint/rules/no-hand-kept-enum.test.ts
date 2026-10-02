/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import tsParser from '@typescript-eslint/parser'
import {RuleTester} from 'eslint'
import rule from './no-hand-kept-enum.mjs'
import {sourceFile} from './rule-tester'

/**
 * A hand-kept copy of a generated enum constant is reported, and so is the `...Name` alias of a
 * generated enum standing beside one. Constants the generator does not write, ordered lists typed by
 * a generated enum and the allowlisted copies are left alone.
 *
 * @vitest-environment node
 */
const tester = new RuleTester({languageOptions: {parser: tsParser}})

const module = sourceFile('api/things.ts')

tester.run('no-hand-kept-enum', rule, {
    valid: [
        {code: "import {FormStatus} from './generated/schema'\nconst status: FormStatus = FormStatus.OPEN", filename: module},
        {code: "const Writability = {OPEN: 'OPEN', LOCKED: 'LOCKED'} as const satisfies Record<string, string>", filename: module},
        {code: "const ORDER: readonly FormStatus[] = [FormStatus.DRAFT, FormStatus.OPEN, FormStatus.CLOSED]", filename: module},
        {code: "const FormStatus = {OPEN: 'OPEN'} as const", filename: module},
        {code: "type FormStatusName = components['schemas']['FormStatus']\nconst other = 1", filename: module},
        {
            code: "type PasskeyModeName = Schemas['Mode']\nconst PasskeyMode = {OFF: 'OFF'} as const satisfies Record<PasskeyModeName, PasskeyModeName>",
            filename: module,
        },
    ],
    invalid: [
        {
            code: "import type {FormStatus as FormStatusUnion} from './generated/schema'\nexport const FormStatus = {DRAFT: 'DRAFT', OPEN: 'OPEN', CLOSED: 'CLOSED'} as const satisfies Record<FormStatusUnion, FormStatusUnion>",
            filename: module,
            errors: [{messageId: 'copy', data: {name: 'FormStatus'}}],
        },
        {
            code: "export type FormStatusName = components['schemas']['FormStatus']\nexport const FormStatus = {DRAFT: 'DRAFT', OPEN: 'OPEN', CLOSED: 'CLOSED'} as const satisfies Record<FormStatusName, FormStatusName>",
            filename: module,
            errors: [
                {messageId: 'alias', data: {alias: 'FormStatusName', schema: 'FormStatus', name: 'FormStatus'}},
                {messageId: 'copy', data: {name: 'FormStatus'}},
            ],
        },
        {
            code: "type EventTypesName = Schemas['EventType']\nconst EventTypes = {EVENT: 'EVENT'} as const",
            filename: module,
            errors: [{messageId: 'alias', data: {alias: 'EventTypesName', schema: 'EventType', name: 'EventTypes'}}],
        },
    ],
})
