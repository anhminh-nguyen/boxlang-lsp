package ortus.boxlang.lsp;

import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import ortus.boxlang.compiler.ast.BoxClass;
import ortus.boxlang.compiler.ast.BoxNode;
import ortus.boxlang.compiler.ast.comment.BoxMultiLineComment;
import ortus.boxlang.compiler.ast.statement.*;
import ortus.boxlang.lsp.workspace.FileParseResult;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BoxlangFoldingProvider {

	// Finds all folding ranges within a document
	public static List<FoldingRange> getFoldingRanges( URI documentURI ) {

		List<FoldingRange>			ranges			= new ArrayList<>();

		// Find the specific document the user is on right now
		Optional<FileParseResult>	parseResultOpt	= ProjectContextProvider.getInstance().getLatestFileParseResultPublic( documentURI );
		if ( parseResultOpt.isEmpty() )
			return ranges;

		FileParseResult		parseResult	= parseResultOpt.get();

		// Find the AST root of the document
		Optional<BoxNode>	rootOpt		= parseResult.findAstRoot();
		if ( rootOpt.isEmpty() )
			return ranges;

		BoxNode							root				= rootOpt.get();

		// Find all code folding types
		List<BoxFunctionDeclaration>	functions			= root.getDescendantsOfType( BoxFunctionDeclaration.class );
		List<BoxClass>					classes				= root.getDescendantsOfType( BoxClass.class );
		List<BoxIfElse>					ifElses				= root.getDescendantsOfType( BoxIfElse.class );
		List<BoxForIndex>				forIndex			= root.getDescendantsOfType( BoxForIndex.class );
		List<BoxForIn>					forIns				= root.getDescendantsOfType( BoxForIn.class );
		List<BoxWhile>					whiles				= root.getDescendantsOfType( BoxWhile.class );
		List<BoxMultiLineComment>		multiLineComments	= root.getDescendantsOfType( BoxMultiLineComment.class );

		// Add only nodes to ranges if they are multiline
		for ( BoxFunctionDeclaration node : functions ) {
			addMultilineRange( ranges, node );
		}
		for ( BoxClass node : classes ) {
			addMultilineRange( ranges, node );
		}
		for ( BoxIfElse node : ifElses ) {
			addMultilineRange( ranges, node );
		}
		for ( BoxForIndex node : forIndex ) {
			addMultilineRange( ranges, node );
		}
		for ( BoxForIn node : forIns ) {
			addMultilineRange( ranges, node );
		}
		for ( BoxWhile node : whiles ) {
			addMultilineRange( ranges, node );
		}
		for ( BoxMultiLineComment node : multiLineComments ) {

			if ( node.getPosition() == null )
				continue;

			FoldingRange commentRange = new FoldingRange();

			commentRange.setStartLine( node.getPosition().getStart().getLine() - 1 );
			commentRange.setEndLine( node.getPosition().getEnd().getLine() - 1 );
			commentRange.setKind( FoldingRangeKind.Comment );

			ranges.add( commentRange );
		}

		return ranges;
	}

	private static void addMultilineRange( List<FoldingRange> ranges, BoxNode node ) {
		if ( node.getPosition() == null )
			return;

		int	startLine	= node.getPosition().getStart().getLine() - 1;
		int	endLine		= node.getPosition().getEnd().getLine() - 1;

		if ( endLine > startLine ) {
			ranges.add( new FoldingRange( startLine, endLine ) );
		}
	}
}
