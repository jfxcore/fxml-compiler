// Copyright (c) 2026, JFXcore. All rights reserved.
// Use of this source code is governed by the BSD-3-Clause license that can be found in the LICENSE file.

package org.jfxcore.compiler;

import javassist.CannotCompileException;
import javassist.NotFoundException;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.MethodInfo;
import org.jfxcore.compiler.ast.DocumentNode;
import org.jfxcore.compiler.ast.codebehind.ClassNode;
import org.jfxcore.compiler.ast.emit.BytecodeEmitContext;
import org.jfxcore.compiler.ast.emit.EmitInitializeRootNode;
import org.jfxcore.compiler.diagnostic.Logger;
import org.jfxcore.compiler.diagnostic.MarkupException;
import org.jfxcore.compiler.diagnostic.SourceInfo;
import org.jfxcore.compiler.diagnostic.errors.GeneralErrors;
import org.jfxcore.compiler.diagnostic.errors.SymbolResolutionErrors;
import org.jfxcore.compiler.parse.EmbeddingContext;
import org.jfxcore.compiler.parse.FxmlParser;
import org.jfxcore.compiler.transform.Transformer;
import org.jfxcore.compiler.type.TypeDeclaration;
import org.jfxcore.compiler.util.AbstractCompiler;
import org.jfxcore.compiler.util.Bytecode;
import org.jfxcore.compiler.util.CompilationContext;
import org.jfxcore.compiler.util.CompilationScope;
import org.jfxcore.compiler.util.CompilationSource;
import org.jfxcore.compiler.util.CompilationUnitDescriptor;
import org.jfxcore.compiler.util.FileUtil;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings("unused")
public final class MarkupCompiler extends AbstractCompiler {

	private final Logger logger;

	public MarkupCompiler(Set<Path> searchPath, Logger logger) {
		super(searchPath);
		this.logger = Objects.requireNonNull(logger, "logger");
	}

	/**
	 * Returns whether the specified class file was compiled by this compiler.
	 *
	 * @param classFile the FXML class file
	 * @return {@code true} if the FXML class file was compiled by this compiler, {@code false} otherwise
	 * @throws IOException if an I/O error occurs
	 */
	public boolean isCompiledFile(Path classFile) throws IOException {
		return FileUtil.hasGeneratorAttribute(classFile);
	}

	/**
	 * Compiles the content of the FXML compilation units.
	 *
	 * @throws IOException if an I/O error occurs
	 * @throws MarkupException if a markup error occurs
	 */
	public void compile(Set<CompilationUnitDescriptor> descriptors) throws IOException {
		if (descriptors.isEmpty()) {
			return;
		}

		var classPool = newClassPool();
		var codeTransformer = Transformer.getCodeTransformer(classPool);
		var bytecodeTransformer = Transformer.getBytecodeTransformer(classPool);

		for (CompilationUnitDescriptor descriptor : descriptors) {
			compileSingleFile(descriptor, codeTransformer, bytecodeTransformer);
		}
	}

	private void compileSingleFile(CompilationUnitDescriptor descriptor,
								   Transformer codeTransformer,
								   Transformer bytecodeTransformer) throws IOException {
		CompilationContext context = new CompilationContext(new CompilationSource.InMemory(descriptor.sourceText()));

		try (var ignored = new CompilationScope(context)) {
			var parser = new FxmlParser(descriptor.sourceFile(), descriptor.sourceText(), descriptor.embeddingContext());
			var document = parser.parseDocument();
			var codeDocument = (DocumentNode)codeTransformer.transform(document, null, null);
			ClassNode classNode = (ClassNode)codeDocument.getRoot();

			boolean hasCodeBehind = classNode.hasCodeBehind();
			String packageName = classNode.getPackageName();
			String codeBehindClassName = packageName + "." + classNode.getClassName();
			String simpleMarkupClassName = hasCodeBehind ? classNode.getMarkupClassName() : classNode.getClassName();
			String markupClassName = packageName + "." + simpleMarkupClassName;
			logger.fine(String.format("Compiling FXML class '%s'", markupClassName));

			URL classUrl = bytecodeTransformer.getClassPool().find(markupClassName);
			if (classUrl == null) {
				throw GeneralErrors.internalError(String.format("%s cannot be found", markupClassName));
			}

			var codeBehindClass = TypeDeclaration.of(bytecodeTransformer.getClassPool().get(codeBehindClassName));
			var markupClass = TypeDeclaration.of(bytecodeTransformer.getClassPool().get(markupClassName));

			if (markupClass.jvmType().getAttribute(FileUtil.GENERATOR_NAME) != null) {
				throw GeneralErrors.internalError(String.format(
					"FXML class '%s' has already been compiled, it cannot be compiled again", markupClassName));
			}

			markupClass.jvmType().defrost();

			Bytecode bytecode = new Bytecode(markupClass, 1);

			EmitInitializeRootNode rootNode = (EmitInitializeRootNode)bytecodeTransformer.transform(
				document, codeBehindClass, markupClass);

			BytecodeEmitContext emitContext = new BytecodeEmitContext(
				codeBehindClass, markupClass, rootNode, document.getImports(), bytecode);

			emitContext.emitRootNode();

			MethodInfo methodInfo = markupClass.jvmType().getClassFile().getMethod("initializeComponent");
			if (methodInfo == null) {
				throw GeneralErrors.internalError("Invalid markup class file");
			}

			// Add the generator attribute, which is used to detect if a class file was compiled by this compiler.
			markupClass.jvmType().getClassFile().addAttribute(new AttributeInfo(
				markupClass.jvmType().getClassFile().getConstPool(), FileUtil.GENERATOR_NAME, new byte[0]));

			methodInfo.setCodeAttribute(bytecode.toCodeAttribute());
			methodInfo.rebuildStackMap(markupClass.jvmType().getClassPool());

			Path outDir = Paths.get(classUrl.toURI());
			context.addModifiedClass(markupClass, outDir);
			emitContext.getNestedClasses().forEach(c -> context.addModifiedClass(c, outDir));

			flushModifiedClasses(context);
		} catch (MarkupException ex) {
			EmbeddingContext embeddingContext = descriptor.embeddingContext();
			ex.setSourceOffset(embeddingContext != null ? embeddingContext.sourceOffset() : null);
			ex.setSourceFile(descriptor.absoluteSourceFile().toFile());
			throw ex;
		} catch (BadBytecode | URISyntaxException | CannotCompileException ex) {
			MarkupException m = GeneralErrors.internalError(ex.getMessage());
			EmbeddingContext embeddingContext = descriptor.embeddingContext();
			m.setSourceOffset(embeddingContext != null ? embeddingContext.sourceOffset() : null);
			m.setSourceFile(descriptor.absoluteSourceFile().toFile());
			throw m;
		} catch (NotFoundException ex) {
			MarkupException m = SymbolResolutionErrors.classNotFound(SourceInfo.none(), ex.getMessage());
			EmbeddingContext embeddingContext = descriptor.embeddingContext();
			m.setSourceOffset(embeddingContext != null ? embeddingContext.sourceOffset() : null);
			m.setSourceFile(descriptor.absoluteSourceFile().toFile());
			throw m;
		}
	}
}
