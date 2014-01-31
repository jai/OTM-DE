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
package org.opentravel.schemas.node.properties;

import org.eclipse.swt.graphics.Image;
import org.opentravel.schemas.node.ComponentNode;
import org.opentravel.schemas.node.EnumerationClosedNode;
import org.opentravel.schemas.node.EnumerationOpenNode;
import org.opentravel.schemas.node.INode;
import org.opentravel.schemas.node.ImpliedNode;
import org.opentravel.schemas.node.ModelNode;
import org.opentravel.schemas.node.Node;
import org.opentravel.schemas.node.NodeFactory;
import org.opentravel.schemas.node.NodeNameUtils;
import org.opentravel.schemas.node.PropertyNodeType;
import org.opentravel.schemas.properties.Images;

import org.opentravel.schemacompiler.model.TLEnumValue;
import org.opentravel.schemacompiler.model.TLModelElement;

/**
 * A property node that represents a enumeration literal. See
 * {@link NodeFactory#newComponentMember(INode, Object)}
 * 
 * @author Dave Hollander
 * 
 */

public class EnumLiteralNode extends PropertyNode {

    public EnumLiteralNode(ComponentNode parent, String name) {
        super(new TLEnumValue(), parent, name, PropertyNodeType.ENUM_LITERAL);
        parent.getModelObject().addChild(this.getTLModelObject());

        validateParent(parent);
    }

    public EnumLiteralNode(TLModelElement tlObj, INode parent) {
        super(tlObj, parent, PropertyNodeType.ENUM_LITERAL);

        validateParent(parent);
    }

    private void validateParent(INode parent) {
        if (parent == null) {
            return;
        }

        if (!((parent instanceof EnumerationOpenNode) || (parent instanceof EnumerationClosedNode)))
            throw new IllegalArgumentException("Invalid parent for enumeration literal.");
    }

    @Override
    public boolean canAssign(Node type) {
        return type instanceof ImpliedNode ? true : false;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.opentravel.schemas.node.PropertyNode#createProperty(org.opentravel.schemas.node.Node)
     */
    @Override
    public INode createProperty(Node type) {
        TLEnumValue tlObj = (TLEnumValue) cloneTLObj();
        int index = indexOfNode();
        ((TLEnumValue) getTLModelObject()).getOwningEnum().addValue(index, tlObj);
        EnumLiteralNode n = new EnumLiteralNode(tlObj, null);
        getParent().getChildren().add(index, n);
        n.setName(type.getName());
        return n;
    }

    @Override
    public ImpliedNode getDefaultType() {
        return ModelNode.getUndefinedNode();
    }

    @Override
    public Image getImage() {
        return Images.getImageRegistry().get(Images.RoleValue);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.opentravel.schemas.node.INode#getLabel()
     */
    @Override
    public String getLabel() {
        return modelObject.getLabel() == null ? "" : modelObject.getLabel();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.opentravel.schemas.node.properties.PropertyNode#getOwningComponent()
     */
    @Override
    public Node getOwningComponent() {
        return getParent();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.opentravel.schemas.node.PropertyNode#setName(java.lang.String)
     */
    @Override
    public void setName(String name) {
        modelObject.setName(NodeNameUtils.fixEnumerationValue(name));
    }

}
