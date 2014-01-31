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
/**
 * 
 */
package org.opentravel.schemas.node;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.opentravel.schemacompiler.model.TLFacetType;
import org.opentravel.schemacompiler.model.TLSimpleFacet;

/**
 * Simple facets have only one attribute property. Used on ValueWithAtributes and Core Objects.
 * 
 * @author Dave Hollander
 * 
 */
public class SimpleFacetNode extends FacetNode {
    private static final Logger LOGGER = LoggerFactory.getLogger(FacetNode.class);

    public SimpleFacetNode(TLSimpleFacet obj) {
        super(obj);
        // SimpleFacetMO will inject a TLnSimpleAttribute to construction stream
        // If owned by a VWA, the TLnSimpleAttribute owner will be set by the vwa node.
    }

    public Node getSimpleAttribute() {
        return getChildren().get(0);
    }

    public void setSimpleAttribute(Node typeNode) {
        getChildren().get(0).setAssignedType(typeNode);
    }

    /**
     * Create a new property and assign its type to the passed node. Simple facets should always
     * have one child.
     * 
     * @param sn
     * @return
     */
    @Override
    public INode createProperty(final Node sn) {
        if (!getChildren().isEmpty()) {
            INode child = getChildren().get(0);
            child.setAssignedType(sn);
        } else {
            LOGGER.warn("Simple Facet (" + this + ")  does not have children.");
        }
        return null;
    }

    @Override
    public TLFacetType getFacetType() {
        return TLFacetType.SIMPLE;
    }

    @Override
    public String getLabel() {
        String label = getSimpleComponentType();
        if (label.indexOf("-Facet") > 0)
            label = label.substring(0, label.indexOf("-Facet"));
        return label.isEmpty() ? "" : label;
    }

    @Override
    public boolean isDeleteable() {
        return false;
    }

    /**
     * Facets assigned to core object list types have no model objects but may be page1-assignable.
     */
    @Override
    public boolean isSimpleAssignable() {
        return true;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.opentravel.schemas.node.Node#isSimpleTypeUser()
     */
    @Override
    public boolean isOnlySimpleTypeUser() {
        // 6/21/2013 - this was true in Node.java. I dont think it should be.
        return false;
    }

    @Override
    public boolean isSimpleFacet() {
        return true;
    }

    @Override
    public void removeProperty(Node property) {
        LOGGER.debug("removeProperty() - Setting simple facet " + getName() + " to empty.");
        getTypeClass().setAssignedType((Node) ModelNode.getEmptyNode()); // set to empty.
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.opentravel.schemas.node.FacetNode#isAssignedByReference()
     */
    @Override
    public boolean isAssignedByReference() {
        // must override since super-type facet will return true.
        return false;
    }

    @Override
    public boolean isAssignableToSimple() {
        Node owner = getOwningComponent();
        if (owner instanceof CoreObjectNode)
            return !((CoreObjectNode) owner).getSimpleType().equals(ModelNode.getEmptyNode());
        return true;
    }

    @Override
    public boolean isAssignableToVWA() {
        return true;
    }

}
