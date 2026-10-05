package ortus.boxlang.lsp.workspace.completion;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import ortus.boxlang.lsp.workspace.rules.IRule;
import ortus.boxlang.runtime.BoxRuntime;
import ortus.boxlang.runtime.components.Attribute;
import ortus.boxlang.runtime.components.ComponentDescriptor;
import ortus.boxlang.runtime.validation.Validator;

/**
 * Provides completion for BXM tag attributes.
 * 
 * Triggers when inside a BXM tag after the tag name:
 * - <bx:output |
 * - <bx:thread action="|
 * 
 * Provides:
 * - Attribute names with type information
 * - Required attributes prioritized
 * - Attribute documentation
 */
public class BxmTagAttributeCompletionRule implements IRule<CompletionFacts, List<CompletionItem>> {

	@Override
	public boolean when( CompletionFacts facts ) {
		CompletionContextKind kind = facts.getContext().getKind();

		return kind == CompletionContextKind.BXM_TAG_ATTRIBUTE
		    || kind == CompletionContextKind.BXM_TAG_ATTRIBUTE_VALUE;
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {

		CompletionContext	context	= facts.getContext();

		// Get the tag name from the receiver text (stored in context)
		String				tagName	= context.getReceiverText();
		if ( tagName == null || tagName.isEmpty() ) {
			return;
		}

		// Get the component descriptor
		ComponentDescriptor descriptor = BoxRuntime.getInstance().getComponentService().getComponent( tagName );
		if ( descriptor == null ) {
			return;
		}

		// Add value completion for attribute value
		if ( context.getKind() == CompletionContextKind.BXM_TAG_ATTRIBUTE_VALUE ) {
			addAttributeValueCompletions( context, descriptor, result );
			return;
		}

		// Get already-used attributes to avoid suggesting them again
		String	lineText		= facts.readLine( facts.completionParams().getPosition().getLine() );
		var		usedAttributes	= extractUsedAttributes( lineText );

		// Add attribute completions
		Stream.of( descriptor.getComponent().getDeclaredAttributes() )
		    .filter( attr -> !usedAttributes.contains( attr.name().getName().toLowerCase() ) )
		    .forEach( attr -> result.add( createAttributeCompletion( attr ) ) );
	}

	/**
	 * Create a completion item for an attribute value.
	 */
	private void addAttributeValueCompletions(
	    CompletionContext context,
	    ComponentDescriptor descriptor,
	    List<CompletionItem> result ) {

		String attributeName = context.getAttributeName();

		if ( attributeName == null || attributeName.isEmpty() ) {
			return;
		}

		Attribute attribute = Stream.of(
		    descriptor.getComponent().getDeclaredAttributes()
		)
		    .filter( attr -> attr.name().getName().equalsIgnoreCase( attributeName ) )
		    .findFirst()
		    .orElse( null );

		if ( attribute == null ) {
			return;
		}

		if ( !"boolean".equalsIgnoreCase( attribute.type() ) ) {
			return;
		}

		String partialValue = context.getTriggerText().toLowerCase( Locale.ROOT );

		Stream.of( "true", "false" )
		    .filter( value -> value.startsWith( partialValue ) )
		    .map( value -> createValueCompletion( value, context ) )
		    .forEach( result::add );
	}

	/**
	 * Add value completions for a BXM tag attribute.
	 */
	private CompletionItem createValueCompletion( String value, CompletionContext context ) {
		CompletionItem	item			= new CompletionItem();
		Position		cursor			= context.getCursorPosition();
		int				valueStart		= Math.max( 0, cursor.getCharacter() - context.getTriggerText().length() );
		Range			replaceRange	= new Range(
		    new Position( cursor.getLine(), valueStart ),
		    new Position( cursor.getLine(), cursor.getCharacter() )
		);

		item.setLabel( value );
		item.setKind( CompletionItemKind.Value );
		item.setTextEdit( Either.forLeft( new TextEdit( replaceRange, value ) ) );

		return item;
	}

	@Override
	public boolean stop() {
		return true;
	}

	/**
	 * Create a completion item for a component attribute.
	 */
	private CompletionItem createAttributeCompletion( Attribute attr ) {
		CompletionItem	item			= new CompletionItem();
		String			attrName		= attr.name().toString();
		boolean			isRequired		= attr.validators().contains( Validator.REQUIRED );
		Object			defaultValue	= attr.defaultValue();

		item.setLabel( attrName );
		item.setKind( CompletionItemKind.Property );
		item.setInsertText( attrName + "=\"$1\"$0" );
		item.setInsertTextFormat( org.eclipse.lsp4j.InsertTextFormat.Snippet );

		// Build detail showing type and required status
		StringBuilder detail = new StringBuilder();
		if ( isRequired ) {
			detail.append( "(required) " );
		}
		if ( defaultValue != null && !defaultValue.toString().isEmpty() ) {
			detail.append( "default: " ).append( defaultValue );
		}

		if ( detail.length() > 0 ) {
			item.setDetail( detail.toString() );
		}

		// Sort required attributes first
		String sortPrefix = isRequired ? "0" : "1";
		item.setSortText( sortPrefix + attrName );

		return item;
	}

	/**
	 * Extract already-used attribute names from the line text.
	 * Simple regex-based extraction looking for attribute="value" patterns.
	 */
	private java.util.Set<String> extractUsedAttributes( String lineText ) {
		java.util.Set<String>	used	= new java.util.HashSet<>();
		java.util.regex.Pattern	pattern	= java.util.regex.Pattern.compile( "\\b([\\w\\-]+)\\s*=\\s*[\"']" );
		java.util.regex.Matcher	matcher	= pattern.matcher( lineText );

		while ( matcher.find() ) {
			used.add( matcher.group( 1 ).toLowerCase() );
		}

		return used;
	}
}
