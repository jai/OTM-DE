/**
 * Copyright (C) 2014 OpenTravel Alliance (info@opentravel.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opentravel.schemas.actions;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.opentravel.schemacompiler.model.TLFacetType;
import org.opentravel.schemacompiler.util.OTM16Upgrade;
import org.opentravel.schemas.commands.ContextualFacetHandler;
import org.opentravel.schemas.commands.OtmAbstractHandler;
import org.opentravel.schemas.node.interfaces.LibraryMemberInterface;
import org.opentravel.schemas.node.typeProviders.ChoiceObjectNode;
import org.opentravel.schemas.properties.ExternalizedStringProperties;
import org.opentravel.schemas.properties.StringProperties;
import org.opentravel.schemas.stl2developer.DialogUserNotifier;

/**
 * @author Dave Hollander
 * 
 */
public class AddChoiceFacetAction extends OtmAbstractAction {
	private static StringProperties propsDefault = new ExternalizedStringProperties("action.addChoice");

	OtmAbstractHandler handler = new OtmAbstractHandler() {
		@Override
		public Object execute(ExecutionEvent event) throws ExecutionException {
			return null;
		}
	};

	/**
	 *
	 */
	public AddChoiceFacetAction() {
		super(propsDefault);
	}

	public AddChoiceFacetAction(final StringProperties props) {
		super(props);
	}

	@Override
	public void run() {
		if (OTM16Upgrade.otm16Enabled)
			addContextualFacet(TLFacetType.CHOICE);
		else
			addChoiceFacet();
	}

	@Override
	public boolean isEnabled() {
		// Unmanaged or in the most current (head) library in version chain.
		LibraryMemberInterface n = getOwnerOfNavigatorSelection();
		return n instanceof ChoiceObjectNode ? n.isEditable_newToChain() : false;
	}

	private void addContextualFacet(TLFacetType type) {
		// Verify the current node is editable object
		LibraryMemberInterface current = getOwnerOfNavigatorSelection();
		if (!(current instanceof ChoiceObjectNode) || !current.isEditable_newToChain()) {
			DialogUserNotifier.openWarning("Add Choice Facet",
					"Choice Facets can only be added to choice object that are not versioned or are new to the version chain.");
			return;
		}

		ChoiceObjectNode co = (ChoiceObjectNode) current;

		// FIXED 5/15/2018 - why is this done here and not in the node structure???
		// Used this instead
		co.addFacet("new");

		// Create the contextual facet
		// ChoiceFacetNode cf = new ChoiceFacetNode();
		// cf.setName("new");
		// co.getLibrary().addMember(cf);
		// co.getTLModelObject().addChoiceFacet(cf.getTLModelObject());

		// Create contributed facet
		// NodeFactory.newChild(co, cf.getTLModelObject());
		mc.refresh(co);
	}

	/*
	 * Version 1.5 and older
	 */
	private void addChoiceFacet() {
		ContextualFacetHandler cfh = new ContextualFacetHandler();
		LibraryMemberInterface current = getOwnerOfNavigatorSelection();
		if (current instanceof ChoiceObjectNode)
			cfh.addContextualFacet((ChoiceObjectNode) current);
	}

}
