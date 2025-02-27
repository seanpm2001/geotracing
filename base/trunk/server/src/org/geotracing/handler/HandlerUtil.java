// Copyright (c) 2005 Just Objects B.V. <just@justobjects.nl>
// Distributable under LGPL license. See terms of license at gnu.org.$
package org.geotracing.handler;

import nl.justobjects.jox.dom.JXElement;
import org.keyworx.utopia.core.data.ErrorCode;
import org.keyworx.utopia.core.data.UtopiaException;
import org.keyworx.utopia.core.session.UtopiaRequest;
import org.keyworx.utopia.core.session.UtopiaResponse;
import org.keyworx.utopia.core.session.UtopiaSession;
import org.keyworx.utopia.core.session.UtopiaApplication;
import org.keyworx.utopia.core.util.Oase;
import org.keyworx.utopia.core.control.DefaultHandler;
import org.keyworx.common.log.Logging;
import org.keyworx.oase.api.Record;
import org.keyworx.oase.api.OaseException;

/**
 * Utilities, shorthands to use in Utopia Handlers.
 * <p/>
 *
 * @author Just van den Broecke
 * @version $Id$
 */
public class HandlerUtil {
	/**
	 * Add tags to item(s).
	 */
	public static UtopiaResponse addTags(UtopiaSession anUtopiaSession, String theTags, String theIds) {
		UtopiaRequest tagRequest = null;
		UtopiaResponse tagResponse = null;
		try {
			JXElement tagRequestElm = new JXElement("tagging-tag-req");
			tagRequestElm.setAttr("tags", theTags.trim().toLowerCase());
			tagRequestElm.setAttr("mode", "add");
			tagRequestElm.setAttr("items",theIds);


			// Create Utopia request
			tagRequest = new UtopiaRequest(anUtopiaSession, tagRequestElm);

			// Perform Utopia request
			UtopiaApplication utopiaApplication = anUtopiaSession.getContext().getUtopiaApplication();
			tagResponse = utopiaApplication.performRequest(tagRequest);
			Logging.getLog(tagRequest).info("Added tags to ids=" + theIds + " rsp=" + tagResponse.getResponseCommand().getTag());
		} catch (Throwable t) {
			Logging.getLog(tagRequest).warn("Cannot add tags to ids=" + theIds, t);
		}
		return tagResponse;
	}


/**
	 * Default implementation for unknown service request.
	 * <p/>
	 *
	 * @param anUtopiaReq A UtopiaRequest
	 * @return A negative UtopiaResponse.
	 */
	public static JXElement unknownReq(UtopiaRequest anUtopiaReq) {
		String service = anUtopiaReq.getServiceName();
		Logging.getLog(anUtopiaReq).warn("Unknown service " + service);
		return DefaultHandler.createNegativeResponse(service, ErrorCode.__6000_Unknown_command, "unknown service: " + service);
	}

	/**
	 * Get Oase session (utopia) from request.
	 */
	public static Oase getOase(UtopiaRequest anUtopiaReq) {
		return anUtopiaReq.getUtopiaSession().getContext().getOase();
	}

	/**
	 * Get user (Account) record from request.
	 */
	public static Record getAccountRecord(UtopiaRequest anUtopiaReq) throws OaseException {
		return getOase(anUtopiaReq).getRelater().getRelated(getPersonRecord(anUtopiaReq), "utopia_account", null)[0];
	}

	/**
	 * Get user (Account) name from request.
	 */
	public static String getAccountName(UtopiaRequest anUtopiaReq) throws OaseException {
		return getAccountRecord(anUtopiaReq).getStringField("loginname");
	}

	/**
	 * Get user (Person) record from request.
	 */
	public static Record getPersonRecord(UtopiaRequest anUtopiaReq) throws OaseException {
		return getOase(anUtopiaReq).getFinder().read(getUserId(anUtopiaReq), "utopia_person");
	}

	/**
	 * Get user (Person) id from request.
	 */
	public static int getUserId(UtopiaRequest anUtopiaReq)  {
		return Integer.parseInt(anUtopiaReq.getUtopiaSession().getContext().getUserId());
	}

	/**
	 * Get user (Person) name from request.
	 */
	public static String getUserName(UtopiaRequest anUtopiaReq)  {
		return anUtopiaReq.getUtopiaSession().getContext().getUserName();
	}

	/**
	 * Throw exception when numeric attribute empty or not present.
	 */
	public static void throwIfNotOwner(Oase anOase, Record aPersonRecord, Record aTargetRecord) throws OaseException, UtopiaException {
		if (!anOase.getRelater().isRelated(aPersonRecord, aTargetRecord)) {
			throw new UtopiaException("Person id=" + aPersonRecord.getId() + " is not owner of record id=" + aTargetRecord.getId(), ErrorCode.__6007_insufficient_rights_error);
		}
	}

	/**
	 * Throw exception when attribute empty or not present.
	 */
	public static void throwOnMissingAttr(String aName, String aValue) throws UtopiaException {
		if (aValue == null || aValue.length() == 0) {
			throw new UtopiaException("Missing name=" + aName + " value=" + aValue, ErrorCode.__6002_Required_attribute_missing);
		}
	}

	/**
	 * Throw exception when attribute empty or not present.
	 */
	public static void throwOnMissingAttr(JXElement anElm, String aName) throws UtopiaException {
		String value = anElm.getAttr(aName);
		if (value == null || value.length() == 0) {
			throw new UtopiaException("Missing attribute name=" + aName, ErrorCode.__6002_Required_attribute_missing);
		}
	}

	/**
	 * Throw exception when child element empty not present.
	 */
	public static void throwOnMissingChildElement(JXElement aParentElement, String aChildTag) throws UtopiaException {
		if (aParentElement.getChildByTag(aChildTag) == null) {
			throw new UtopiaException("Missing element name=" + aChildTag + " in element=" + aParentElement.getTag(), ErrorCode.__6002_Required_attribute_missing);
		}
	}

	/**
	 * Throw exception when numeric attribute empty or not present.
	 */
	public static void throwOnNonNumAttr(String aName, String aValue) throws UtopiaException {
		throwOnMissingAttr(aName, aValue);
		try {
			Long.parseLong(aValue);
		} catch (Throwable t) {
			throw new UtopiaException("Invalid numvalue name=" + aName + " value=" + aValue, ErrorCode.__6004_Invalid_attribute_value);
		}
	}

	/**
	 * Throw exception when numeric attribute empty or not present or invalid numvalue.
	 */
	public static void throwOnNonNumAttr(JXElement anElm, String aName) throws UtopiaException {
		throwOnMissingAttr(anElm, aName);
		try {
			Long.parseLong(anElm.getAttr(aName));
		} catch (Throwable t) {
			throw new UtopiaException("Invalid numvalue name=" + aName, ErrorCode.__6004_Invalid_attribute_value);
		}
	}
	/**
	 * Throw exception when numeric attribute empty or not present.
	 */
	public static void throwOnNegNumAttr(String aName, long aValue) throws UtopiaException {
		if (aValue == -1) {
			throw new UtopiaException("Invalid numvalue name=" + aName + " value=" + aValue, ErrorCode.__6004_Invalid_attribute_value);
		}
	}
}
