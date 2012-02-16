/*
 * ***** BEGIN LICENSE BLOCK *****
 * Zimbra Collaboration Suite Server
 * Copyright (C) 2011 Zimbra, Inc.
 *
 * The contents of this file are subject to the Zimbra Public License
 * Version 1.3 ("License"); you may not use this file except in
 * compliance with the License.  You may obtain a copy of the License at
 * http://www.zimbra.com/license.
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
 * ***** END LICENSE BLOCK *****
 */

package com.zimbra.soap.mail.message;

import com.google.common.base.Objects;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import java.util.Collections;
import java.util.List;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

import com.zimbra.common.soap.MailConstants;
import com.zimbra.soap.type.AttributeName;
import com.zimbra.soap.type.Id;
import com.zimbra.soap.type.ZmBoolean;

/**
 * @zm-api-command-description Get contacts
 * <br />
 * Contact group members are returned as &lt;m> elements.
 * <br />
 * If derefGroupMember is not set, group members are returned in the order they were inserted in the group.
 * <br />
 * If derefGroupMember is set, group members are returned ordered by the "key" of member.
 * <br />
 * Key is:
 * <ul>
 * <li> for contact ref (type="C"): the fileAs field of the Contact
 * <li> for GAL ref (type="G"): email address of the GAL entry
 * <li> for inlined member (type="I"): the value
 * <ul>
 * Contact group members are returned as sub-elements of &lt;m>.
 * If for any(transient or permanent) reason a member cannot be dereferenced, then there will be no sub-element
 * under &lt;m>.
 * <br />
 * <br />
 * For example:
 * <pre>
 * &lt;GetContactsResponse xmlns="urn:zimbraMail">
 *   &lt;cn id="680" fileAsStr="group1" d="1308612784000" rev="900" l="7">
 *     &lt;a n="nickname">group one&lt;/a>
 *     &lt;a n="type">group&lt;/a>
 *     &lt;a n="fileAs">8:group1&lt;/a>
 *     &lt;m value="user@zimbra.com" type="I"/>
 *     &lt;m value="282" type="C">
 *       &lt;cn id="282" fileAsStr="Smith, John" d="1308547353000" rev="27" l="7">
 *         &lt;a n="lastName">Smith&lt;/a>
 *         &lt;a n="email">jsmith@example.zimbra.com&lt;/a>
 *         &lt;a n="workURL">http://www.example.zimbra.com&lt;/a>
 *         &lt;a n="company">Zimbra&lt;/a>
 *         &lt;a n="workCountry">US&lt;/a>
 *         &lt;a n="workState">CA&lt;/a>
 *         &lt;a n="workPhone">(408) 123-4567&lt;/a>
 *         &lt;a n="firstName">Mark&lt;/a>
 *       &lt;/cn>
 *     &lt;/m>
 *     &lt;m value="uid=user1,ou=people,dc=phoebe,dc=mbp" type="G">
 *       &lt;cn id="2a692f57-1a5b-4542-9f8e-28dfdd0e3f43:260" fileAsStr="Demo User One" d="1308626790000" rev="22" l="257">
 *         &lt;a n="createTimeStamp">20110620052132Z&lt;/a>
 *         &lt;a n="lastName">user1&lt;/a>
 *         &lt;a n="email">user1@phoebe.mbp&lt;/a>
 *         &lt;a n="zimbraId">b4bf7953-c10f-449e-b7fe-3df48eea36f8&lt;/a>
 *         &lt;a n="objectClass">inetOrgPerson&lt;/a>
 *         &lt;a n="objectClass">zimbraAccount&lt;/a>
 *         &lt;a n="objectClass">amavisAccount&lt;/a>
 *         &lt;a n="fullName">Demo User One&lt;/a>
 *         &lt;a n="dn">uid=user1,ou=people,dc=phoebe,dc=mbp&lt;/a>
 *         &lt;a n="workPhone">+1 650 555 1111&lt;/a>
 *         &lt;a n="modifyTimeStamp">20110620052229Z&lt;/a>
 *         &lt;a n="fileAs">8:Demo User One&lt;/a>
 *       &lt;/cn>
 *     &lt;/m>
 *   &lt;/cn>
 * &lt;/GetContactsResponse>
 * </pre>
 */
@XmlAccessorType(XmlAccessType.NONE)
@XmlRootElement(name=MailConstants.E_GET_CONTACTS_REQUEST)
public class GetContactsRequest {

    /**
     * @zm-api-field-tag return-mod-date-flag
     * @zm-api-field-description If set, return modified date (md) on contacts.
     */
    @XmlAttribute(name=MailConstants.A_SYNC /* sync */, required=false)
    private ZmBoolean sync;

    /**
     * @zm-api-field-tag folder-id
     * @zm-api-field-description If is present, return only contacts in the specified folder.
     */
    @XmlAttribute(name=MailConstants.A_FOLDER /* l */, required=false)
    private String folderId;

    // Valid values are case insensitive "names" from enum com.zimbra.cs.index.SortBy
    /**
     * @zm-api-field-tag sort-by
     * @zm-api-field-description Sort by
     */
    @XmlAttribute(name=MailConstants.A_SORTBY /* sortBy */, required=false)
    private String sortBy;

    /**
     * @zm-api-field-tag deref-group-member
     * @zm-api-field-description If set, deref contact group members.
     * <br />
     * Contact members can be:
     * <ul>
     * <li> inline data of name and email address
     * <li> a reference to a local contact or a shared contact
     * <li> a GAL entry
     * </ul>
     * <b>Note</b>: for performance reason, derefGroupMember is supported only when specific contact ids are specified.
     */
    @XmlAttribute(name=MailConstants.A_DEREF_CONTACT_GROUP_MEMBER /* derefGroupMember */, required=false)
    private ZmBoolean derefGroupMember;

    /**
     * @zm-api-field-tag return-hidden-attrs
     * @zm-api-field-description Whether to return contact hidden attrs defined in <b>zimbraContactHiddenAttributes</b>
     * <br />
     * ignored if <b>&lt;a></b> is present.
     */
    @XmlAttribute(name=MailConstants.A_RETURN_HIDDEN_ATTRS /* returnHiddenAttrs */, required=false)
    private ZmBoolean returnHiddenAttrs;

    /**
     * @zm-api-field-tag max-members
     * @zm-api-field-description Max members
     */
    @XmlAttribute(name=MailConstants.A_MAX_MEMBERS /* maxMembers */, required=false)
    private Long maxMembers;

    /**
     * @zm-api-field-description Attrs - if present, return only the specified attribute(s).
     */
    @XmlElement(name=MailConstants.E_ATTRIBUTE /* a */, required=false)
    private List<AttributeName> attributes = Lists.newArrayList();

    /**
     * @zm-api-field-description If present, return only the specified attribute(s) for derefed members, applicable
     * only when <b>derefGroupMember</b> is set.
     */
    @XmlElement(name=MailConstants.E_CONTACT_GROUP_MEMBER_ATTRIBUTE /* ma */, required=false)
    private List<AttributeName> memberAttributes = Lists.newArrayList();

    /**
     * @zm-api-field-description If present, only get the specified contact(s).
     */
    @XmlElement(name=MailConstants.E_CONTACT /* cn */, required=false)
    private List<Id> contacts = Lists.newArrayList();

    public GetContactsRequest() {
    }

    public void setSync(Boolean sync) { this.sync = ZmBoolean.fromBool(sync); }
    public void setFolderId(String folderId) { this.folderId = folderId; }
    public void setSortBy(String sortBy) { this.sortBy = sortBy; }
    public void setDerefGroupMember(Boolean derefGroupMember) {
        this.derefGroupMember = ZmBoolean.fromBool(derefGroupMember);
    }
    public void setReturnHiddenAttrs(Boolean returnHiddenAttrs) {
        this.returnHiddenAttrs = ZmBoolean.fromBool(returnHiddenAttrs);
    }
    public void setMaxMembers(Long maxMembers) {
        this.maxMembers = maxMembers;
    }
    public void setAttributes(Iterable <AttributeName> attributes) {
        this.attributes.clear();
        if (attributes != null) {
            Iterables.addAll(this.attributes,attributes);
        }
    }

    public void addAttribute(AttributeName attribute) {
        this.attributes.add(attribute);
    }

    public void setMemberAttributes(Iterable <AttributeName> memberAttributes) {
        this.memberAttributes.clear();
        if (memberAttributes != null) {
            Iterables.addAll(this.memberAttributes,memberAttributes);
        }
    }

    public void addMemberAttribute(AttributeName memberAttribute) {
        this.memberAttributes.add(memberAttribute);
    }

    public void setContacts(Iterable <Id> contacts) {
        this.contacts.clear();
        if (contacts != null) {
            Iterables.addAll(this.contacts,contacts);
        }
    }

    public void addContact(Id contact) {
        this.contacts.add(contact);
    }

    public Boolean getSync() { return ZmBoolean.toBool(sync); }
    public String getFolderId() { return folderId; }
    public String getSortBy() { return sortBy; }
    public Boolean getDerefGroupMember() { return ZmBoolean.toBool(derefGroupMember); }
    public Boolean getReturnHiddenAttrs() { return ZmBoolean.toBool(returnHiddenAttrs); }
    public Long getMaxMembers() { return maxMembers; }
    public List<AttributeName> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }
    public List<AttributeName> getMemberAttributes() {
        return Collections.unmodifiableList(memberAttributes);
    }
    public List<Id> getContacts() {
        return Collections.unmodifiableList(contacts);
    }

    public Objects.ToStringHelper addToStringInfo(
                Objects.ToStringHelper helper) {
        return helper
            .add("sync", sync)
            .add("folderId", folderId)
            .add("sortBy", sortBy)
            .add("attributes", getAttributes())
            .add("memberAttributes", getMemberAttributes())
            .add("contacts", getContacts());
    }

    @Override
    public String toString() {
        return addToStringInfo(Objects.toStringHelper(this))
                .toString();
    }
}
