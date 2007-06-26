package nl.diwi.control;

import nl.diwi.logic.MapLogic;
import nl.diwi.logic.NavigationLogic;
import nl.diwi.logic.TrafficLogic;
import nl.diwi.logic.TripLogic;
import nl.diwi.util.Constants;
import nl.diwi.util.ProjectionConversionUtil;
import nl.justobjects.jox.dom.JXElement;
import org.geotracing.handler.HandlerUtil;
import org.geotracing.handler.Track;
import org.geotracing.handler.TrackLogic;
import org.geotracing.handler.Location;
import org.keyworx.common.log.Log;
import org.keyworx.common.log.Logging;
import org.keyworx.common.util.Sys;
import org.keyworx.utopia.core.control.DefaultHandler;
import org.keyworx.utopia.core.data.ErrorCode;
import org.keyworx.utopia.core.data.UtopiaException;
import org.keyworx.utopia.core.session.UtopiaRequest;
import org.keyworx.utopia.core.session.UtopiaResponse;
import org.keyworx.utopia.core.util.Oase;
import org.keyworx.oase.api.*;
import org.postgis.Point;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Vector;

public class NavigationHandler extends DefaultHandler implements Constants {

    TripLogic tripLogic;

    /**
     * Processes the Client Request.
     *
     * @param anUtopiaReq A UtopiaRequest
     * @return A UtopiaResponse.
     * @throws UtopiaException standard Utopia exception
     */
    public UtopiaResponse processRequest(UtopiaRequest anUtopiaReq) throws UtopiaException {
        Log log = Logging.getLog(anUtopiaReq);
        String service = anUtopiaReq.getServiceName();
        log.trace("Handling request for service=" + service);

        JXElement response;
        try {
            if (service.equals(NAV_GET_MAP)) {
                response = getMap(anUtopiaReq);
            } else if (service.equals(NAV_START)) {
                response = startNavigation(anUtopiaReq);
            } else if (service.equals(NAV_STOP)) {
                response = stopNavigation(anUtopiaReq);
            } else if (service.equals(NAV_POINT)) {
                response = handlePoint(anUtopiaReq);
            } else if (service.equals(NAV_ACTIVATE_ROUTE)) {
                response = activateRoute(anUtopiaReq);
            } else if (service.equals(NAV_DEACTIVATE_ROUTE)) {
                response = deactivateRoute(anUtopiaReq);
            } else if (service.equals(NAV_ADD_MEDIUM)) {
                response = addMedium(anUtopiaReq);
            } else if (service.equals(NAV_GET_STATE)) {
                response = getState(anUtopiaReq);
            } else if (service.equals(NAV_TOGGLE_UGC)) {
                response = toggleUGC(anUtopiaReq);
            } else {
                // May be overridden in subclass
                response = unknownReq(anUtopiaReq);
            }

            // store the traffic
            //TrafficLogic t = new TrafficLogic(anUtopiaReq.getUtopiaSession().getContext().getOase());
            //t.storeTraffic(anUtopiaReq.getUtopiaSession().getContext().getUserId(), anUtopiaReq.getRequestCommand(), response);

            tripLogic = new TripLogic(anUtopiaReq.getUtopiaSession().getContext().getOase());

        } catch (UtopiaException ue) {
            log.warn("Negative response service=" + service, ue);
            response = createNegativeResponse(service, ue.getErrorCode(), ue.getMessage());
        } catch (Throwable t) {
            log.error("Unexpected error service=" + service, t);
            response = createNegativeResponse(service, ErrorCode.__6005_Unexpected_error, "Unexpected error in request " + t);
        }

        // Always return a response
        log.trace("Handled service=" + service + " response=" + response.getTag());
        return new UtopiaResponse(response);
    }

    private JXElement deactivateRoute(UtopiaRequest anUtopiaReq) throws UtopiaException {
        NavigationLogic logic = createLogic(anUtopiaReq);
        int personId = Integer.parseInt(anUtopiaReq.getUtopiaSession().getContext().getUserId());

        logic.deactivateRoute(personId);

        tripLogic.storeEvent(anUtopiaReq.getUtopiaSession().getContext().getUserId(), anUtopiaReq.getRequestCommand());

        return createResponse(NAV_DEACTIVATE_ROUTE);
    }
    private JXElement toggleUGC(UtopiaRequest anUtopiaReq) throws UtopiaException {
        NavigationLogic logic = createLogic(anUtopiaReq);
        logic.toggleUGC(anUtopiaReq.getUtopiaSession().getContext().getUserId());

        tripLogic.storeEvent(anUtopiaReq.getUtopiaSession().getContext().getUserId(), anUtopiaReq.getRequestCommand());

        return createResponse(NAV_TOGGLE_UGC);
    }


    private JXElement activateRoute(UtopiaRequest anUtopiaReq) throws UtopiaException {
        NavigationLogic logic = createLogic(anUtopiaReq);
        JXElement reqElm = anUtopiaReq.getRequestCommand();
        int personId = Integer.parseInt(anUtopiaReq.getUtopiaSession().getContext().getUserId());
        int routeId = Integer.parseInt(anUtopiaReq.getRequestCommand().getAttr(ID_FIELD));
        String initString = anUtopiaReq.getRequestCommand().getAttr(INIT_FIELD);
        boolean init = false;
        if(initString!=null && initString.toLowerCase().equals("true")){
            init = true;
        }

        logic.activateRoute(routeId, personId, init);

        tripLogic.storeEvent(anUtopiaReq.getUtopiaSession().getContext().getUserId(), reqElm);
        
        return createResponse(NAV_ACTIVATE_ROUTE);
    }

    private JXElement getState(UtopiaRequest anUtopiaReq) throws UtopiaException {
        NavigationLogic logic = createLogic(anUtopiaReq);
        int personId = Integer.parseInt(anUtopiaReq.getUtopiaSession().getContext().getUserId());

        JXElement response = createResponse(NAV_GET_STATE);
        Record route = logic.getActiveRoute(personId);
        if(route!=null){
            response.setAttr("routeid", route.getId());
        }

        return response;
    }

    private JXElement handlePoint(UtopiaRequest anUtopiaReq) throws UtopiaException {
        JXElement reqElm = anUtopiaReq.getRequestCommand();
        TrackLogic trackLogic = new TrackLogic(anUtopiaReq.getUtopiaSession().getContext().getOase());
        NavigationLogic navLogic = new NavigationLogic(anUtopiaReq.getUtopiaSession().getContext().getOase());

        //result contains 'pt' elements with everything filled out if an EMEA string was sent.
        Vector result = trackLogic.write(reqElm.getChildren(), HandlerUtil.getUserId(anUtopiaReq));

        //Get Point from pt elements
        JXElement ptElement = (JXElement) (reqElm.getChildren().get(0));
        Point point = new Point(Double.parseDouble(ptElement.getAttr(LAT_FIELD)), Double.parseDouble(ptElement.getAttr(LON_FIELD)), 0);
        point.setSrid(EPSG_WGS84);
        result.addAll(navLogic.checkPoint(point, HandlerUtil.getUserId(anUtopiaReq)));

        JXElement response = createResponse(NAV_POINT);
        response.addChildren(result);

        return response;
    }

    private JXElement stopNavigation(UtopiaRequest anUtopiaReq) throws UtopiaException {
        TrackLogic trackLogic = new TrackLogic(anUtopiaReq.getUtopiaSession().getContext().getOase());

        // Resume current Track for this user
        trackLogic.suspend(HandlerUtil.getUserId(anUtopiaReq), System.currentTimeMillis());
        // store the event
        tripLogic.storeEvent(anUtopiaReq.getUtopiaSession().getContext().getUserId(), anUtopiaReq.getRequestCommand());
        // close this trip
        tripLogic.closeTrip(anUtopiaReq.getUtopiaSession().getContext().getUserId());
        
        // Create and return response with open track id.
        return createResponse(NAV_STOP);
    }

    private JXElement startNavigation(UtopiaRequest anUtopiaReq) throws UtopiaException {
        Oase oase = anUtopiaReq.getUtopiaSession().getContext().getOase();
        TrackLogic trackLogic = new TrackLogic(oase);

        // Resume current Track for this user
        Track track = trackLogic.resume(HandlerUtil.getUserId(anUtopiaReq), Track.VAL_DAY_TRACK, System.currentTimeMillis());

        // close previous trip
        tripLogic.closeTripByTime(anUtopiaReq.getUtopiaSession().getContext().getUserId());

        // and store the request
        tripLogic.storeEvent(anUtopiaReq.getUtopiaSession().getContext().getUserId(), anUtopiaReq.getRequestCommand());

        // relate the track to the trip
        Record trip = tripLogic.getActiveTrip(anUtopiaReq.getUtopiaSession().getContext().getUserId());
        // relate the track to the trip
        try{
            oase.getRelater().relate(trip, track.getRecord());
        }catch(Throwable t){
            throw new UtopiaException(t);
        }

        // Create and return response with open track id.
        return createResponse(NAV_START);
    }

    protected NavigationLogic createLogic(UtopiaRequest anUtopiaReq) throws UtopiaException {
        return new NavigationLogic(anUtopiaReq.getUtopiaSession().getContext().getOase());
    }

    private JXElement getMap(UtopiaRequest anUtopiaReq) throws UtopiaException {
        MapLogic logic = new MapLogic();
        JXElement reqElm = anUtopiaReq.getRequestCommand();

        int height = Integer.parseInt(reqElm.getAttr(HEIGHT_FIELD));
        int width = Integer.parseInt(reqElm.getAttr(WIDTH_FIELD));
        double llbLat = Double.parseDouble(reqElm.getAttr(LLB_LAT_ATTR));
        double llbLon = Double.parseDouble(reqElm.getAttr(LLB_LON_ATTR));
        double urtLat = Double.parseDouble(reqElm.getAttr(URL_LAT_ATTR));
        double urtLon = Double.parseDouble(reqElm.getAttr(URT_LON_ATTR));
//		"BOX(5.37404870986938 52.1408767700195,5.40924692153931 52.172737121582)"

//		double llbLon = 5.37404870986938;
//		double llbLat = 52.1408767700195;
//		double urtLon = 5.40924692153931;
//		double urtLat = 52.172737121582;


        Point llb = ProjectionConversionUtil.WGS842RD(new Point(llbLon, llbLat));
        Point urt = ProjectionConversionUtil.WGS842RD(new Point(urtLon, urtLat));

        String mapURL = logic.getMapURL(urt, llb, height, width);
        JXElement response = createResponse(NAV_GET_MAP);
        try {
            response.setAttr(URL_FIELD, URLEncoder.encode(mapURL, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            throw new UtopiaException("Exception in getMap", e);
        }

        // and store the request
        tripLogic.storeEvent(anUtopiaReq.getUtopiaSession().getContext().getUserId(), anUtopiaReq.getRequestCommand());

        return response;
    }

    /*
        <nav-add-medium-req id="[mediumid]" />
        <play-add-medium-rsp locationid="[locationid]" />
    */    
    public JXElement addMedium(UtopiaRequest anUtopiaReq) throws OaseException, UtopiaException {
		JXElement requestElement = anUtopiaReq.getRequestCommand();
		String mediumIdStr = requestElement.getAttr(ID_FIELD);
		HandlerUtil.throwOnNonNumAttr(ID_FIELD, mediumIdStr);

		Oase oase = HandlerUtil.getOase(anUtopiaReq);
		Relater relater = oase.getRelater();

		// Create Location for medium and relate to track and location tables
		Record medium = oase.getFinder().read(Integer.parseInt(mediumIdStr));
		int mediumId = medium.getId();

		// Person is person related to medium
		// not neccessarily the person logged in (e.g. admin for email upload)
		Record person = relater.getRelated(medium, PERSON_TABLE, null)[0];
		int personId = person.getId();

		// Determine timestamp: we use the time that the medium was sent
		long timestamp = Sys.now();
		if (requestElement.hasAttr(TIME_FIELD)) {
			// if a timestamp was provided we assume we already have the correct creation time
			timestamp = requestElement.getLongAttr(TIME_FIELD);
		}

		// First determine medium location and add to track
		TrackLogic trackLogic = new TrackLogic(oase);

		// Adds medium to track and creates location object for timestamp
		// (where the player was at that time)
		Location location = trackLogic.createLocation(personId, mediumId, timestamp, TrackLogic.REL_TAG_MEDIUM);

		// We either have location or an exception here
		JXElement rsp = createResponse(NAV_ADD_MEDIUM);
		rsp.setAttr(LOCATION_ID_FIELD, location.getId());

        // store the event        
        tripLogic.storeEvent(anUtopiaReq.getUtopiaSession().getContext().getUserId(), requestElement);

        return rsp;
	}

    protected JXElement unknownReq(UtopiaRequest anUtopiaReq) throws UtopiaException {
        String service = anUtopiaReq.getServiceName();
        Logging.getLog(anUtopiaReq).warn("Unknown service " + service);
        return createNegativeResponse(service, ErrorCode.__6000_Unknown_command, "unknown service: " + service);
    }

}
