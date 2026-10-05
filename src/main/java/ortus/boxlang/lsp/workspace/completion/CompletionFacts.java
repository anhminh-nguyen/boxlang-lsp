package ortus.boxlang.lsp.workspace.completion;

import org.eclipse.lsp4j.CompletionParams;

import ortus.boxlang.lsp.workspace.DocumentModel;
import ortus.boxlang.lsp.workspace.FileParseResult;

/**
 * Facts about a completion request, used by completion rules to determine
 * what completions to provide.
 */
public record CompletionFacts( FileParseResult fileParseResult, CompletionParams completionParams, DocumentModel documentModel ) {

	public CompletionFacts( FileParseResult fileParseResult, CompletionParams completionParams ) {
		this( fileParseResult, completionParams, null );
	}

	/**
	 * Get the analyzed completion context for this request.
	 * The context determines what kind of completion is appropriate
	 * (member access, import, new expression, etc.)
	 *
	 * @return The analyzed CompletionContext
	 */
	public CompletionContext getContext() {
		return CompletionContext.analyze( fileParseResult, completionParams, readLine( completionParams.getPosition().getLine() ) );
	}

	/**
	 * Read the current editor line when the document is open, falling back to the
	 * latest parsed snapshot for unopened documents.
	 *
	 * @param lineNumber zero-based line number
	 *
	 * @return current line text
	 */
	public String readLine( int lineNumber ) {
		if ( documentModel != null ) {
			return documentModel.getLine( lineNumber );
		}

		return fileParseResult.readLine( lineNumber );
	}
}
