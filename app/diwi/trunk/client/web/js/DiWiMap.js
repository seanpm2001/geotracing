/**
 * Manages OpenLayers Map object.
 *
 * author: Just van den Broecke
 */
var MAP = {
	BOUNDS: new OpenLayers.Bounds(4.724717, 51.813062, 5.752441, 52.486596),
	CENTER: new OpenLayers.LonLat(5.3790321, 52.171682),
	CONTROLS: [new OpenLayers.Control.MouseDefaults(), new OpenLayers.Control.PanZoomBar(),new OpenLayers.Control.LayerSwitcher(),new OpenLayers.Control.MousePosition()],
	DIV_ID: 'map',
	IMAGE_FORMAT: 'image/png',
	MAX_RESOLUTION: 'auto',
	NUM_ZOOMLEVELS: 10,
	PROJECTION: 'EPSG:4326',
	WMS_URL: 'http://test.digitalewichelroede.nl/map84',
	ZOOM: 12,
/** OL map object. */
	map: null,

	keyArray : new Array(),

/** Add Google Map key and reg exp for regexp URL, e.g. "^https?://www.geotracing.com/.*" */
	addKey: function(aName, aKey, aURLRegExp) {
		MAP.keyArray[aName] = { key: aKey, reg: aURLRegExp };
	},

	addRouteLayer: function(aRouteId) {

		var routeLayer = new OpenLayers.Layer.WMS.Untiled('Route (#' + aRouteId + ')',
				// MAP.WMS_URL + '?ID=' + aRouteId + '&LAYERS=topnl_raster,single_diwi_route');
				MAP.WMS_URL + '?ID=' + aRouteId, {layers: 'single_diwi_route', format: MAP.IMAGE_FORMAT, transparent: true});
		MAP.map.addLayer(routeLayer);
	},

	addTOPNLRasterLayer: function() {
		var topNLLayer = new OpenLayers.Layer.WMS.Untiled("Topografische Kaart",
				MAP.WMS_URL, {layers: 'topnl_raster', format: MAP.IMAGE_FORMAT});

		MAP.map.addLayer(topNLLayer);
	},

	addGoogleSatLayer: function() {
		var googles = new OpenLayers.Layer.Google("Google Maps Satelliet", { 'type': G_SATELLITE_MAP });

		MAP.map.addLayer(googles);
	},

	create: function() {
		MAP.destroy();

		MAP.map = new OpenLayers.Map(DH.getObject(MAP.DIV_ID), {
			controls: MAP.CONTROLS,
			maxExtent: MAP.BOUNDS,
			projection: MAP.PROJECTION,
			maxResolution: MAP.MAX_RESOLUTION
			// numZoomLevels: MAP.NUM_ZOOMLEVELS
		}
				);

		MAP.addGoogleSatLayer();
		MAP.addTOPNLRasterLayer();

		MAP.map.setCenter(MAP.CENTER, MAP.ZOOM);
	},

	destroy: function() {
		if (MAP.map != null) {
			// MAP.map.destroy();
			// DH.setHTML(MAP.DIV_ID, ' ');
			MAP.map = null;
		}
	},

	init: function() {

	},

	loadGoogleMapScript: function(aVersion) {
		var version = '2';
		if (aVersion) {
			version = aVersion;
		}

		// Find the GMap key based on our URL regular expression
		for (k in MAP.keyArray) {
			var key = MAP.keyArray[k];
			var regexp = new RegExp(key.reg);

			if (regexp.test(window.location.href)) {
				// alert('load key=' + key.key);
				document.write('<' + 'script src="http://maps.google.com/maps?file=api&amp;v=' + version + '&amp;key=' + key.key + '" type="text/javascript"><' + '/script>');
				break;
			}
		}
	}
}

MAP.addKey('live',
		'ABQIAAAAD3bxjYK2kuWoA5XU4dh89xSegWNS_BtfE0_SbjkW1pkdsveSEhS9935cVFSC9wEMB5FdZntmVMpl2w',
		'^https?://www.digitalewichelroede.nl/.*');

MAP.addKey('test',
		'ABQIAAAAD3bxjYK2kuWoA5XU4dh89xSY8w_XxQ1lplxjwMXbYkbJSPSvpRTetUEIpZ2OTf-U93olm8oLCoC29A',
		'^https?://test.digitalewichelroede.nl/.*');

MAP.addKey('local',
		'ABQIAAAAD3bxjYK2kuWoA5XU4dh89xSzCH91z57ocwwUF0G9rnam-69XfBSYstFMYwQaq5OD5kCUatNyH_JFqw',
		'^https?://local.diwi.nl/.*');

MAP.loadGoogleMapScript();