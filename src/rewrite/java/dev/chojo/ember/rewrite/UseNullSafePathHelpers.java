/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package dev.chojo.ember.rewrite;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Option;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;

import java.util.List;
import java.util.Objects;

/**
 * Replaces the two ways a path's missing name or parent turns into a {@link NullPointerException}
 * with the helpers of {@code FilePaths} that say what happens instead.
 *
 * <p>{@code path.getFileName().toString()} becomes {@code FilePaths.nameOf(path)}, which answers an
 * empty text for a root. {@code Files.createDirectories(file.getParent());} as a statement of its own
 * becomes {@code FilePaths.createParentDirectories(file);}, which creates nothing for a file without
 * a parent. Every path a listing or a {@code resolve} produces has both, so the rewrite changes no
 * behaviour there; it only stops the code from relying on what the types do not promise.
 */
public final class UseNullSafePathHelpers extends Recipe {

    private static final String FILE_PATHS = "dev.chojo.ember.util.FilePaths";

    private static final String FILE_PATHS_STUB = """
            package dev.chojo.ember.util;
            public final class FilePaths {
                public static String nameOf(java.nio.file.Path path) { return ""; }
                public static void createParentDirectories(java.nio.file.Path file) throws java.io.IOException {}
            }
            """;

    private static final MethodMatcher TO_STRING = new MethodMatcher("java.nio.file.Path toString()", true);
    private static final MethodMatcher FILE_NAME = new MethodMatcher("java.nio.file.Path getFileName()");
    private static final MethodMatcher PARENT = new MethodMatcher("java.nio.file.Path getParent()");
    private static final MethodMatcher CREATE_DIRECTORIES =
            new MethodMatcher("java.nio.file.Files createDirectories(java.nio.file.Path, ..)");

    @Option(
            displayName = "Packages",
            description =
                    "Comma separated packages to reach, each with the packages below it. Every package when left out.",
            example = "dev.chojo.ember.feature.inventory",
            required = false)
    private final @Nullable String packages;

    /**
     * Creates the recipe.
     *
     * @param packages comma separated packages to reach, or {@code null} for every package
     */
    @JsonCreator
    public UseNullSafePathHelpers(@JsonProperty("packages") @Nullable String packages) {
        this.packages = packages;
    }

    @Override
    public String getDisplayName() {
        return "Read a path's name and parent through FilePaths";
    }

    @Override
    public String getDescription() {
        return "Replaces `getFileName().toString()` and `Files.createDirectories(x.getParent())` with the null-safe helpers of `FilePaths`.";
    }

    /**
     * The packages option.
     *
     * @return the packages to reach, or {@code null} for every package
     */
    public @Nullable String getPackages() {
        return packages;
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        PackageFilter filter = PackageFilter.of(packages);
        JavaTemplate nameOf = template("FilePaths.nameOf(#{any(java.nio.file.Path)})");
        JavaTemplate createParents = template("FilePaths.createParentDirectories(#{any(java.nio.file.Path)})");
        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit unit, ExecutionContext ctx) {
                J.Package declaration = unit.getPackageDeclaration();
                if (declaration == null
                        || !MainSources.contains(unit)
                        || !filter.includesPackage(declaration.getPackageName())) {
                    return unit;
                }
                return super.visitCompilationUnit(unit, ctx);
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation invocation, ExecutionContext ctx) {
                J.MethodInvocation visited = super.visitMethodInvocation(invocation, ctx);
                if (TO_STRING.matches(visited)
                        && visited.getSelect() instanceof J.MethodInvocation select
                        && FILE_NAME.matches(select)
                        && select.getSelect() != null) {
                    maybeAddImport(FILE_PATHS);
                    return nameOf.apply(getCursor(), visited.getCoordinates().replace(), select.getSelect());
                }
                List<Expression> arguments = visited.getArguments();
                if (CREATE_DIRECTORIES.matches(visited)
                        && arguments.size() == 1
                        && arguments.getFirst() instanceof J.MethodInvocation argument
                        && PARENT.matches(argument)
                        && argument.getSelect() != null
                        && getCursor().getParentTreeCursor().getValue() instanceof J.Block) {
                    maybeAddImport(FILE_PATHS);
                    return createParents.apply(
                            getCursor(), visited.getCoordinates().replace(), argument.getSelect());
                }
                return visited;
            }
        };
    }

    private static JavaTemplate template(String code) {
        return JavaTemplate.builder(code)
                .imports(FILE_PATHS)
                .javaParser(JavaParser.fromJavaVersion().dependsOn(FILE_PATHS_STUB))
                .build();
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof UseNullSafePathHelpers that && Objects.equals(packages, that.packages);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(packages);
    }
}
