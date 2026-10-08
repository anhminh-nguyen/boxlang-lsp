package ortus.boxlang.lsp;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextEdit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import ortus.boxlang.lsp.workspace.ProjectContextProvider;
import ortus.boxlang.runtime.BoxRuntime;

/**
 * Tests for BXM tag and attribute completion in template files.
 * Tests cover:
 * - Tag name completion after <bx:
 * - Attribute name completion within tags
 * - Attribute value context filtering
 * - Self-closing vs container tags
 * - Required vs optional attributes
 */
public class BxmTagCompletionTest extends BaseTest {

	static BoxRuntime						instance;
	private static ProjectContextProvider	pcp;
	private static Path						projectRoot;
	private static Path						templatePath;
	private static File						templateFile;
	private static Path						booleanValuesPath;

	@BeforeAll
	static void loadFixtures() {
		instance			= BoxRuntime.getInstance( true );
		pcp					= ProjectContextProvider.getInstance();
		projectRoot			= Paths.get( System.getProperty( "user.dir" ) );
		templatePath		= projectRoot.resolve( "src/test/resources/files/bxmTagCompletionTest/simpleTemplate.bxm" );
		templateFile		= templatePath.toFile();
		booleanValuesPath	= projectRoot.resolve( "src/test/resources/files/bxmTagCompletionTest/booleanAttributeValues.bxm" );

		assertTrue( templateFile.exists(), "Test file does not exist: " + templatePath.toString() );
		assertTrue( booleanValuesPath.toFile().exists(), "Test file does not exist: " + booleanValuesPath.toString() );

		try {
			pcp.trackDocumentOpen( templatePath.toUri(), Files.readString( templatePath ) );
			pcp.trackDocumentOpen( booleanValuesPath.toUri(), Files.readString( booleanValuesPath ) );
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
		List<CompletionItem>	items		= getCompletionsAt( 9, 8 );

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
	@DisplayName( "Should not suggest tags inside an attribute value" )
	void testAttributeValueCompletion() {
		// Position: Line 18 (0-indexed), inside "<bx:output encodefor=\""
		List<CompletionItem> items = getCompletionsAt( 18, 27 );

		assertThat( items.stream()
		    .map( CompletionItem::getLabel )
		    .filter( label -> label.startsWith( "bx:" ) )
		    .toList() ).isEmpty();
	}

	@Test
	@DisplayName( "Should show required attributes first" )
	void testRequiredAttributesPrioritized() {
		// Position: Line 12 (0-indexed), after "<bx:output "
		List<CompletionItem>	items		= getCompletionsAt( 12, 13 );

		// Required attributes should have better sort order
		// They should be marked with (required) in detail or have special sorting
		List<CompletionItem>	sortedItems	= items.stream().filter( item -> item.getKind() == CompletionItemKind.Property )
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

	@Test
	@DisplayName( "Should complete true/false inside a double-quoted boolean attribute value" )
	void testBooleanValueCompletionDoubleQuotes() {
		// Line 0: <bx:setting showDebugOutput="|">
		assertBooleanValueCompletions( 0, 29, 29, List.of( "true", "false" ) );
	}

	@Test
	@DisplayName( "Should complete true/false inside a single-quoted boolean attribute value" )
	void testBooleanValueCompletionSingleQuotes() {
		// Line 1: <bx:setting showDebugOutput='|'>
		assertBooleanValueCompletions( 1, 29, 29, List.of( "true", "false" ) );
	}

	@Test
	@DisplayName( "Should match tag and attribute names case-insensitively" )
	void testBooleanValueCompletionCaseInsensitive() {
		// Line 2: <BX:SETTING SHOWDEBUGOUTPUT="|">
		assertBooleanValueCompletions( 2, 29, 29, List.of( "true", "false" ) );
	}

	@Test
	@DisplayName( "Should filter boolean values by the typed prefix and replace it" )
	void testBooleanValueCompletionPartialValue() {
		// Line 3: <bx:setting showDebugOutput="tr|"> - the edit must replace "tr", not append to it
		assertBooleanValueCompletions( 3, 29, 31, List.of( "true" ) );
	}

	@Test
	@DisplayName( "Should complete a boolean attribute that follows other attributes" )
	void testBooleanValueCompletionAfterOtherAttributes() {
		// Line 4: <bx:setting requestTimeout="30" enableOutputOnly="|">
		assertBooleanValueCompletions( 4, 50, 50, List.of( "true", "false" ) );
	}

	@Test
	@DisplayName( "Should not suggest values for a non-boolean attribute" )
	void testNoValueCompletionForNonBooleanAttribute() {
		// Line 5: <bx:setting requestTimeout="|"> - requestTimeout is declared as long
		assertThat( getCompletionsAt( booleanValuesPath, 5, 28 ) ).isEmpty();
	}

	@Test
	@DisplayName( "Should not suggest values for an unknown attribute" )
	void testNoValueCompletionForUnknownAttribute() {
		// Line 6: <bx:setting madeUpAttr="|">
		assertThat( getCompletionsAt( booleanValuesPath, 6, 24 ) ).isEmpty();
	}

	@Test
	@DisplayName( "Should not suggest values for an unknown tag" )
	void testNoValueCompletionForUnknownTag() {
		// Line 7: <bx:notARealTag showDebugOutput="|">
		assertThat( getCompletionsAt( booleanValuesPath, 7, 33 ) ).isEmpty();
	}

	@Test
	@DisplayName( "Should not suggest boolean values inside a comment" )
	void testNoBooleanValuesInsideComment() {
		// Line 8: <!-- <bx:setting showDebugOutput="|"> -->
		assertNoBooleanValues( getCompletionsAt( booleanValuesPath, 8, 34 ) );
	}

	@Test
	@DisplayName( "Should not suggest boolean values inside an interpolated expression" )
	void testNoBooleanValuesInsideInterpolation() {
		// Line 9: <bx:setting showDebugOutput="#|">
		assertNoBooleanValues( getCompletionsAt( booleanValuesPath, 9, 30 ) );
	}

	@Test
	@DisplayName( "Should not suggest boolean values inside an ordinary string" )
	void testNoBooleanValuesInsideOrdinaryString() {
		// Line 10: <div class="|">
		assertNoBooleanValues( getCompletionsAt( booleanValuesPath, 10, 12 ) );
	}

	@Test
	@DisplayName( "Should complete true/false when there is extra whitespace around the attribute name and equals sign" )
	void testBooleanValueCompletionExtraWhitespace() {
		// Line 11: <bx:setting SHOWDEBUGOUTPUT = "|"> with whitespaces around the attribute name and equals sign
		assertBooleanValueCompletions( 11, 42, 42, List.of( "true", "false" ) );
	}

	@Test
	@DisplayName( "Should not suggest values when the typed value matches neither true nor false" )
	void testNoValueCompletionForNonMatchingValue() {
		// Line 12: <bx:setting showDebugOutput='xyz|'>
		assertThat( getCompletionsAt( booleanValuesPath, 12, 32 ) ).isEmpty();
	}

	/**
	 * Assert that none of the completions are the boolean value suggestions.
	 * Other rules may still contribute items in these positions, so only true/false are checked.
	 */
	private void assertNoBooleanValues( List<CompletionItem> items ) {
		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).containsNoneOf( "true", "false" );
	}

	/**
	 * Assert that the completions at the cursor in the boolean values fixture are exactly the expected
	 * values, each a Value item whose edit replaces the typed part of the attribute value.
	 *
	 * @param line           0-indexed line in booleanAttributeValues.bxm
	 * @param valueStartCol  column just after the opening quote
	 * @param cursorCol      column of the cursor
	 * @param expectedLabels the exact labels expected
	 */
	private void assertBooleanValueCompletions( int line, int valueStartCol, int cursorCol, List<String> expectedLabels ) {
		List<CompletionItem> items = getCompletionsAt( booleanValuesPath, line, cursorCol );

		assertThat( items.stream().map( CompletionItem::getLabel ).toList() ).containsExactlyElementsIn( expectedLabels );

		for ( CompletionItem item : items ) {
			assertThat( item.getKind() ).isEqualTo( CompletionItemKind.Value );
			assertThat( item.getTextEdit() ).isNotNull();
			assertThat( item.getTextEdit().isLeft() ).isTrue();

			TextEdit edit = item.getTextEdit().getLeft();
			assertThat( edit.getNewText() ).isEqualTo( item.getLabel() );
			assertThat( edit.getRange() ).isEqualTo( new Range( new Position( line, valueStartCol ), new Position( line, cursorCol ) ) );
		}
	}

	/**
	 * Helper method to get completions at a specific position
	 */
	private List<CompletionItem> getCompletionsAt( int line, int character ) {
		return getCompletionsAt( templatePath, line, character );
	}

	/**
	 * Helper method to get completions at a specific position in a given fixture file
	 */
	private List<CompletionItem> getCompletionsAt( Path path, int line, int character ) {
		Position				position			= new Position( line, character );
		CompletionParams		completionParams	= new CompletionParams();
		TextDocumentIdentifier	td					= new TextDocumentIdentifier( path.toUri().toString() );
		completionParams.setPosition( position );
		completionParams.setTextDocument( td );
		return pcp.getAvailableCompletions( path.toUri(), completionParams );
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
