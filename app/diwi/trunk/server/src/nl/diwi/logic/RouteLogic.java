// Copyright (c) 2000 Just Objects B.V. <just@justobjects.nl>
// Distributable under LGPL license. See terms of license at gnu.org.$

package nl.diwi.logic;

import nl.diwi.external.RouteGenerator;
import nl.diwi.util.Constants;
import nl.diwi.util.GPXUtil;
import nl.justobjects.jox.dom.JXElement;
import org.keyworx.common.log.Log;
import org.keyworx.common.log.Logging;
import org.keyworx.oase.api.OaseException;
import org.keyworx.oase.api.Record;
import org.keyworx.utopia.core.data.ErrorCode;
import org.keyworx.utopia.core.data.UtopiaException;
import org.keyworx.utopia.core.util.Oase;
import org.keyworx.utopia.core.util.XML;
import org.postgis.PGbox2d;
import org.postgis.PGgeometryLW;
import org.postgis.LineString;
import org.postgis.Point;
import org.geotracing.gis.Transform;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Properties;
import java.util.Vector;

/**
 * Handles all logic related to commenting.
 * <p/>
 * Uses Oase directly for DB updates.
 *
 * @author Just van den Broecke
 * @version $Id: CommentLogic.java 353 2007-02-02 12:04:11Z just $
 */
public class RouteLogic implements Constants {
	public static final String TABLE_PERSON = "utopia_person";
	private Log log = Logging.getLog("RouteLogic");

	private static final Properties properties = new Properties();
	public static final String PROP_MAX_CONTENT_CHARS = "max-content-chars";

	private Oase oase;

	public RouteLogic(Oase o) {
		oase = o;
	}

	/*
			<route-generate-req >
				 <pref name="bos" value="40" type="outdoor-params" />
				 <pref name="heide" value="20" type="outdoor-params" />
				 <pref name="bebouwing" value="10" type="outdoor-params" />
				 <pref name="thema" value="forts" type="theme" />
				 <pref name="activiteit" value="wandelaar" type="activity" />
				 <pref name="startpunt" value="nijevelt" type="route" />
				 <pref name="eindpunt" value="groningen" type="route" />
				 <pref name="lengte" value="130" type="route" />
			</route-generate-req>

			return vector of Route record converted to JXElement.
		 */
	public JXElement generateRoute(JXElement reqElm, int personId) throws UtopiaException {
		try {
			Record person = oase.getFinder().read(personId);

			// first delete existing prefs
			Record[] prefs = oase.getRelater().getRelated(person, PREFS_TABLE, null);
			for (int i = 0; i < prefs.length; i++) {
				oase.getModifier().delete(prefs[i]);
			}

			// now store the prefs
			Vector prefElms = reqElm.getChildrenByTag(PREF_ELM);
			prefs = new Record[prefElms.size()];
			for (int i = 0; i < prefElms.size(); i++) {
				JXElement prefElm = (JXElement) prefElms.elementAt(i);

				// create the pref
				Record pref = oase.getModifier().create(PREFS_TABLE);
				pref.setIntField(OWNER_FIELD, personId);
				pref.setStringField(NAME_FIELD, prefElm.getAttr(NAME_FIELD));
				pref.setStringField(VALUE_FIELD, prefElm.getAttr(VALUE_FIELD));
				pref.setIntField(TYPE_FIELD, prefElm.getIntAttr(TYPE_FIELD));
				prefs[i] = pref;
				oase.getModifier().insert(pref);

				// relate pref to person
				oase.getRelater().relate(person, pref);
			}

			JXElement generated = RouteGenerator.generateRoute(prefs);

			//Process the GPX into datastructures
			Record route = null;
			try {
				route = oase.getModifier().create(ROUTE_TABLE);
				route.setStringField(NAME_FIELD, new String(generated.getChildByTag(NAME_ELM).getCDATA()));
				route.setStringField(DESCRIPTION_FIELD, new String(generated.getChildByTag(DESCRIPTION_ELM).getCDATA()));
				route.setIntField(TYPE_FIELD, ROUTE_TYPE_GENERATED);

				LineString lineString = GPXUtil.GPXRoute2LineString(generated);

				// Convert if routing API is in RD
				if (SRID_ROUTING_API == EPSG_DUTCH_RD) {
					lineString = RD2WGS84(lineString);
				}

				PGgeometryLW geom = new PGgeometryLW(lineString);
				route.setObjectField(PATH_FIELD, geom);
				oase.getModifier().insert(route);

				// now relate the route to the person
				oase.getRelater().relate(person, route);
			} catch (OaseException e) {
				e.printStackTrace();
			}

			//Convert Route record to XML and add to result
			return getRoute(route.getId());
		} catch (Throwable t) {
			log.error("Exception in generateRoute: " + t.toString());
			throw new UtopiaException("Error in generateRoute", t, ErrorCode.__6006_database_irregularity_error);
		}
	}

	public int insertRoute(JXElement aRouteElement, int aRouteType) throws UtopiaException {
		Record route;
		try {
			// fixed routes have unique names so check first
			String name = aRouteElement.getChildByTag(NAME_ELM).getCDATA();
			Record[] recs = oase.getFinder().queryTable(ROUTE_TABLE, NAME_FIELD + "='" + name + "'", null, null);
			if (recs.length > 0) {
				// do an update
				route = oase.getFinder().read(recs[0].getId());
				route.setStringField(NAME_FIELD, aRouteElement.getChildByTag(NAME_ELM).getCDATA());
				route.setStringField(DESCRIPTION_FIELD, aRouteElement.getChildByTag(DESCRIPTION_ELM).getCDATA());
				route.setIntField(TYPE_FIELD, aRouteType);

				LineString lineString = GPXUtil.GPXRoute2LineString(aRouteElement);
				// Convert if routing API is in RD
				if (SRID_ROUTING_API == EPSG_DUTCH_RD) {
					lineString = RD2WGS84(lineString);
				}
				PGgeometryLW geom = new PGgeometryLW(lineString);
				route.setObjectField(PATH_FIELD, geom);
				oase.getModifier().update(route);

			} else {
				// do an insert
				route = oase.getModifier().create(ROUTE_TABLE);
				route.setStringField(NAME_FIELD, aRouteElement.getChildByTag(NAME_ELM).getCDATA());
				route.setStringField(DESCRIPTION_FIELD, aRouteElement.getChildByTag(DESCRIPTION_ELM).getCDATA());
				route.setIntField(TYPE_FIELD, aRouteType);

				LineString lineString = GPXUtil.GPXRoute2LineString(aRouteElement);
				// Convert if routing API is in RD
				if (SRID_ROUTING_API == EPSG_DUTCH_RD) {
					lineString = RD2WGS84(lineString);
				}

				PGgeometryLW geom = new PGgeometryLW(lineString);
				route.setObjectField(PATH_FIELD, geom);
				oase.getModifier().insert(route);
			}
		} catch (Throwable t) {
			log.error("Exception in insertRoute: " + t.toString());
			throw new UtopiaException("Error in insertRoute", t, ErrorCode.__6006_database_irregularity_error);
		}
		return route.getId();
	}

	public JXElement getRoute(int routeId) throws UtopiaException {
		JXElement routeElm;
		try {
			routeElm = getRoute(oase.getFinder().read(routeId));
		} catch (Throwable t) {
			log.error("Exception in getRoute: " + t.toString());
			throw new UtopiaException("Error in getRoute", t, ErrorCode.__6006_database_irregularity_error);
		}
		return routeElm;
	}

	public JXElement getRoute(Record aRoute) throws UtopiaException {
		JXElement routeElm;
		try {
			routeElm = XML.createElementFromRecord(ROUTE_ELM, aRoute);
			routeElm.removeChildByTag(PATH_FIELD);

			JXElement lengthElm = new JXElement(DISTANCE_FIELD);
			lengthElm.setText("" + getDistance(aRoute));
			routeElm.addChild(lengthElm);
		} catch (Throwable t) {
			log.error("Exception in getRoute: " + t.toString());
			throw new UtopiaException("Error in getRoute", t, ErrorCode.__6006_database_irregularity_error);
		}
		return routeElm;
	}

	public Vector getRoutes(int aRouteType, String aPersonId) throws UtopiaException {
		Vector results;
		try {
			Record[] routes;
			if (aRouteType == ROUTE_TYPE_GENERATED) {
				Record person = oase.getFinder().read(Integer.parseInt(aPersonId));
				routes = oase.getRelater().getRelated(person, ROUTE_TABLE, null);
			} else {
				routes = oase.getFinder().queryTable(ROUTE_TABLE, TYPE_FIELD + "=" + aRouteType, null, null);
			}

			results = new Vector(routes.length);
			for (int i = 0; i < routes.length; i++) {
				results.add(getRoute(routes[i]));
			}
		} catch (Throwable t) {
			log.error("Exception in getRoutes: " + t.toString());
			throw new UtopiaException("Error in getRoutes", t, ErrorCode.__6006_database_irregularity_error);
		}
		return results;
	}

	/**
	 * Delete a route.
	 *
	 * @param aRouteId a comment id
	 * @throws UtopiaException Standard exception
	 */
	public void deleteRoute(int aRouteId) throws UtopiaException {
		try {
			oase.getModifier().delete(aRouteId);
		} catch (Throwable t) {
			log.error("Exception in deleteRoute: " + t.toString());
			throw new UtopiaException("Cannot delete route record with id=" + aRouteId, t, ErrorCode.__6006_database_irregularity_error);
		}
	}

	private int getDistance(Record route) throws OaseException {
		Record distance = oase.getFinder().freeQuery(
				"select length2d(path) AS distance from diwi_route where id="
						+ route.getId())[0];

		return (int) Float.parseFloat(distance.getField(DISTANCE_FIELD).toString());
	}


	public String getMapUrl(int routeId, double width, double height) throws UtopiaException {
		Record bounds;
		try {
			bounds = oase.getFinder().freeQuery(
					"select box2d(path) AS bbox from diwi_route where id="
							+ routeId)[0];

		} catch (OaseException e) {
			log.error("Exception in deleteRoute: " + e.toString());
			throw new UtopiaException("Exception in getMapUrl", e, ErrorCode.__6006_database_irregularity_error);
		}
		PGbox2d bbox = (PGbox2d) bounds.getObjectField("bbox");

		double boundsHeight = bbox.getURT().y - bbox.getLLB().y;
		double boundsWidth = bbox.getURT().x - bbox.getLLB().x;

		if (width / height > boundsWidth / boundsHeight) {
			//pad x
			double padWidth = ((width / height) * boundsHeight) - boundsWidth;
			bbox.getLLB().x -= (padWidth / 2);
			bbox.getURT().x += (padWidth / 2);
		} else {
			//pad y			
			double padHeight = (boundsWidth * (height / width)) - boundsHeight;
			bbox.getLLB().y -= (padHeight / 2);
			bbox.getURT().y += (padHeight / 2);
		}

		//add a 10% border
		boundsHeight = bbox.getURT().y - bbox.getLLB().y;
		boundsWidth = bbox.getURT().x - bbox.getLLB().x;
		double padHeight = 0.1 * boundsHeight;
		double padWidth = 0.1 * boundsWidth;
		bbox.getLLB().x -= padWidth / 2;
		bbox.getURT().x += padWidth / 2;
		bbox.getLLB().y -= padHeight / 2;
		bbox.getURT().y += padHeight / 2;


		String boxString;
		try {
			boxString = URLEncoder.encode("" + bbox.getLLB().x + "," + bbox.getLLB().y + "," + bbox.getURT().x + "," + bbox.getURT().y, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			log.error("Exception in deleteRoute: " + e.toString());
			throw new UtopiaException("Exception in getMapUrl", e, ErrorCode.__6006_database_irregularity_error);
		}

		return "http://test.digitalewichelroede.nl/map/?ID=" + routeId + "&LAYERS=topnl_raster,single_diwi_route&SERVICE=WMS&VERSION=1.1.1&REQUEST=GetMap&STYLES=&EXCEPTIONS=application%2Fvnd.ogc.se_inimage&FORMAT=image%2Fjpeg&SRS=EPSG%3A28992&BBOX=" + boxString + "&WIDTH=" + width + "&HEIGHT=" + height;
	}

	/**
	 * Convert LineString from RD to WGS84.
	 *
	 * @param inLS an RD linestring
	 * @throws UtopiaException Standard exception
	 */
	public static LineString RD2WGS84(LineString inLS) throws UtopiaException {
		try {
			Point[] inPoints = inLS.getPoints();
			Point[] outPoints = new Point[inPoints.length];
			for (int i=0; i < inPoints.length; i++) {
				outPoints[i] = RD2WGS84(inPoints[i]);
			}

			return new LineString(outPoints);

		} catch (Throwable t) {
			throw new UtopiaException("Cannot convert LineString", t);
		}
	}

	/**
	 * Convert Point from RD to WGS84.
	 *
	 * @param inPT an RD point
	 * @throws UtopiaException Standard exception
	 */
	public static Point RD2WGS84(Point inPT) throws UtopiaException {
		try {
			double xy[] = Transform.RDtoWGS84(inPT.x, inPT.y);
			Point out = new Point(xy[0], xy[1], inPT.z);
			out.setSrid(EPSG_WGS84);
			return out;
		} catch (Throwable t) {
			throw new UtopiaException("Cannot convert Point", t);
		}
	}

}

