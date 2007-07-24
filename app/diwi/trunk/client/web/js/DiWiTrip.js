/**
 * Trip display and such.
 *
 * author: Just van den Broecke
 */
var TRIP = {
	trips: null,
	mediumPopup: null,

	curTrip: {
		points: null,
		media: null,
		poiHits: null,
		ugcHits: null
	},

	onShowTrip: function(rsp) {
		DH.displayOff('triplist');
		DIWIAPP.pr('Mijn trip ingetekend.');
		DH.displayOn('triplistbacklink');
		MAP.show();
		MAP.addMarkerLayer('Mijn trip');

		TRIP.curTrip.points = rsp.getElementsByTagName('pt');
		TRIP.curTrip.media = rsp.getElementsByTagName('medium');
		TRIP.curTrip.poiHits = null;
		TRIP.curTrip.ugcHits = null;

		TRIP.showTripPoints();
		TRIP.showTripMedia();
	},

	onShowTrips: function(rsp) {
		TRIP.trips = TRIP.rsp2Records(rsp);

		var tripsCont = ' ';
		var nextTrip;
		for (var i = 0; i < TRIP.trips.length; i++) {
			nextTrip = TRIP.trips[i];
			tripsCont += '<a onclick="TRIP.showTrip(\'' + nextTrip.getField('id') + '\');" href="#"> ' + nextTrip.getField('name') + '</a><br/>';
		}
		DH.setHTML('triplist', tripsCont);
		DH.displayOn('triplist');
		DIWIAPP.pr('Hiernaast een lijst van uw gemaakte tochten.');
	},

	showTripMedia: function() {
		var x,y,img,w,h,medium;
		img = 'media/images/icon-trace.png';
		w = 10;
		h = 10;

		for (var i = 0; i < TRIP.curTrip.media.length; i++) {
			TRIP.showTripMediumMarker(i);

			//MAP.addMarker(x, y, img, w, h);
		}
	},

	showTripMediumMarker: function(index) {
		// alert('medium');
		var medium = TRIP.curTrip.media[index];
		var x = medium.getAttribute('x');
		var y = medium.getAttribute('y');
		var marker = new OpenLayers.Marker(new OpenLayers.LonLat(x, y));
		this.fun = function(evt) {
			TRIP.showMedium(index);
			Event.stop(evt);
		};

		marker.events.register('mousedown', marker, this.fun);
		MAP.overlays['markers'].addMarker(marker);
	},

	showMedium: function(index) {
		// alert('medium');
		var medium = TRIP.curTrip.media[index];
		var x = medium.getAttribute('x');
		var y = medium.getAttribute('y');
		var id = medium.getAttribute('id');
		var kind = medium.getAttribute('kind');
		var name = medium.getAttribute('name');
		var mediumURL = DIWIAPP.PORTAL + '/media.srv';

		var html = '<img src="' + mediumURL + '?id=' + id + '&resize=240" /><br/>' + name;
		if (kind == 'video') {
			html = '<a href="' + mediumURL + '?id=' + id + '" target="_new" >view video: ' + name + '</a>';
		} else if (kind == 'text') {
			html = name + '<br/><i>'+ DH.getURL(mediumURL + '?id=' + id) + '</i>';
		}
		var mediumPopup = new OpenLayers.Popup("mediumpopup",
				new OpenLayers.LonLat(x, y),
				new OpenLayers.Size(320, 240),
				html,
				true);
		MAP.map.addPopup(mediumPopup);

	},

	showTripPoints: function() {
		var x,y,img,w,h,pt,xsw = 0,ysw = 0,xne = 0,yne = 0;
		img = 'media/images/icon-trace.png';
		w = 10;
		h = 10;

		for (var i = 0; i < TRIP.curTrip.points.length; i++) {
			pt = TRIP.curTrip.points[i];
			x = pt.getAttribute('x');
			y = pt.getAttribute('y');
			if (i == 0) {
				xsw = xne = x;
				ysw = yne = y;
			} else {
				if (x < xsw) {
					xsw = x;
				} else if (x > xne) {
					xne = x;
				}
				if (y < ysw) {
					ysw = y;
				} else if (y > yne) {
					yne = y;
				}
			}

			MAP.addMarker(x, y, img, w, h);
		}

		if (xsw != 0 & xne != 0 && xne != xsw) {
			var bounds = new OpenLayers.Bounds(xsw, ysw, xne, yne);
			MAP.map.zoomToExtent(bounds);
		}
	},

	showTrips: function() {
		DH.displayOff('triplistbacklink');
		KW.DIWI.gettrips(TRIP.onShowTrips, DIWIAPP.personId);
		MAP.hide();
	},

	showTrip: function(anId) {
		KW.DIWI.gettrip(TRIP.onShowTrip, anId);
		DIWIAPP.pr('Trip ophalen...');
		return false;
	},

	rsp2Records: function(anRsp) {
		var records = [];

		// Convert xml doc to array of Record objects
		var recordElements = anRsp.childNodes;
		for (i = 0; i < recordElements.length; i++) {
			records.push(new XMLRecord(recordElements[i]));
		}
		return records;
	}
}
