// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.codebehind.JavaEmitContext;
import org.jfxcore.compiler.diagnostic.Location;
import org.jfxcore.compiler.diagnostic.Logger;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.parse.EmbeddingContext;
import org.jfxcore.compiler.parse.FxmlParseAbortException;
import org.jfxcore.compiler.parse.FxmlParser;
import org.jfxcore.compiler.transform.Transformer;
import org.jfxcore.compiler.util.AbstractCompiler;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.CompilationScope;
import org.jfxcore.compiler.util.CompilationSource;
import org.jfxcore.compiler.util.CompilationUnit;
import org.jfxcore.compiler.util.CompilationUnitDescriptor;
import org.jfxcore.compiler.util.FileUtil;
import org.jfxcore.compiler.util.QualifiedName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings("unused")
public final class ClassGenerator extends AbstractCompiler {

	private final Logger logger;
	private final Map<Path, CompilationUnitInfo> compilationUnits = new HashMap<>();

	public ClassGenerator(Set<Path> searchPath, Logger logger) {
		super(searchPath);
		this.logger = Objects.requireNonNull(logger, "logger");
	}

	/**
	 * Adds an FXML markup file to the current generator and returns whether the file will be compiled.
	 *
	 * @param sourceRoot the base source directory (the root of the package namespace)
	 * @param sourceFile the FXML markup file, either as an absolute path or relative to {@code sourceRoot}
	 * @return {@code true} if the file will be compiled, {@code false} otherwise
	 * @throws IOException if an I/O error occurs
	 * @throws MarkupException if a markup error occurs
	 */
	public boolean addFileSource(Path sourceRoot, Path sourceFile) throws IOException {
		String sourceText = Files.readString(sourceRoot.resolve(sourceFile));
		return addSource(sourceRoot, sourceFile, sourceText, null);
	}

	/**
	 * Adds FXML markup text to the current generator.
	 *
	 * @param sourceRoot the base source directory (the root of the package namespace)
	 * @param sourceFile the source file path, either as an absolute path or relative to {@code sourceRoot}
	 * @param sourceText the FXML markup text
	 * @param additionalImports additional imports from the embedding environment
	 * @param embeddingHost the name of the embedding host class
	 * @param sourceOffset the source offset of the embedded markup in the embedding file
	 * @throws MarkupException if a markup error occurs
	 */
	public void addEmbeddedSource(Path sourceRoot,
								  Path sourceFile,
								  String sourceText,
								  List<String> additionalImports,
								  QualifiedName embeddingHost,
								  Location sourceOffset) {
		var embeddingContext = new EmbeddingContext(
			Objects.requireNonNull(additionalImports, "additionalImports"),
			Objects.requireNonNull(embeddingHost, "embeddingHost"),
			Objects.requireNonNull(sourceOffset, "sourceOffset"));

		if (!addSource(sourceRoot, sourceFile, sourceText, embeddingContext)) {
			throw new IllegalArgumentException("The specified markup text will not be compiled");
		}
	}

	private boolean addSource(Path sourceRoot, Path sourceFile, String sourceText, EmbeddingContext embeddingContext) {
		sourceRoot = Objects.requireNonNull(sourceRoot, "sourceRoot").normalize();
		sourceFile = Objects.requireNonNull(sourceFile, "sourceFile").normalize();
		sourceText = Objects.requireNonNull(sourceText, "sourceText");

		if (!sourceRoot.isAbsolute()) {
			throw new IllegalArgumentException("The source directory must be specified as an absolute path");
		}

		if (sourceFile.isAbsolute()) {
			try {
				sourceFile = sourceRoot.relativize(sourceFile);
			} catch (IllegalArgumentException ex) {
				throw new IllegalArgumentException("The specified file is not in the source directory");
			}
		}

		Path absoluteSourceFile = sourceRoot.resolve(sourceFile);

		if (!absoluteSourceFile.startsWith(sourceRoot)) {
			throw new IllegalArgumentException("The specified file is not in the source directory");
		}

		if (compilationUnits.containsKey(absoluteSourceFile)) {
			throw new IllegalArgumentException("The specified file was already added");
		}

		try {
			CompilationUnitInfo compilationUnit = parseSingleFile(sourceRoot, sourceFile, sourceText, embeddingContext);
			compilationUnits.put(absoluteSourceFile, compilationUnit);
			return true;
		} catch (FxmlParseAbortException ex) {
			logger.info(String.format("File skipped: %s (%s)", absoluteSourceFile, ex.getMessage()));
			return false;
		} catch (MarkupException ex) {
			ex.setSourceOffset(embeddingContext != null ? embeddingContext.sourceOffset() : new Location(0, 0));
			ex.setSourceFile(absoluteSourceFile.toFile());
			throw ex;
		}
	}

	/**
	 * Generates Java stub files for all FXML markup files that were added to this generator.
	 *
	 * @throws MarkupException if a markup error occurs
	 */
	public List<CompilationUnit> process() {
		if (compilationUnits.isEmpty()) {
			return List.of();
		}

		try {
			var transformer = Transformer.getCodeTransformer(newClassPool());
			var result = new ArrayList<CompilationUnit>();

			for (var entry : compilationUnits.entrySet()) {
				CompilationUnitDescriptor descriptor = entry.getValue().descriptor();
				var context = new CompilationContext(new CompilationSource.InMemory(descriptor.sourceText()));

				try (var ignored = new CompilationScope(context)) {
					ClassInfo classInfo = processSingleFile(entry.getValue(), transformer);
					result.add(new CompilationUnit(descriptor, classInfo.sourceText()));
				} catch (MarkupException ex) {
					ex.setSourceFile(descriptor.absoluteSourceFile().toFile());
					throw ex;
				}
			}

			return List.copyOf(result);
		} finally {
			compilationUnits.clear();
		}
	}

	private CompilationUnitInfo parseSingleFile(Path sourceRoot,
												Path sourceFile,
												String sourceText,
												EmbeddingContext embeddingContext) {
		CompilationContext context = new CompilationContext(new CompilationSource.InMemory(sourceText));

		try (var ignored = new CompilationScope(context)) {
			logger.fine("Parsing FXML markup file " + sourceRoot.resolve(sourceFile));

			var document = new FxmlParser(sourceFile, sourceText, embeddingContext).parseDocument();
			var markupBaseClass = QualifiedName.ofPath(FileUtil.getMarkupJavaFile(document));
			var descriptor = new CompilationUnitDescriptor(
				embeddingContext, markupBaseClass, sourceRoot, sourceFile, sourceText);

			return new CompilationUnitInfo(descriptor, document);
		} catch (MarkupException ex) {
			ex.setSourceOffset(embeddingContext != null ? embeddingContext.sourceOffset() : new Location(0, 0));
			ex.setSourceFile(sourceRoot.resolve(sourceFile).normalize().toFile());
			throw ex;
		}
	}

	private ClassInfo processSingleFile(CompilationUnitInfo compilationUnit, Transformer transformer) {
		DocumentNode document = compilationUnit.document();
		StringBuilder stringBuilder = new StringBuilder();
		JavaEmitContext context = new JavaEmitContext(stringBuilder);

		try {
			document = (DocumentNode)transformer.transform(document, null, null);
			context.emit(document);
		} catch (MarkupException ex) {
			EmbeddingContext embeddingContext = compilationUnit.descriptor().embeddingContext();
			ex.setSourceOffset(embeddingContext != null ? embeddingContext.sourceOffset() : new Location(0, 0));
			ex.setSourceFile(compilationUnit.descriptor().absoluteSourceFile().toFile());
			throw ex;
		}

		return new ClassInfo(compilationUnit.descriptor().markupClass(), stringBuilder.toString());
	}

	private record ClassInfo(QualifiedName markupBaseClass, String sourceText) {}

	private record CompilationUnitInfo(CompilationUnitDescriptor descriptor, DocumentNode document) {}
}
