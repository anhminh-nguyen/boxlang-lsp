package ortus.boxlang.lsp.workspace.completion;

import java.util.List;
import java.util.stream.Stream;

import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;

import org.eclipse.lsp4j.InsertTextFormat;
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
		CompletionContextKind completionContextKind = facts.getContext().getKind();

		return completionContextKind == CompletionContextKind.BXM_TAG_ATTRIBUTE ||
		    completionContextKind == CompletionContextKind.BXM_TAG_ATTRIBUTE_VALUE;
	}

	@Override
	public void then( CompletionFacts facts, List<CompletionItem> result ) {
		// Get the tag name from the receiver text (stored in context)
		String tagName = facts.getContext().getReceiverText();
		if ( tagName == null || tagName.isEmpty() ) {
			return;
		}

		// Get the component descriptor
		ComponentDescriptor descriptor = BoxRuntime.getInstance().getComponentService().getComponent( tagName );
		if ( descriptor == null ) {
			return;
		}

		CompletionContext		context	= facts.getContext();
		CompletionContextKind	kind	= context.getKind();

		// Run completion logic based on type of tag attribute autocompletion
		if ( kind == CompletionContextKind.BXM_TAG_ATTRIBUTE ) {

			// Get already-used attributes to avoid suggesting them again
			String	lineText		= facts.fileParseResult().readLine( facts.completionParams().getPosition().getLine() );
			var		usedAttributes	= extractUsedAttributes( lineText );

			// Add attribute completions
			Stream.of( descriptor.getComponent().getDeclaredAttributes() )
			    .filter( attr -> !usedAttributes.contains( attr.name().getName().toLowerCase() ) )
			    .forEach( attr -> result.add( createAttributeCompletion( attr ) ) );
		} else if ( kind == CompletionContextKind.BXM_TAG_ATTRIBUTE_VALUE ) {

			// Check if the attribute exists
			String attributeName = context.getTriggerText();
			if ( attributeName == null || attributeName.isEmpty() )
				return;

			// Add attribute value completions
			Stream.of( descriptor.getComponent().getDeclaredAttributes() )
			    .filter( attr -> attr.name().toString().equalsIgnoreCase( attributeName ) )
			    .findFirst()
			    .ifPresent( targetAttr -> addAttributeValueCompletions( targetAttr, result ) );

		}
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

		item.setInsertText( resolveAttributeSnippet( attrName, attr ) );
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
	 * Evaluates which snippet format to return based on the attribute's type
	 *
	 * Separated this out of createAttributeCompletion to make it easier to expand for other attribute types
	 * in the future
	 */
	private String resolveAttributeSnippet( String attrName, Attribute attr ) {
		String type = attr.type();

		if ( type.equalsIgnoreCase( "boolean" ) ) {
			return attrName + "=\"${1|true,false|}\"$0";
		}

		return attrName + "=\"$1\"$0";
	}

	/**
	 * Evaluates the data type and populates completion result list with relevant values
	 * This can be expanded for multiple different attribute value types
	 */
	private void addAttributeValueCompletions( Attribute attr, List<CompletionItem> result ) {
		if ( attr.type().equalsIgnoreCase( "boolean" ) ) {
			result.add( createAttributeValueCompletion( "true" ) );
			result.add( createAttributeValueCompletion( "false" ) );
		}
	}

	/**
	 * Creates and returns a plain text completion item for a specific attribute value string.
	 */
	private CompletionItem createAttributeValueCompletion( String str ) {
		CompletionItem item = new CompletionItem();
		item.setLabel( str );
		item.setKind( CompletionItemKind.Value );
		item.setInsertText( str );
		item.setInsertTextFormat( InsertTextFormat.PlainText );
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
