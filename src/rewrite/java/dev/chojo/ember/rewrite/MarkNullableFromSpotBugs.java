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
import org.openrewrite.Tree;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.java.tree.TypeTree;
import org.openrewrite.marker.Markers;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Marks jspecify {@code @Nullable} where a SpotBugs report says a null arrives: on a method that
 * returns null, and on a parameter a caller passes null for.
 *
 * <p>Meant for the moment a package first carries SpotBugs' non-null defaults. Everything that was
 * quietly nullable is then reported, and most of it only needs the marker the code already
 * behaves by. Run SpotBugs, run this, run SpotBugs again: the second report holds the uses of the
 * newly marked values that are not checked, which is where the judgement is, and those are fixed
 * by hand. Whether a site should rather never be null is a judgement too, so the diff is read
 * before it is kept.
 *
 * <p>A record component is marked where the report names the record's canonical constructor or a
 * component's accessor. A type the marker cannot simply precede, such as a qualified name, is left
 * for hand, as is a file that imports another annotation called {@code Nullable}.
 */
public final class MarkNullableFromSpotBugs extends Recipe {

    private static final String NULLABLE = "org.jspecify.annotations.Nullable";

    @Option(
            displayName = "Report",
            description = "The SpotBugs XML report to read, as `be-spotbugs` writes it.",
            example = "build/reports/spotbugs/main.xml")
    private final String report;

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
     * @param report   the SpotBugs XML report
     * @param packages comma separated packages to reach, or {@code null} for every package
     */
    @JsonCreator
    public MarkNullableFromSpotBugs(
            @JsonProperty("report") String report, @JsonProperty("packages") @Nullable String packages) {
        this.report = report;
        this.packages = packages;
    }

    @Override
    public String getDisplayName() {
        return "Mark what SpotBugs finds null as nullable";
    }

    @Override
    public String getDescription() {
        return "Adds jspecify `@Nullable` to each method return and parameter a SpotBugs report shows a null arriving at.";
    }

    /**
     * The report option.
     *
     * @return the SpotBugs XML report
     */
    public String getReport() {
        return report;
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
        Set<SpotBugsNullSites.Site> sites = SpotBugsNullSites.read(Path.of(report)).stream()
                .filter(site -> filter.includesType(site.type()))
                .collect(Collectors.toSet());
        return new JavaIsoVisitor<>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit unit, ExecutionContext ctx) {
                boolean otherNullable = unit.getImports().stream()
                        .anyMatch(anImport ->
                                "Nullable".equals(anImport.getQualid().getSimpleName())
                                        && !NULLABLE.equals(anImport.getTypeName()));
                return otherNullable ? unit : super.visitCompilationUnit(unit, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration declaration, ExecutionContext ctx) {
                J.ClassDeclaration visited = super.visitClassDeclaration(declaration, ctx);
                List<Statement> components = visited.getPrimaryConstructor();
                JavaType.FullyQualified type = visited.getType();
                if (visited.getKind() != J.ClassDeclaration.Kind.Type.Record || components == null || type == null) {
                    return visited;
                }
                String typeName = type.getFullyQualifiedName();
                String canonical = components.stream()
                        .map(component -> component instanceof J.VariableDeclarations variable
                                ? Descriptors.of(variable.getType())
                                : "?")
                        .collect(Collectors.joining());
                List<Statement> marked = new ArrayList<>(components);
                boolean changed = false;
                for (int i = 0; i < components.size(); i++) {
                    if (!(components.get(i) instanceof J.VariableDeclarations component)) {
                        continue;
                    }
                    String accessor = component.getVariables().getFirst().getSimpleName();
                    boolean reported = sites.contains(new SpotBugsNullSites.Site(typeName, "<init>", canonical, i + 1))
                            || sites.contains(new SpotBugsNullSites.Site(typeName, accessor, "", 0));
                    if (reported) {
                        J.VariableDeclarations annotated = annotate(component);
                        if (annotated != component) {
                            marked.set(i, annotated);
                            changed = true;
                        }
                    }
                }
                if (!changed) {
                    return visited;
                }
                maybeAddImport(NULLABLE);
                return visited.withPrimaryConstructor(marked);
            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration method, ExecutionContext ctx) {
                J.MethodDeclaration visited = super.visitMethodDeclaration(method, ctx);
                JavaType.Method type = visited.getMethodType();
                if (type == null) {
                    return visited;
                }
                String typeName = type.getDeclaringType().getFullyQualifiedName();
                String name = visited.isConstructor() ? "<init>" : visited.getSimpleName();
                String parameters =
                        type.getParameterTypes().stream().map(Descriptors::of).collect(Collectors.joining());
                J.MethodDeclaration marked = visited;
                if (sites.contains(new SpotBugsNullSites.Site(typeName, name, parameters, 0))
                        && visited.getReturnTypeExpression() != null
                        && !hasNullable(visited.getLeadingAnnotations(), visited.getModifiers())) {
                    TypeTree annotated = annotate(visited.getReturnTypeExpression());
                    if (annotated != null) {
                        marked = marked.withReturnTypeExpression(annotated);
                    }
                }
                List<Statement> declared = visited.getParameters();
                List<Statement> annotatedParameters = new ArrayList<>(declared);
                boolean parameterMarked = false;
                for (int i = 0; i < declared.size(); i++) {
                    if (declared.get(i) instanceof J.VariableDeclarations parameter
                            && sites.contains(new SpotBugsNullSites.Site(typeName, name, parameters, i + 1))) {
                        J.VariableDeclarations annotated = annotate(parameter);
                        annotatedParameters.set(i, annotated);
                        parameterMarked |= annotated != parameter;
                    }
                }
                if (parameterMarked) {
                    marked = marked.withParameters(annotatedParameters);
                }
                if (marked != visited) {
                    maybeAddImport(NULLABLE);
                }
                return marked;
            }
        };
    }

    private static J.VariableDeclarations annotate(J.VariableDeclarations variable) {
        TypeTree type = variable.getTypeExpression();
        if (type == null || hasNullable(variable.getLeadingAnnotations(), variable.getModifiers())) {
            return variable;
        }
        TypeTree annotated = annotate(type);
        return annotated == null ? variable : variable.withTypeExpression(annotated);
    }

    private static boolean hasNullable(List<J.Annotation> leading, List<J.Modifier> modifiers) {
        return leading.stream().anyMatch(MarkNullableFromSpotBugs::isNullable)
                || modifiers.stream()
                        .flatMap(modifier -> modifier.getAnnotations().stream())
                        .anyMatch(MarkNullableFromSpotBugs::isNullable);
    }

    private static boolean isNullable(J.Annotation annotation) {
        return "Nullable".equals(annotation.getSimpleName());
    }

    /**
     * The type with {@code @Nullable} in front, or {@code null} where it cannot simply go in front.
     *
     * <p>jspecify's marker annotates the type, so it goes right before the simple type name, and on
     * an array before the brackets. A primitive cannot be null, and a qualified name would need the
     * marker between qualifier and name, which is left for hand.
     */
    private static @Nullable TypeTree annotate(TypeTree type) {
        if (type instanceof J.AnnotatedType annotated) {
            if (annotated.getAnnotations().stream().anyMatch(MarkNullableFromSpotBugs::isNullable)) {
                return null;
            }
            List<J.Annotation> annotations = new ArrayList<>(annotated.getAnnotations());
            annotations.add(nullable(Space.SINGLE_SPACE));
            return annotated.withAnnotations(annotations);
        }
        if (type instanceof J.ArrayType array) {
            List<J.Annotation> annotations =
                    array.getAnnotations() == null ? new ArrayList<>() : new ArrayList<>(array.getAnnotations());
            if (annotations.stream().anyMatch(MarkNullableFromSpotBugs::isNullable)) {
                return null;
            }
            annotations.add(nullable(Space.SINGLE_SPACE));
            return array.withAnnotations(annotations)
                    .withDimension(array.getDimension().withBefore(Space.SINGLE_SPACE));
        }
        boolean simple = type instanceof J.Identifier
                || (type instanceof J.ParameterizedType parameterized
                        && parameterized.getClazz() instanceof J.Identifier);
        if (!simple) {
            return null;
        }
        return new J.AnnotatedType(
                Tree.randomId(),
                type.getPrefix(),
                Markers.EMPTY,
                List.of(nullable(Space.EMPTY)),
                type.withPrefix(Space.SINGLE_SPACE));
    }

    private static J.Annotation nullable(Space prefix) {
        J.Identifier name = new J.Identifier(
                Tree.randomId(),
                Space.EMPTY,
                Markers.EMPTY,
                List.of(),
                "Nullable",
                JavaType.ShallowClass.build(NULLABLE),
                null);
        return new J.Annotation(Tree.randomId(), prefix, Markers.EMPTY, name, null);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof MarkNullableFromSpotBugs that
                && Objects.equals(report, that.report)
                && Objects.equals(packages, that.packages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(report, packages);
    }
}
