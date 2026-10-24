package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.FoldingRange;
import org.eclipse.lsp4j.FoldingRangeKind;
import org.eclipse.lsp4j.FoldingRangeRequestParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import ortus.boxlang.lsp.formatting.FormattingCapabilityCoordinator;
import ortus.boxlang.lsp.workspace.ProjectContextProvider;

class FoldingRangeTest extends BaseTest {

	@TempDir
	Path										tempDir;

	private final BoxLangTextDocumentService	service	= new BoxLangTextDocumentService();

	@Test
	void advertisesFoldingRangeSupport() throws Exception {
		LanguageServer		server	= new LanguageServer( new BoxLangWorkspaceService(), service,
		    mock( ProjectContextProvider.class ), mock( FormattingCapabilityCoordinator.class ) );
		InitializeParams	params	= new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of() );

		var capability = server.initialize( params ).get( 5, TimeUnit.SECONDS ).getCapabilities().getFoldingRangeProvider();

		assertThat( capability ).isNotNull();
		assertThat( capability.isRight() || Boolean.TRUE.equals( capability.getLeft() ) ).isTrue();
	}

	@Test
	void foldsClassFunctionsAndNestedBlocksWithoutCrossing() throws Exception {
		assertThat( folds( "Test.bx", """
		                              class {
		                                  function first() {
		                                      if ( ready ) {
		                                          return 1;
		                                      }
		                                  }
		                                  function second() {
		                                      return 2;
		                                  }
		                              }
		                              """ ) ).containsExactly( new FoldingRange( 0, 9 ), new FoldingRange( 1, 5 ), new FoldingRange( 2, 4 ),
		    new FoldingRange( 6, 8 ) );
	}

	@Test
	void startsFoldAtOpeningBrace() throws Exception {
		assertThat( folds( "test.bxs", "function example()\n{\n    return 1;\n}\n" ) )
		    .containsExactly( new FoldingRange( 1, 3 ) );
	}

	// A fold whose closing-brace line starts another fold ends one line early, so collapsing it keeps "} else {" visible.
	// Prevents the if statement block from getting a FoldingRange from 0-2, which would cause it to fold the else block in it.
	@Test
	void foldsIfAndElseBodiesSeparately() throws Exception {
		assertThat( folds( "test.bxs", """
		                               if ( ready ) {
		                                   writeOutput( 1 );
		                               } else {
		                                   writeOutput( 2 );
		                               }
		                               """ ) ).containsExactly( new FoldingRange( 0, 1 ), new FoldingRange( 2, 4 ) );
	}

	@Test
	void foldsElseIfChainWithoutSharingLines() throws Exception {
		assertThat( folds( "test.bxs", """
		                               if ( first ) {
		                                   writeOutput( 1 );
		                               } else if ( second ) {
		                                   writeOutput( 2 );
		                               } else {
		                                   writeOutput( 3 );
		                               }
		                               """ ) ).containsExactly( new FoldingRange( 0, 1 ), new FoldingRange( 2, 3 ), new FoldingRange( 4, 6 ) );
	}

	// This test excludes duplicate ranges, so that two FoldingRanges from 0-2 don't get added.
	@Test
	void collapsesDuplicateRangesFromBlocksSharingLines() throws Exception {
		assertThat( folds( "test.bxs", """
		                               if ( outer ) { if ( inner ) {
		                                   writeOutput( 1 );
		                               } }
		                               """ ) ).containsExactly( new FoldingRange( 0, 2 ) );
	}

	@ParameterizedTest
	@ValueSource( strings = { "for ( i = 0; i < 3; i++ )", "for ( item in items )", "while ( ready )" } )
	void foldsLoopBodies( String header ) throws Exception {
		assertThat( folds( "test.bxs", header + " {\n    writeOutput( 1 );\n}\n" ) )
		    .containsExactly( new FoldingRange( 0, 2 ) );
	}

	@Test
	void foldsBlockCommentAndIgnoresItsBraces() throws Exception {
		FoldingRange comment = new FoldingRange( 0, 2 );
		comment.setKind( FoldingRangeKind.Comment );

		assertThat( folds( "test.bxs", "/*\n * comment with unmatched {\n */\nvalue = 1;\n" ) )
		    .containsExactly( comment );
	}

	@Test
	void skipsSingleLineConstructs() throws Exception {
		assertThat( folds( "test.bxs", """
		                               function inline() { return 1; }
		                               if ( ready ) { writeOutput( 1 ); }
		                               for ( item in items ) { writeOutput( item ); }
		                               /* inline comment */
		                               """ ) ).isEmpty();
	}

	@Test
	void ignoresBracesInsideStringsAndLineComments() throws Exception {
		assertThat( folds( "test.bxs", """
		                               function example() {
		                                   var value = "{";
		                                   // } function fake() {
		                                   return '}';
		                               }
		                               """ ) ).containsExactly( new FoldingRange( 0, 4 ) );
	}

	@Test
	void incompleteFileDoesNotThrowOrInventClosingBrace() throws Exception {
		URI uri = Files.writeString( tempDir.resolve( "incomplete.bxs" ), "function broken() {\n    return 1;\n" ).toUri();
		try {
			assertThat( requestFolds( uri ) ).isEmpty();
		} finally {
			ProjectContextProvider.getInstance().remove( uri );
		}
	}

	private List<FoldingRange> folds( String filename, String source ) throws Exception {
		URI uri = Files.writeString( tempDir.resolve( filename ), source ).toUri();
		try {
			var parsed = ProjectContextProvider.getInstance().getLatestFileParseResultPublic( uri ).orElseThrow();
			assertThat( parsed.getIssues() ).isEmpty();
			assertThat( parsed.findAstRoot() ).isPresent();
			return requestFolds( uri );
		} finally {
			ProjectContextProvider.getInstance().remove( uri );
		}
	}

	private List<FoldingRange> requestFolds( URI uri ) throws Exception {
		FoldingRangeRequestParams params = new FoldingRangeRequestParams();
		params.setTextDocument( new TextDocumentIdentifier( uri.toString() ) );
		return service.foldingRange( params ).get( 5, TimeUnit.SECONDS );
	}
}
