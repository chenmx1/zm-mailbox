/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2011, 2012, 2013, 2014 Zimbra, Inc.
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

package com.zimbra.soap.admin.message;

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.zimbra.common.soap.SyncAdminConstants;
import com.zimbra.common.soap.SyncConstants;
import com.zimbra.soap.admin.type.DeviceStatusInfo;

@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name=SyncAdminConstants.E_REMOTE_WIPE_RESPONSE)
@XmlType(propOrder = {})
public class RemoteWipeResponse {

    /**
     * @zm-api-field-description Information about device status
     */
    @XmlElement(name=SyncConstants.E_DEVICE /* device */, required=false)
    private List<DeviceStatusInfo> devices = Lists.newArrayList();

    public RemoteWipeResponse() {
    }

    public void setDevices(Iterable<DeviceStatusInfo> devices) {
        this.devices.clear();
        if (devices != null) {
            Iterables.addAll(this.devices, devices);
        }
    }

    public void addDevice(DeviceStatusInfo device) {
        this.devices.add(device);
    }

    public List<DeviceStatusInfo> getDevices() {
        return Collections.unmodifiableList(devices);
    }

    public Objects.ToStringHelper addToStringInfo(Objects.ToStringHelper helper) {
        return helper.add("devices", devices);
    }

    @Override
    public String toString() {
        return addToStringInfo(Objects.toStringHelper(this)).toString();
    }
}
