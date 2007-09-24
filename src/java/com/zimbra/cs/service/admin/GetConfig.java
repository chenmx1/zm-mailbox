/*
 * ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1
 * 
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 ("License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.zimbra.com/license
 * 
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the License for the specific language governing rights and limitations
 * under the License.
 * 
 * The Original Code is: Zimbra Collaboration Suite Server.
 * 
 * The Initial Developer of the Original Code is Zimbra, Inc.
 * Portions created by Zimbra are Copyright (C) 2004, 2005, 2006, 2007 Zimbra, Inc.
 * All Rights Reserved.
 * 
 * Contributor(s): 
 * 
 * ***** END LICENSE BLOCK *****
 */

/*
 * Created on Jun 17, 2004
 */
package com.zimbra.cs.service.admin;

import java.util.Map;

import com.zimbra.common.service.ServiceException;
import com.zimbra.common.soap.AdminConstants;
import com.zimbra.common.soap.Element;
import com.zimbra.cs.account.AttributeManager;
import com.zimbra.cs.account.Provisioning;
import com.zimbra.cs.service.account.ToXML;
import com.zimbra.soap.ZimbraSoapContext;

/**
 * @author schemers
 */
public class GetConfig extends AdminDocumentHandler {
    
	public Element handle(Element request, Map<String, Object> context) throws ServiceException {

        ZimbraSoapContext lc = getZimbraSoapContext(context);
        Provisioning prov = Provisioning.getInstance();

        Element a = request.getElement(AdminConstants.E_A);
        String name = a.getAttribute(AdminConstants.A_N);

        String value[] = prov.getConfig().getMultiAttr(name);

        Element response = lc.createElement(AdminConstants.GET_CONFIG_RESPONSE);
        doConfig(response, name, value, AttributeManager.getInstance().isEmailOrIDN(name));

        return response;
	}

    public static void doConfig(Element e, String name, String[] value, boolean isIDN) {
        if (value == null)
            return;
        for (int i = 0; i < value.length; i++)
            ToXML.encodeAttrOld(e, name, value[i], AdminConstants.E_A, AdminConstants.A_N, isIDN);
    }

    public static void doConfig(Element e, String name, String value, boolean isIDN) {
        if (value == null)
            return;
        ToXML.encodeAttrOld(e, name, value, AdminConstants.E_A, AdminConstants.A_N, isIDN);
    }
}
