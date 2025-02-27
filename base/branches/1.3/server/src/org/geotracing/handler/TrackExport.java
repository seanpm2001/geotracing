// Copyright (c) 2000 Just Objects B.V. <just@justobjects.nl>
// Distributable under LGPL license. See terms of license at gnu.org.$

package org.geotracing.handler;

import nl.justobjects.jox.dom.JXAttributeTable;
import nl.justobjects.jox.dom.JXElement;
import org.keyworx.common.log.Log;
import org.keyworx.common.log.Logging;
import org.keyworx.oase.api.OaseException;
import org.keyworx.oase.api.Record;
import org.keyworx.oase.api.Relater;
import org.keyworx.utopia.core.data.UtopiaException;
import org.keyworx.utopia.core.util.Oase;
import org.keyworx.amuse.core.Amuse;
import org.geotracing.gis.GPSSample;
import org.geotracing.handler.Location;
import org.geotracing.handler.Track;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Export Track data to XML formats.
 */
public class TrackExport {
	public static final SimpleDateFormat GPX_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	public static final SimpleDateFormat GPX_TIME_FORMAT_MILLIS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"); // allow millis too
	// public static final SimpleDateFormat GPX_TIME_FORMAT = new SimpleDateFormat("yyyy'-'MM'-'dd'T'HH:mm:ss'Z'");
	public static final String EXPORT_FORMAT_GPX = "gpx";
	public static final String EXPORT_FORMAT_GTX = "gtx";
	public static final String DEFAULT_FORMAT = EXPORT_FORMAT_GTX;
	public static final String DEFAULT_ATTRS = "lon,lat,t";
	public static final String POI_MEDIUM = "medium";

	private Oase oase;
	private Log log = Logging.getLog("TrackExport");

	public TrackExport(Oase o) {
		oase = o;
	}


	/**
	 * Export XML elements from given Track to given format.
	 *
	 * @param aTrack a track
	 * @param aFormat a format e.g. "gpx"
	 * @return theElements one or more JXElements
	 *
	 * @exception org.keyworx.utopia.core.data.UtopiaException Standard exception
	 */
	JXElement export(Track aTrack, String aFormat, String theAttrs, boolean addMedia, long aMinDist, int aMaxPoint) throws UtopiaException {
		// Defaults if null
		theAttrs = theAttrs == null ? DEFAULT_ATTRS : theAttrs;
		aFormat = aFormat == null ? DEFAULT_FORMAT : aFormat;
		TrackPointFilter trackPointFilter = new TrackPointFilter(aMinDist, aMaxPoint, aTrack.getIntValue(Track.FIELD_PTCOUNT));

		if (aFormat.equals(EXPORT_FORMAT_GPX)) {
			return toGPX(aTrack, theAttrs, addMedia, trackPointFilter);
		} else if (aFormat.equals(EXPORT_FORMAT_GTX)) {
			return toGTX(aTrack, theAttrs, addMedia, trackPointFilter);
		} else {
			throw new UtopiaException("Unsupported Track export format: " + aFormat);
		}
	}

	/**
	 * Export Track to internal GTX XML document.
	 *
	 * @param aTrack a track
	 * @param theAttrs (point) attributes to include
	 * @param addMedia include media ?
	 * @return a GTX document
	 *
	 * @exception org.keyworx.utopia.core.data.UtopiaException Standard exception
	 */
	JXElement toGTX(Track aTrack, String theAttrs, boolean addMedia, TrackPointFilter aTrackPointFilter) throws UtopiaException {
		try {
			int trackId = aTrack.getId();
			Vector elements = aTrack.getDataElements();
			JXElement doc = new JXElement("gtx");
			doc.setAttr("version", "1.0");

			// Create <info/> element
			JXElement info = new JXElement("info");
			info.setAttr("id", trackId);
			info.setAttr(Track.FIELD_NAME, aTrack.getName());
			info.setAttr(Track.FIELD_START_DATE, aTrack.getStartDate());
			info.setAttr(Track.FIELD_END_DATE, aTrack.getEndDate());
			info.setAttr(Track.FIELD_STATE, aTrack.getIntValue(Track.FIELD_STATE));
			info.setAttr(Track.FIELD_TYPE, aTrack.getIntValue(Track.FIELD_TYPE));
			JXElement desc = new JXElement(Track.FIELD_DESCRIPTION);
			info.addChild(desc);
			doc.addChild(info);

			// Add media ?
			if (addMedia) {
				JXElement mediaElm = new JXElement("media");

				// Add media related to track
				Record nextMedium = null, nextLocations[] = null, nextLoc = null;
				try {
					Relater relater = oase.getRelater();
					Record[] media = relater.getRelated(aTrack.getRecord(), "base_medium", TrackLogic.REL_TAG_MEDIUM);
					TreeMap sortedMediaMap = new TreeMap();
					String mediumDesc;
						for (int j = 0; j < media.length; j++) {
						nextMedium = media[j];
						JXElement mediumElm = new JXElement("medium");
						mediumElm.setAttr("id", nextMedium.getId());
						mediumElm.setAttr("name", nextMedium.getStringField("name"));
						mediumElm.setAttr("kind", nextMedium.getStringField("kind"));
						mediumElm.setAttr("mime", nextMedium.getStringField("mime"));
						mediumElm.setAttr("ctime", nextMedium.getLongField("creationdate"));

						// Optional description
						mediumDesc = nextMedium.getStringField("description");
						if (mediumDesc == null) {
							mediumDesc = ".";
						}
						mediumElm.setText(mediumDesc);

						long time = nextMedium.getLongField("creationdate");
						mediumElm.setAttr("time", time);

						nextLocations = relater.getRelated(nextMedium, Location.TABLE_NAME, "medium");
						if (nextLocations.length == 1) {
							// Has location: add location attrs
							nextLoc = nextLocations[0];
							mediumElm.setAttr("lon", nextLoc.getRealField("lon"));
							mediumElm.setAttr("lat", nextLoc.getRealField("lat"));
							mediumElm.setAttr("time", nextLoc.getLongField("time"));
						}

						// Sort media by timestamp
						sortedMediaMap.put(new Long(time), mediumElm);
					}

					// Get media elements in time-sorted order
					Collection sortedMedia = sortedMediaMap.values();

					// Add to media element
					mediaElm.addChildren(new Vector(sortedMedia));
				} catch (OaseException oe) {
					log.warn("Error handling medium for trackId=" + trackId, oe);
				} catch (Throwable t) {
					log.error("Serious Error handling medium for trackId=" + trackId, t);
				}
				doc.addChild(mediaElm);
			}

			// Create trk
			JXElement trk = new JXElement("trk");

			// Add points add trkseg, separated when open/close etc tags reached
			JXElement nextElement = null;
			String nextTag = null;
			JXElement nextSegment = null;
			JXElement nextPoint = null;
			int ptCount = 0;
			// Minimal distance
			GPSSample nextSample = null;
			for (int i = 0; i < elements.size(); i++) {
				nextElement = (JXElement) elements.get(i);
				nextTag = nextElement.getTag();
				if (!nextTag.equals(Track.TAG_PT)) {
					if (nextSegment != null) {
						if (nextSegment.hasChildren()) {
							trk.addChild(nextSegment);
						}
						nextSegment = null;
					}
					nextSegment = new JXElement("seg");
					aTrackPointFilter.reset();
				} else {
					if (nextSegment == null) {
						nextSegment = new JXElement("seg");
						aTrackPointFilter.reset();
					}
					nextPoint = new JXElement("pt");
					JXAttributeTable attrTable = nextElement.getAttrs();
					String nextKey;

					// Copy all attrs as specified in theAttrs
					for (Iterator iter = attrTable.keys(); iter.hasNext();) {
						nextKey = (String) iter.next();
						if (theAttrs.indexOf(nextKey) != -1) {
							nextPoint.setAttr(nextKey, nextElement.getAttr(nextKey));
						}
					}

					// Possibly filter out samples too close to each other
					// and excess samples.
					nextSample = new GPSSample(nextPoint.getDoubleAttr("lat"), nextPoint.getDoubleAttr("lat"));
					if (aTrackPointFilter.filter(nextSample)) {
						nextSegment.addChild(nextPoint);
						ptCount++;
					}
				}
			}

			// Last segment
			if (nextSegment != null && nextSegment.hasChildren()) {
				trk.addChild(nextSegment);
			}

			info.setAttr(Track.FIELD_PTCOUNT, ptCount);
			log.info(aTrack.getName() + " export ptCount=" + ptCount + " discardCount=" + aTrackPointFilter.getDiscardCount());
			doc.addChild(trk);
			return doc;
		} catch (Throwable t) {
			log.warn("Unexpected error in toGTX trackId=" + aTrack.getId(), t);
			throw new UtopiaException("Unexpected error in toGTX trackId=" + aTrack.getId(), t);
		}
	}

	/**
	 * Export Track to standard GPX.
	 *
	 * @param aTrack a track
	 * @param theAttrs (point) attributes to include
	 * @param addMedia include media ?
	 * @return a GPX document
	 *
	 * @exception org.keyworx.utopia.core.data.UtopiaException Standard exception
	 */
	JXElement toGPX(Track aTrack, String theAttrs, boolean addMedia, TrackPointFilter aTrackPointFilter) throws UtopiaException {
		try {
			int trackId = aTrack.getId();
			Vector elements = aTrack.getDataElements();

/*     version="1.0"
creator="GPSBabel - http://www.gpsbabel.org"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xmlns="http://www.topografix.com/GPX/1/0"
xsi:schemaLocation="http://www.topografix.com/GPX/1/0 http://www.topografix.com/GPX/1/0/gpx.xsd">
<time>2005-08-29T10:29:02Z</time>
<trk>
<name>ACTIVE LOG #15</name>
<number>14</number>
<trkseg> */
			JXElement gpx = new JXElement("gpx");
			gpx.setAttr("version", "1.1");
			gpx.setAttr("creator", "www.geotracing.org");
			gpx.setAttr("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance");
			gpx.setAttr("xmlns", "http://www.topografix.com/GPX/1/1");
			gpx.setAttr("xsi:schemaLocation", "http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd");

			// Time use start time
			JXElement time = new JXElement("time");
			time.setText(GPX_TIME_FORMAT.format(new Date(aTrack.getEndDate())));
			gpx.addChild(time);


			// Name element
			JXElement name = new JXElement("name");
			name.setText("track #" + trackId);
			gpx.addChild(name);

			// Number element
			JXElement number = new JXElement("number");
			number.setText(trackId + "");
			gpx.addChild(number);

			// Add media ? add as waypoints with links
			if (addMedia) {
				// Add media related to track
				Record nextMedium, nextLocations[], nextLoc;
				String webAppURL = Amuse.server.getPortal().getProperty("webappurl");
				String mediumURL = webAppURL + "/media.srv?id=";
				try {
					Relater relater = oase.getRelater();
					Record[] media = relater.getRelated(aTrack.getRecord(), "base_medium", TrackLogic.REL_TAG_MEDIUM);
					TreeMap sortedMediaMap = new TreeMap();
					String mediumName, mediumDesc;
					for (int j = 0; j < media.length; j++) {
						nextMedium = media[j];

						// Create waypoint
						// See http://www.topografix.com/GPX/1/1/#type_wptType
						JXElement mediumElm = new JXElement("wpt");

						// First lat/lon/time/ele
						nextLocations = relater.getRelated(nextMedium, Location.TABLE_NAME, "medium");
						if (nextLocations.length == 1) {
							// Has location: add location attrs
							nextLoc = nextLocations[0];
						} else {
							continue;
						}

						mediumElm.setAttr("lon", nextLoc.getRealField("lon"));
						mediumElm.setAttr("lat", nextLoc.getRealField("lat"));
						mediumElm.setChildText("ele", nextLoc.getRealField("ele") + "");

						long ctime = nextMedium.getLongField("creationdate");
						mediumElm.setChildText("time", GPX_TIME_FORMAT.format(new Date(ctime)));

						mediumName = nextMedium.getStringField("name");
						if (mediumName != null) {
							mediumElm.setChildText("name", mediumName);
						}
						// Optional description
						mediumDesc = nextMedium.getStringField("description");
						if (mediumDesc != null) {
							mediumElm.setChildText("desc", mediumDesc);
						}

						// Set type to mime type
						mediumElm.setChildText("type", nextMedium.getStringField("mime"));

						// Set link to medium
						int id = nextMedium.getId();
						mediumElm.setChildText("link", mediumURL + id);

						// Sort media by timestamp
						sortedMediaMap.put(new Long(ctime), mediumElm);
					}

					// Get media elements in time-sorted order
					Collection sortedMedia = sortedMediaMap.values();

					// Add to media element
					gpx.addChildren(new Vector(sortedMedia));
				} catch (OaseException oe) {
					log.warn("Error handling medium for trackId=" + trackId, oe);
				} catch (Throwable t) {
					log.error("Serious Error handling medium for trackId=" + trackId, t);
				}
			}


			// Add points add trkseg, separated when open/close etc tags reached
			// Create trk
			JXElement trk = new JXElement("trk");
			JXElement nextElement = null;
			String nextTag = null;
			JXElement nextSegment = null;
			JXElement nextPoint = null;
			GPSSample nextSample;
			int ptCount=0;
			for (int i = 0; i < elements.size(); i++) {
				nextElement = (JXElement) elements.get(i);
				nextTag = nextElement.getTag();
				if (!nextTag.equals(Track.TAG_PT)) {
					if (nextSegment != null) {
						if (nextSegment.hasChildren()) {
							trk.addChild(nextSegment);
						}
						nextSegment = null;
					}
					nextSegment = new JXElement("trkseg");
				} else {
					if (nextSegment == null) {
						nextSegment = new JXElement("trkseg");
					}
					nextPoint = new JXElement("trkpt");
					nextPoint.setAttr("lon", nextElement.getAttr(Track.ATTR_LON));
					nextPoint.setAttr("lat", nextElement.getAttr(Track.ATTR_LAT));


					// Time use time of GPS fix
					JXElement fixTime = new JXElement("time");
					fixTime.setText(GPX_TIME_FORMAT.format(new Date(nextElement.getLongAttr(Track.ATTR_TIME))));
					nextPoint.addChild(fixTime);

					// Elevation
					JXElement ele = new JXElement("ele");
					ele.setText(nextElement.getAttr(Track.ATTR_ELE));
					nextPoint.addChild(ele);

					// Possibly filter out samples too close to each other
					// and excess samples.
					nextSample = new GPSSample(nextPoint.getDoubleAttr("lat"), nextPoint.getDoubleAttr("lat"));
					if (aTrackPointFilter.filter(nextSample)) {
						nextSegment.addChild(nextPoint);
						ptCount++;
					}
				}
			}

			// Last segment
			if (nextSegment != null && nextSegment.hasChildren()) {
				trk.addChild(nextSegment);
			}

			gpx.addChild(trk);
			log.info(aTrack.getName() + " GPX export ptCount=" + ptCount + " discardCount=" + aTrackPointFilter.getDiscardCount());

			return gpx;
		} catch (Throwable t) {
			log.warn("Unexpected error in toGPX trackId=" + aTrack.getId(), t);
			throw new UtopiaException("Unexpected error in toGPX trackId=" + aTrack.getId(), t);
		}
	}

	private static class TrackPointFilter {
		private double minDistKm;
		private GPSSample lastSample;
		private int discardCount;
		private int increment;
		private int totalPoints;

		public TrackPointFilter(long aMinDistMeter, int aMaxPoint, int aTrackPointCount) {
			minDistKm = ((double) aMinDistMeter / 1000d);
			increment = (aMaxPoint > 0) ? aTrackPointCount/aMaxPoint + 1 : 1;
		}

		public boolean filter(GPSSample aGPSSample) {
			totalPoints++;
			if (lastSample == null) {
				lastSample = aGPSSample;
				return true;
			}

			// Filter passes if:
			// 1. distance greater/equal minimum distance
			// 2. increment reached
			if (lastSample.distance(aGPSSample) >= minDistKm && (totalPoints % increment == 0)) {
				lastSample = aGPSSample;
				return true;
			} else {
				discardCount++;
				return false;
			}
		}

		public int getDiscardCount() {
			return discardCount;
		}

		public void reset() {
			lastSample = null;
		}
	}
}

/*
* $Log: TrackExport.java,v $
* Revision 1.10  2006-07-06 23:06:48  just
* no message
*
* Revision 1.9  2006-04-27 09:51:06  just
* trackfiltering improved
*
* Revision 1.8  2006/02/12 19:55:25  just
* *** empty log message ***
*
* Revision 1.7  2005/12/07 12:49:01  just
* *** empty log message ***
*
* Revision 1.6  2005/10/20 15:37:20  just
* *** empty log message ***
*
* Revision 1.5  2005/10/18 15:23:51  just
* *** empty log message ***
*
* Revision 1.4  2005/10/13 13:15:58  just
* *** empty log message ***
*
* Revision 1.3  2005/10/07 15:23:09  just
* *** empty log message ***
*
* Revision 1.2  2005/10/06 14:34:51  just
* *** empty log message ***
*
* Revision 1.1  2005/10/06 13:51:08  just
* *** empty log message ***
*
* empty log message ***
*
*
*/

