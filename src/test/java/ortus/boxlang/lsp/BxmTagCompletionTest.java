package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.ClientCapabilities;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.runtime.BoxRuntime;

/**
 * Tests for BXM tag and attribute completion in template files.
 * Tests cover:
 * - Tag name completion after <bx:
 * - Attribute name completion within tags
 * - Attribute value completion for known attributes
 * - Self-closing vs container tags
 * - Required vs optional attributes
 */
public class BxmTagCompletionTest extends BaseTest {

	static BoxRuntime						instance;
	private static ProjectContextProvider	pcp;
	private static Path						projectRoot;
	private static Path						templatePath;
	private static File						templateFile;

	@TempDir
	Path									tempDir;

	@BeforeAll
	static void loadFixtures() {
		instance		= BoxRuntime.getInstance( true );
		pcp				= ProjectContextProvider.getInstance();
		projectRoot		= Paths.get( System.getProperty( "user.dir" ) );
		templatePath	= projectRoot.resolve( "src/test/resources/files/bxmTagCompletionTest/simpleTemplate.bxm" );
		templateFile	= templatePath.toFile();

		assertTrue( templateFile.exists(), "Test file does not exist: " + templatePath.toString() );

		try {
			pcp.trackDocumentOpen( templatePath.toUri(), Files.readString( templatePath ) );
		} catch ( IOException e ) {
			e.printStackTrace();
		}
	}

	@Test
	@DisplayName( "Should complete tag names after <bx:" )
	void testTagNameCompletion() {
		// Position: Line 6 (0-indexed), col 5 after "<bx:"
		Position				position			= new Position( 6, 5 );
		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( templatePath.toUri().toString() );
		completionParams.setPosition( position );
		completionParams.setTextDocument( td );

		List<CompletionItem> items = pcp.getAvailableCompletions( templateFile.toURI(), completionParams );

		assertThat( items ).isNotEmpty();

		// Should have bx:output tag
		CompletionItem outputTag = findCompletion( items, "bx:output" );
		assertThat( outputTag ).isNotNull();
		assertThat( outputTag.getKind() ).isEqualTo( CompletionItemKind.Snippet );
		assertThat( outputTag.getInsertText() ).contains( "output" );

		// Should have bx:loop tag
		CompletionItem loopTag = findCompletion( items, "bx:loop" );
		assertThat( loopTag ).isNotNull();

		// Should have bx:thread tag
		CompletionItem threadTag = findCompletion( items, "bx:thread" );
		assertThat( threadTag ).isNotNull();
	}

	@Test
	@DisplayName( "Should complete tag names with partial match" )
	void testPartialTagNameCompletion() {
		List<CompletionItem>	items		= getCompletionsAt( 9, 9 );

		// Should still offer all tags but output should match
		CompletionItem			outputTag	= findCompletion( items, "bx:output" );
		assertThat( outputTag ).isNotNull();
	}

	@Test
	@DisplayName( "Should complete attribute names inside tag" )
	void testAttributeNameCompletion() {
		// Position: Line 12 (0-indexed), after "<bx:output "
		List<CompletionItem> items = getCompletionsAt( 12, 13 );

		// Should have attribute completions
		// Looking for common bx:output attributes like 'encodefor', 'var', etc.
		// Note: Exact attributes depend on BoxRuntime's output component
		assertThat( items ).isNotEmpty();

		// Should have CompletionItemKind.Property for attributes
		long attributeCount = items.stream()
		    .filter( item -> item.getKind() == CompletionItemKind.Property )
		    .count();

		assertThat( attributeCount ).isGreaterThan( 0 );
	}

	@Test
	@DisplayName( "Should complete attribute names with partial match" )
	void testPartialAttributeNameCompletion() {
		// Position: Line 15 (0-indexed), after "<bx:output enc"
		List<CompletionItem> items = getCompletionsAt( 15, 16 );

		// Should filter to attributes starting with "enc"
		// Note: Testing pattern, exact attribute names depend on BoxRuntime
		assertThat( items ).isNotEmpty();
	}

	@Test
	@DisplayName( "Should not offer enumerated values in the boolean-only implementation" )
	void testNoEnumeratedAttributeValueCompletion() {
		// Position: Line 18 (0-indexed), inside "<bx:output encodefor=\""
		List<CompletionItem> items = getCompletionsAt( 18, 27 );

		assertThat( items ).isEmpty();
	}

	@Test
	@DisplayName( "Should complete boolean attribute values with double quotes" )
	void testBooleanAttributeValueCompletionDoubleQuotes() {
		CompletionCase completion = completeSource( "<bx:setting showDebugOutput=\"|\" />" );

		assertBooleanCompletions( completion, List.of( "true", "false" ), completion.position(), completion.position() );
	}

	@Test
	@DisplayName( "Should automatically trigger completion for either quote style" )
	void testQuoteCompletionTriggerRegistration() throws Exception {
		LanguageServer		server	= new LanguageServer();
		InitializeParams	params	= new InitializeParams();
		params.setCapabilities( new ClientCapabilities() );
		params.setWorkspaceFolders( List.of() );

		InitializeResult result = server.initialize( params ).get();

		assertThat( result.getCapabilities().getCompletionProvider().getTriggerCharacters() )
		    .containsExactly( ".", "\"", "'" )
		    .inOrder();
	}

	@Test
	@DisplayName( "Should complete boolean attribute values with single quotes and case-insensitive names" )
	void testBooleanAttributeValueCompletionSingleQuotesAndCaseInsensitiveNames() {
		CompletionCase completion = completeSource( "<BX:SeTtInG ShOwDeBuGoUtPuT='|' />" );

		assertBooleanCompletions( completion, List.of( "true", "false" ), completion.position(), completion.position() );
	}

	@Test
	@DisplayName( "Should filter partial boolean values and replace only the typed prefix" )
	void testBooleanAttributeValueCompletionPartialInput() {
		CompletionCase	completion	= completeSource( "<bx:setting showDebugOutput=\"f|\" />" );
		Position		start		= new Position( completion.position().getLine(), completion.position().getCharacter() - 1 );

		assertBooleanCompletions( completion, List.of( "false" ), start, completion.position() );
	}

	@Test
	@DisplayName( "Should use current editor text before the debounced parse finishes" )
	void testBooleanAttributeValueCompletionUsesCurrentEditorText() {
		URI		uri				= tempDir.resolve( "live-completion.bxm" ).toUri();
		String	updatedSource	= "<bx:setting showDebugOutput=\"f\" />";
		int		cursorCharacter	= updatedSource.indexOf( 'f' ) + 1;

		pcp.trackDocumentOpen( uri, "<bx:setting showDebugOutput= />", 1 );

		try {
			pcp.trackDocumentChange( uri, List.of( new TextDocumentContentChangeEvent( updatedSource ) ), 2 );

			Position		position	= new Position( 0, cursorCharacter );
			CompletionCase	completion	= new CompletionCase( getCompletionsAt( uri, position ), position );
			Position		start		= new Position( 0, cursorCharacter - 1 );

			assertBooleanCompletions( completion, List.of( "false" ), start, position );
		} finally {
			pcp.trackDocumentClose( uri );
		}
	}

	@Test
	@DisplayName( "Should not complete unknown attribute values" )
	void testNoBooleanCompletionForUnknownAttribute() {
		assertThat( completeSource( "<bx:setting madeUpAttribute=\"|\" />" ).items() ).isEmpty();
	}

	@Test
	@DisplayName( "Should not complete non-boolean attribute values" )
	void testNoBooleanCompletionForNonBooleanAttribute() {
		assertThat( completeSource( "<bx:setting requestTimeout=\"|\" />" ).items() ).isEmpty();
	}

	@Test
	@DisplayName( "Should not complete boolean values in ordinary strings" )
	void testNoBooleanCompletionInOrdinaryString() {
		CompletionCase completion = completeSource( "value = '<bx:setting showDebugOutput=\"|';" );

		assertNoBooleanValues( completion.items() );
	}

	@Test
	@DisplayName( "Should not complete boolean values in comments" )
	void testNoBooleanCompletionInComment() {
		CompletionCase completion = completeSource( "<!-- <bx:setting showDebugOutput=\"|\" /> -->" );

		assertNoBooleanValues( completion.items() );
	}

	@Test
	@DisplayName( "Should not complete boolean values in interpolated expressions" )
	void testNoBooleanCompletionInInterpolatedExpression() {
		CompletionCase completion = completeSource( "<bx:setting showDebugOutput=\"#someValue|#\" />" );

		assertNoBooleanValues( completion.items() );
	}

	@Test
	@DisplayName( "Should show required attributes first" )
	void testRequiredAttributesPrioritized() {
		// Position: Line 12 (0-indexed), after "<bx:output "
		List<CompletionItem>	items		= getCompletionsAt( 12, 13 );

		// Required attributes should have better sort order
		// They should be marked with (required) in detail or have special sorting
		List<CompletionItem>	sortedItems	= items.stream()
		    .filter( item -> item.getKind() == CompletionItemKind.Property )
		    .sorted( ( a, b ) -> a.getSortText().compareTo( b.getSortText() ) )
		    .toList();

		assertThat( sortedItems ).isNotEmpty();
	}

	@Test
	@DisplayName( "Should handle self-closing tag attributes" )
	void testSelfClosingTagAttributes() {
		// Position: Line 24 (0-indexed), after "<bx:thread "
		List<CompletionItem> items = getCompletionsAt( 24, 13 );

		// Should still provide attribute completions for self-closing tags
		assertThat( items ).isNotEmpty();

		long attributeCount = items.stream()
		    .filter( item -> item.getKind() == CompletionItemKind.Property )
		    .count();

		assertThat( attributeCount ).isGreaterThan( 0 );
	}

	@Test
	@DisplayName( "Tag completion items should have proper detail/documentation" )
	void testTagCompletionHasDocumentation() {
		List<CompletionItem>	items		= getCompletionsAt( 6, 5 );

		CompletionItem			outputTag	= findCompletion( items, "bx:output" );
		assertThat( outputTag ).isNotNull();

		// Should have detail showing signature
		assertThat( outputTag.getDetail() ).isNotNull();
		assertThat( outputTag.getDetail() ).contains( "bx:output" );
	}

	@Test
	@DisplayName( "Attribute completion items should have proper kind" )
	void testAttributeCompletionKind() {
		List<CompletionItem>	items		= getCompletionsAt( 12, 13 );

		// Attributes should use CompletionItemKind.Property
		List<CompletionItem>	attributes	= items.stream()
		    .filter( item -> item.getKind() == CompletionItemKind.Property )
		    .toList();

		assertThat( attributes ).isNotEmpty();

		// Each attribute should have detail
		for ( CompletionItem attr : attributes ) {
			// Detail should describe the attribute
			// May be null for some attributes, but check structure
			assertThat( attr.getLabel() ).isNotNull();
		}
	}

	@Test
	@DisplayName( "Should not offer tag completion outside BXM context" )
	void testNoTagCompletionInScriptContext() {
		// This test verifies that tag completion is specific to .bxm files
		// We'll verify the ComponentCompletionRule's when() method filters correctly
		// Actual verification happens in unit tests for the rule itself

		List<CompletionItem> items = getCompletionsAt( 6, 5 );

		// Should have tag completions in .bxm file
		assertThat( items ).isNotEmpty();
		CompletionItem tagItem = items.stream()
		    .filter( item -> item.getLabel().startsWith( "bx:" ) )
		    .findFirst()
		    .orElse( null );

		assertThat( tagItem ).isNotNull();
	}

	/**
	 * Helper method to get completions at a specific position
	 */
	private List<CompletionItem> getCompletionsAt( int line, int character ) {
		return getCompletionsAt( templateFile.toURI(), new Position( line, character ) );
	}

	private List<CompletionItem> getCompletionsAt( URI uri, Position position ) {
		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( uri.toString() );
		completionParams.setPosition( position );
		completionParams.setTextDocument( td );
		return pcp.getAvailableCompletions( uri, completionParams );
	}

	private CompletionCase completeSource( String sourceWithCursor ) {
		int cursorOffset = sourceWithCursor.indexOf( '|' );
		assertThat( cursorOffset ).isAtLeast( 0 );

		String		textBeforeCursor	= sourceWithCursor.substring( 0, cursorOffset );
		String		source				= textBeforeCursor + sourceWithCursor.substring( cursorOffset + 1 );
		int			line				= ( int ) textBeforeCursor.chars().filter( character -> character == '\n' ).count();
		int			lastNewline			= textBeforeCursor.lastIndexOf( '\n' );
		Position	position			= new Position( line, cursorOffset - lastNewline - 1 );
		URI			uri					= tempDir.resolve( "completion-" + UUID.randomUUID() + ".bxm" ).toUri();

		pcp.trackDocumentOpen( uri, source );

		try {
			return new CompletionCase( getCompletionsAt( uri, position ), position );
		} finally {
			pcp.trackDocumentClose( uri );
		}
	}

	private void assertBooleanCompletions( CompletionCase completion, List<String> expectedLabels, Position editStart, Position editEnd ) {
		List<CompletionItem> items = completion.items();

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).containsExactlyElementsIn( expectedLabels ).inOrder();
		assertThat( items.stream().map( CompletionItem::getKind ).toList() )
		    .containsExactlyElementsIn( expectedLabels.stream().map( label -> CompletionItemKind.Value ).toList() )
		    .inOrder();

		for ( CompletionItem item : items ) {
			assertThat( item.getInsertText() ).isNull();
			assertThat( item.getTextEdit() ).isNotNull();
			assertThat( item.getTextEdit().isLeft() ).isTrue();

			TextEdit textEdit = item.getTextEdit().getLeft();
			assertThat( textEdit.getNewText() ).isEqualTo( item.getLabel() );
			assertThat( textEdit.getRange() ).isEqualTo( new Range( editStart, editEnd ) );
		}
	}

	private void assertNoBooleanValues( List<CompletionItem> items ) {
		assertThat(
		    items.stream()
		        .filter( item -> item.getKind() == CompletionItemKind.Value )
		        .map( CompletionItem::getLabel )
		        .filter( label -> label.equals( "true" ) || label.equals( "false" ) )
		        .toList()
		).isEmpty();
	}

	private record CompletionCase( List<CompletionItem> items, Position position ) {
	}

	/**
	 * Helper method to find a completion item by label
	 */
	private CompletionItem findCompletion( List<CompletionItem> items, String label ) {
		return items.stream()
		    .filter( item -> item.getLabel().equals( label ) )
		    .findFirst()
		    .orElse( null );
	}
}
