package fr.lacaleche.glue.gametest.internal.discovery;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Indexes Java source types using javac's type model, without loading or initializing game classes.
 * Intentionally non-incremental: discovery includes unannotated types, so every compilation must
 * rebuild the complete index rather than retaining deleted or renamed tests.
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public final class ClientTestProcessor extends AbstractProcessor {

    private static final String FABRIC_TEST = "net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest";
    private static final String SPEC = "fr.lacaleche.glue.gametest.ClientTestSpec";
    static final String INDEX = "META-INF/glue/client-tests.tsv";

    private final Map<String, IndexedTest> tests = new TreeMap<>();
    private boolean invalid;
    private boolean written;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        if (!round.processingOver()) {
            TypeElement contract = this.processingEnv.getElementUtils().getTypeElement(FABRIC_TEST);
            for (TypeElement root : ElementFilter.typesIn(round.getRootElements())) this.visit(root, contract);
        } else if (!this.written && !this.invalid && !round.errorRaised()) {
            this.written = true;
            this.writeIndex();
        }
        return false;
    }

    private void visit(TypeElement type, TypeElement contract) {
        AnnotationMirror spec = type.getAnnotationMirrors().stream()
                .filter(annotation -> annotation.getAnnotationType().toString().equals(SPEC))
                .findFirst().orElse(null);
        boolean implementation = contract != null && type.getKind() == ElementKind.CLASS
                && this.processingEnv.getTypeUtils().isAssignable(type.asType(), contract.asType());
        boolean concrete = implementation && !type.getModifiers().contains(Modifier.ABSTRACT);
        if (spec != null && !concrete) {
            this.fail(type, "@ClientTestSpec requires a concrete FabricClientGameTest implementation");
        } else if (concrete) {
            this.index(type, spec);
        }
        for (TypeElement nested : ElementFilter.typesIn(type.getEnclosedElements())) this.visit(nested, contract);
    }

    private void index(TypeElement type, AnnotationMirror spec) {
        for (Element owner = type; owner instanceof TypeElement; owner = owner.getEnclosingElement()) {
            if (!owner.getModifiers().contains(Modifier.PUBLIC)) {
                this.fail(type, "Discovered client tests and their enclosing classes must be public");
                return;
            }
        }
        if (type.getEnclosingElement() instanceof TypeElement && !type.getModifiers().contains(Modifier.STATIC)) {
            this.fail(type, "A nested client test must be static so Fabric can instantiate it");
            return;
        }
        boolean constructor = ElementFilter.constructorsIn(type.getEnclosedElements()).stream()
                .anyMatch(method -> method.getParameters().isEmpty() && method.getModifiers().contains(Modifier.PUBLIC));
        if (!constructor) {
            this.fail(type, "A client test requires a public no-argument constructor");
            return;
        }

        String name = "";
        boolean explicitOnly = false;
        if (spec != null) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry
                    : this.processingEnv.getElementUtils().getElementValuesWithDefaults(spec).entrySet()) {
                switch (entry.getKey().getSimpleName().toString()) {
                    case "value" -> name = (String) entry.getValue().getValue();
                    case "explicitOnly" -> explicitOnly = (Boolean) entry.getValue().getValue();
                    default -> { }
                }
            }
        }
        if (name.isEmpty()) {
            name = type.getSimpleName().toString().replaceFirst("(?:ClientTest|GameTest|Test)$", "")
                    .replaceAll("([A-Z]+)([A-Z][a-z])", "$1-$2")
                    .replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
        }
        if (!name.matches("[a-z0-9][a-z0-9_.:-]*") || name.equals("auto") || name.equals("all")) {
            this.fail(type, "Invalid client test name '" + name + "'; choose a lowercase @ClientTestSpec name other than auto/all");
            return;
        }
        String binaryName = this.processingEnv.getElementUtils().getBinaryName(type).toString();
        this.tests.put(binaryName, new IndexedTest(name, binaryName, explicitOnly, type));
    }

    private void writeIndex() {
        Element[] origins = this.tests.values().stream().map(IndexedTest::type).toArray(Element[]::new);
        try (Writer writer = this.processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", INDEX, origins).openWriter()) {
            writer.write("# name\tbinaryName\texplicitOnly\n");
            for (IndexedTest test : this.tests.values()) {
                writer.write(test.name() + "\t" + test.binaryName() + "\t" + test.explicitOnly() + "\n");
            }
        } catch (IOException exception) {
            this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Could not write client test index: " + exception);
        }
    }

    private void fail(TypeElement type, String message) {
        this.invalid = true;
        this.processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message, type);
    }

    private record IndexedTest(String name, String binaryName, boolean explicitOnly, TypeElement type) {
    }
}
