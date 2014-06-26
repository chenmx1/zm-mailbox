/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2012, 2013, 2014 Zimbra, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software Foundation,
 * version 2 of the License.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.
 * If not, see <http://www.gnu.org/licenses/>.
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.doc.soap.apidesc;

import java.util.List;

import com.google.common.collect.Lists;
import com.zimbra.doc.soap.ChoiceNode;
import com.zimbra.doc.soap.DescriptionNode;
import com.zimbra.doc.soap.ValueDescription;
import com.zimbra.doc.soap.XmlAttributeDescription;
import com.zimbra.doc.soap.XmlElementDescription;
import com.zimbra.soap.JaxbUtil;

/**
 * Information about a "type".
 * i.e.  Associated class name, description and ID information for child nodes
 * @author gren
 *
 */
public class SoapApiType {
    private final String className;
    private List<SoapApiElementArtifact> subElements = null;
    private List<SoapApiAttribute> attributes = null;
    private ValueDescription type = null; // Value associated with type (if it is a simple type)
    private ValueDescription valueType = null; // Value associated with field annotated with @XmlValue (if present)
    private String valueJaxb = null; // Class associated with field annotated with @XmlValue (if present)
    private String valueFieldDescription = null;

    /* no-argument constructor needed for deserialization */
    @SuppressWarnings("unused")
    private SoapApiType() {
        className = null;
    }

    public SoapApiType(XmlElementDescription xmlDesc) {
        boolean simple = false;
        Class<?> jaxbClass = xmlDesc.getJaxbClass();
        if (jaxbClass == null) {
            // Don't expect to see this.
            simple = true;
            className = null;
        } else {
            className = jaxbClass.getName();
            if (jaxbClass.isEnum()) {
                simple = true;
                type = ValueDescription.create(jaxbClass);
            }
        }
        if (!simple) {
            if (xmlDesc.getAttribs().size() > 0) {
                attributes = Lists.newArrayList();
            }
            for (XmlAttributeDescription attr : xmlDesc.getAttribs()) {
                attributes.add(new SoapApiAttribute(attr));
            }
            if (xmlDesc.getChildren().size() > 0) {
                subElements = Lists.newArrayList();
            }
            for (DescriptionNode child : xmlDesc.getChildren()) {
                if (child instanceof XmlElementDescription) {
                    XmlElementDescription elemChild = (XmlElementDescription) child;
                    if (elemChild.isWrapper()) {
                        subElements.add(new SoapApiWrapperElement(elemChild));
                    } else if (elemChild.isJaxbType()) {
                        subElements.add(new SoapApiElement(elemChild));
                    } else {
                        subElements.add(new SoapApiSimpleElement(elemChild));
                    }
                } else if (child instanceof ChoiceNode) {
                    subElements.add(new SoapApiElementChoiceInfo((ChoiceNode)child));
                }
            }
            valueType = xmlDesc.getValueType();
            if (valueType == null) {
                valueFieldDescription = null;
            } else {
                valueFieldDescription = xmlDesc.getValueFieldDescription();
                Class<?> klass;
                try {
                    klass = Class.forName(valueType.getClassName());
                    if (JaxbUtil.isJaxbType(klass)) {
                        valueJaxb = valueType.getClassName();
                        valueType = null;
                    }
                } catch (ClassNotFoundException e) {
                    klass = null;
                }
            }
        }
    }

    public SoapApiType(Class<?> klass) {
        className = klass.getName();
        valueFieldDescription = null;
        type = ValueDescription.create(klass);
    }

    public String getClassName() { return className; }
    public List<SoapApiElementArtifact> getSubElements() { return subElements; }
    public List<SoapApiAttribute> getAttributes() { return attributes; }
    public String getValueFieldDescription() { return valueFieldDescription; }
    public ValueDescription getType() { return type; }
    public ValueDescription getValueType() { return valueType; }
    public String getValueJaxb() { return valueJaxb; }
}
