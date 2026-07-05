/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.feature.quiz.service;

import de.chojo.sadu.core.configuration.DatabaseConfig;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.postgresql.mapper.PostgresqlMapper;
import de.chojo.sadu.queries.api.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import dev.chojo.ember.feature.media.service.ImageVariantService;
import dev.chojo.ember.feature.quiz.entity.QuizQuestionType;
import dev.chojo.ember.feature.quiz.repository.QuizCatalogRepository;
import dev.chojo.ember.feature.quiz.repository.QuizTestRepository;
import dev.chojo.ember.feature.restriction.RestrictionRepository;
import dev.chojo.ember.feature.station.repository.StationRepository;
import dev.chojo.ember.feature.storage.backend.StorageBackendResolver;
import dev.chojo.ember.feature.storage.backend.local.LocalStorageBackend;
import dev.chojo.ember.feature.storage.service.StorageService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("database")
@Testcontainers
class QuizPdfExportTest {
    private static final String SCHEMA = "ember";

    @Container
    static final PostgreSQLContainer PG = new PostgreSQLContainer("postgres:17")
            .withDatabaseName("ember_test")
            .withUsername("test")
            .withPassword("test")
            // Retry on the rootless-Docker random-host-port collision; a fresh attempt gets a
            // new port. See RepositoryTestBase for the full rationale.
            .withStartupAttempts(4);

    private static QuizCatalogRepository catalogRepo;
    private static QuizTestRepository testRepo;
    private static QuizService quizService;
    private static QuizPdfService pdfService;
    private static StationRepository stationRepo;

    @BeforeAll
    static void setup() throws Exception {
        DatabaseConfig dbConfig = new DatabaseConfig() {
            @Override
            public String host() {
                return PG.getHost();
            }

            @Override
            public String port() {
                return String.valueOf(PG.getFirstMappedPort());
            }

            @Override
            public String user() {
                return PG.getUsername();
            }

            @Override
            public String password() {
                return PG.getPassword();
            }

            @Override
            public String database() {
                return PG.getDatabaseName();
            }
        };

        var dataSource = DataSourceCreator.create(PostgreSql.get())
                .configure(c -> c.withConfig(dbConfig).currentSchema(SCHEMA).applicationName("QuizPdfTest"))
                .create()
                .withMaximumPoolSize(2)
                .build();

        SqlUpdater.builder(dataSource, PostgreSql.get())
                .setReplacements(new QueryReplacement("ember_schema", SCHEMA))
                .setSchemas(SCHEMA)
                .execute();

        var config = QueryConfiguration.builder(dataSource)
                .setThrowExceptions(true)
                .setRowMapperRegistry(new RowMapperRegistry().register(PostgresqlMapper.getDefaultMapper()))
                .build();
        QueryConfiguration.setDefault(config);

        stationRepo = new StationRepository();
        catalogRepo = new QuizCatalogRepository();
        testRepo = new QuizTestRepository();
        quizService = new QuizService(
                catalogRepo, testRepo, new RestrictionRepository(null, null, null), null, null, null, null);
        var backend = new LocalStorageBackend();
        var storage = new StorageService(new StorageBackendResolver(backend), backend);
        var imageService = new QuizQuestionImageService(new ImageVariantService(storage), stationRepo);
        pdfService = new QuizPdfService(testRepo, catalogRepo, imageService, quizService);
    }

    @Test
    void showcaseTestPdfExport() throws Exception {
        // Check if typst is available
        try {
            new ProcessBuilder("typst", "--version").start().waitFor();
        } catch (Exception e) {
            assumeTrue(false, "typst binary not available, skipping PDF test");
        }

        // Create station
        var station = stationRepo.create("PDF Test Station");

        // Create catalog with one question per type
        var catalog = catalogRepo.create(station.id(), "Showcase Catalog", "", false);
        var category = catalogRepo.createCategory(station.id(), "Allgemein", "", 0);

        // MULTIPLE_CHOICE
        catalogRepo.createQuestion(
                catalog.id(),
                category.id(),
                QuizQuestionType.MULTIPLE_CHOICE,
                "Welche Farbe hat ein Feuerwehrauto?",
                "",
                null,
                2,
                true,
                "{\"options\":[{\"text\":\"Rot\",\"correct\":true},{\"text\":\"Blau\",\"correct\":false},{\"text\":\"Grün\",\"correct\":false}]}",
                0);

        // TRUE_FALSE
        catalogRepo.createQuestion(
                catalog.id(),
                category.id(),
                QuizQuestionType.TRUE_FALSE,
                "Die Notrufnummer in Deutschland ist 112.",
                "",
                null,
                1,
                true,
                "{\"correctAnswer\":true}",
                1);

        // FILL_IN_THE_BLANK (without distractors)
        catalogRepo.createQuestion(
                catalog.id(),
                category.id(),
                QuizQuestionType.FILL_IN_THE_BLANK,
                "Die Feuerwehr löscht ___ und rettet ___.",
                "",
                null,
                2,
                true,
                "{\"text\":\"Die Feuerwehr löscht ___ und rettet ___.\",\"answers\":[\"Brände\",\"Menschen\"]}",
                2);

        // FILL_IN_THE_BLANK (with distractors)
        catalogRepo.createQuestion(
                catalog.id(),
                category.id(),
                QuizQuestionType.FILL_IN_THE_BLANK,
                "Setze die richtigen Wörter ein.",
                "",
                null,
                2,
                true,
                "{\"text\":\"Der ___ schützt den Kopf und die ___ schützen die Hände.\",\"answers\":[\"Helm\",\"Handschuhe\"],\"distractors\":[\"Stiefel\",\"Jacke\"]}",
                3);

        // CONNECT
        catalogRepo.createQuestion(
                catalog.id(),
                category.id(),
                QuizQuestionType.CONNECT,
                "Ordne die Begriffe zu",
                "",
                null,
                3,
                true,
                "{\"pairs\":[{\"left\":\"112\",\"right\":\"Feuerwehr\"},{\"left\":\"110\",\"right\":\"Polizei\"},{\"left\":\"116117\",\"right\":\"Ärztlicher Bereitschaftsdienst\"}]}",
                4);

        // ORDERING
        catalogRepo.createQuestion(
                catalog.id(),
                category.id(),
                QuizQuestionType.ORDERING,
                "Bringe die Schritte in die richtige Reihenfolge",
                "",
                null,
                2,
                true,
                "{\"items\":[\"Notruf wählen\",\"Lage schildern\",\"Auf Rückfragen warten\"]}",
                5);

        // FREE_ANSWER
        catalogRepo.createQuestion(
                catalog.id(),
                category.id(),
                QuizQuestionType.FREE_ANSWER,
                "Beschreibe die Aufgaben der Feuerwehr.",
                "",
                null,
                3,
                false,
                "{\"lines\":4,\"answers\":[\"Retten, Löschen, Bergen, Schützen\"]}",
                6);

        // IMAGE_TEXT
        catalogRepo.createQuestion(
                catalog.id(),
                category.id(),
                QuizQuestionType.IMAGE_TEXT,
                "Was siehst du auf dem Bild?",
                "Beschreibe das Fahrzeug.",
                null,
                2,
                false,
                "{\"answer\":\"Ein Feuerwehrauto\"}",
                7);

        // Create test with one section sourcing from the catalog
        var test = testRepo.create(station.id(), "Showcase PDF Test", "", 20, false, false, 0);
        var section = testRepo.createSection(test.id(), "Alle Fragetypen", "", 0);
        testRepo.createSource(section.id(), catalog.id(), category.id(), 8);

        // Activate the test (generates frozen questions)
        quizService.activateTest(test.id());

        // Export question PDF
        byte[] questionPdf = pdfService.exportQuestionPdf(test.id());
        assertNotNull(questionPdf);
        assertTrue(questionPdf.length > 100, "Question PDF should have content");
        // Check PDF magic bytes
        assertEquals('%', (char) questionPdf[0]);
        assertEquals('P', (char) questionPdf[1]);
        assertEquals('D', (char) questionPdf[2]);
        assertEquals('F', (char) questionPdf[3]);

        // Export solution PDF
        byte[] solutionPdf = pdfService.exportSolutionPdf(test.id());
        assertNotNull(solutionPdf);
        assertTrue(solutionPdf.length > 100, "Solution PDF should have content");
        assertEquals('%', (char) solutionPdf[0]);
    }
}
