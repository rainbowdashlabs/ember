/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.system.service;

import dev.chojo.ember.auth.PasswordHasher;
import dev.chojo.ember.feature.members.repository.MemberGroupSetRepository;
import dev.chojo.ember.feature.members.repository.StationFieldSetRepository;
import dev.chojo.ember.repository.RepositoryTestBase;

/** Tests of a demo seeder that runs on the demo stations with their members, seeded as the demo does. */
abstract class DemoTestBase extends RepositoryTestBase {
    /**
     * Seeds the demo stations and their members, as every per station seeder finds them.
     *
     * @return the run, holding the stations
     */
    protected static DemoRunContext seedDemoStations() {
        var run = new DemoRunContext(new PasswordHasher().hash(DemoSeeder.PASSWORD));
        new DemoStationSeeder(accountRepo, stationRepo).seed(run);
        new DemoMemberSeeder(
                        accountRepo,
                        stationMemberRepo,
                        memberLookupService,
                        memberGroupRepo,
                        new MemberGroupSetRepository(),
                        profileFieldRepo,
                        profileFieldChangeRepo,
                        userTagRepo,
                        stationRepo,
                        new StationFieldSetRepository())
                .seed(run);
        return run;
    }
}
