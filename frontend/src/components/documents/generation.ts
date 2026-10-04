/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {memberGroups, userTags} from '@/api'
import type {MemberGroup, UserTag} from '@/api/generated/schema'

/** The groups and tags of the station an audience of a template is chosen from. */
export interface AudienceLists {
    groups: MemberGroup[]
    tags: UserTag[]
}

/**
 * The station's groups and tags, each left empty where the reader may not read it: they only make
 * choosing an audience easier and are no reason to keep anybody from working with a template.
 */
export async function audienceLists(): Promise<AudienceLists> {
    const [groups, tags] = await Promise.all([
        memberGroups.listGroups().catch(() => []),
        userTags.listTags().catch(() => []),
    ])
    return {groups, tags}
}

/** The data a document still lacks, named as the catalogue names it, for one sentence. */
export function missingLabels(missing: readonly {label: string}[]): string {
    return missing.map(value => value.label).join(', ')
}

/**
 * An issuer with what they do, as a document prints it.
 *
 * @returns the name and the function, the name alone where no function is given, empty without a name
 */
export function issuerLine(name: string | null | undefined, issuerFunction: string | null | undefined): string {
    if (!name) return ''
    return issuerFunction ? `${name}, ${issuerFunction}` : name
}
