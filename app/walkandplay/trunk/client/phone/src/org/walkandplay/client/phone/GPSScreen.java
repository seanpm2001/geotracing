package org.walkandplay.client.phone;

import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.Graphics;
import javax.microedition.rms.RecordStoreException;
import javax.bluetooth.*;
import java.util.Hashtable;

public class GPSScreen extends Canvas implements DiscoveryListener {

    private Hashtable devices = new Hashtable(2);
    private String[] discoveredDevices = new String[20];
    private LocalDevice device;
	private RemoteDevice remoteDevice;
	private String connectionURL;
	private ServiceRecord serviceRecord;
	private String deviceName;
    private int deviceCounter = 0;
    private static Preferences preferences;

    public static final String RMS_STORE_NAME = "GPS";
	public static final String RMS_GPS_NAME = "name";
	public static final String RMS_GPS_URL = "url";

    // paint vars
    int w, h, fh;
    Font f;

    int x0, y0;
    int midx;

    private WP midlet;

    // image objects
    private Image logo, textArea, bg, backBt;

    // screenstates
    private int screenStat = 0;
    private final static int HOME_STAT = 0;
    private final static int DEVICES_STAT = 1;
    private final static int DEVICE_SELECTED_STAT = 2;
    private final static int SEARCHING_SERVICES_STAT = 3;

    private int fontType = Font.FACE_MONOSPACE;

    public GPSScreen(WP aMidlet) {
        try {
            midlet = aMidlet;
            w = getWidth();
            h = getHeight();
            setFullScreenMode(true);

            // load all images
            logo = Image.createImage("/logo.png");
            textArea = Image.createImage("/text_area.png");
            backBt = Image.createImage("/back_button.png");
            bg = Image.createImage("/bg.png");
        } catch (Throwable t) {
            log("could not load all images : " + t.toString());
        }
    }

    static public String getGPSName() {
		return getPreferences().get(RMS_GPS_NAME);
	}

	static public String getGPSURL() {
		return getPreferences().get(RMS_GPS_URL);
	}

    /**
	 * Start device inquiry. Your application call this method to start inquiry.
	 */
	public void searchDevices() {
		try {
			// initialize the JABWT stack
			device = LocalDevice.getLocalDevice(); // obtain reference to singleton
			device.setDiscoverable(DiscoveryAgent.GIAC); // set Discover Mode
			device.getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, this);
			log("Searching GPS Devices...");
		} catch (Throwable e) {
			log("cannot start search ex=" + e + "]");
		}
	}

    /**
	 * Searches for a service from the gps device.
	 */
	private void searchServices() {
		try {
			log("Start service search" + remoteDevice.getFriendlyName(false));
			// Use the serial UUID for connection
			UUID[] serviceUUIDs = new UUID[1];
			serviceUUIDs[0] = new UUID(0x1101);
			DiscoveryAgent agent = device.getDiscoveryAgent();
			agent.searchServices(null, serviceUUIDs, remoteDevice, this);

		} catch (Throwable ex) {
			log("Error searchServices() " + ex);
		}
	}

    public synchronized void deviceDiscovered(RemoteDevice aRemoteDevice, DeviceClass deviceClass) {
        try {
            String name = aRemoteDevice.getFriendlyName(false);
            log("found: [" + name + "]\n" + aRemoteDevice.getBluetoothAddress());

            // BugFix: some phones do not return friendly name (nokia 6230) ?
            if (name == null) {
                name = "unnamed";
            }
            if (devices.containsKey(name)) {
                name += "-2";
            }
            deviceCounter++;
            discoveredDevices[deviceCounter - 1] = name;
            devices.put(name, aRemoteDevice);
            log("wait, searching further...");
        } catch (Throwable t) {
            log("ERROR getting name");
        }
    }

    public synchronized void inquiryCompleted(int complete) {
		log("Device search complete");
        String[] temp = new String[deviceCounter];
        for(int i=0;i<deviceCounter;i++){
            temp[i] = discoveredDevices[i];
        }
        discoveredDevices = temp;
        ScreenUtil.resetMenu();
        screenStat = DEVICES_STAT;
        repaint();
    }

	public synchronized void servicesDiscovered(int transId, ServiceRecord[] records) {
		log("[srvDisc] #" + records.length);

		if (records.length > 0) {
			serviceRecord = records[0];
			connectionURL = serviceRecord.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
			log("[SERVICE_FOUND] " + connectionURL);
		} else {
			log("[NO_RECORDS_FOUND] ");
		}
	}

	public synchronized void serviceSearchCompleted(int transId, int aStatus) {
		if (serviceRecord == null) {
			log("completed " + aStatus + " [NO_SERVICES_FOUND] ");
			log("hmm, press Ok or Exit and try again..");
		} else {
			try {
				getPreferences().put(RMS_GPS_NAME, deviceName);
				getPreferences().put(RMS_GPS_URL, connectionURL);
				getPreferences().save();
			} catch (RecordStoreException e) {
				log("RMS Error" + e);
			}

			log("OK using GPS named ");
			log("[" + deviceName + "]");
			log("url=");
			log("[" + connectionURL + "]");
			log(" ");
			screenStat = DEVICE_SELECTED_STAT;
            midlet.setGPSStat(true);
            repaint();
        }
	}

    private static Preferences getPreferences() {
		try {
			if (preferences == null) {
				preferences = new Preferences(RMS_STORE_NAME);
			}
			return preferences;
		} catch (RecordStoreException e) {
			return null;
		}
	}


    // passes log msg to the main log method
    private void log(String aMsg) {
        midlet.log(aMsg);
    }

    /**
     * Draws the screen.
     *
     * @param g The graphics object.
     */
    public void paint(Graphics g) {
        if (f == null) {
            g.setColor(0, 0, 0);
            f = Font.getFont(fontType, Font.STYLE_PLAIN, Font.SIZE_SMALL);
            g.setFont(f);
            fh = f.getHeight();
        }

        g.setColor(0, 0, 0);
        g.drawImage(bg, 0, 0, Graphics.TOP | Graphics.LEFT);
        g.drawImage(logo, 5, 5, Graphics.TOP | Graphics.LEFT);
        g.drawImage(textArea, 5, logo.getHeight() + 10, Graphics.TOP | Graphics.LEFT);

        switch (screenStat) {
            case HOME_STAT:
                String GPS = getGPSName();
                String text;
                if(GPS!=null && GPS.length()>0){
                    text = "You previously used a GPS with name " + GPS + ".";
                    text += "To change your GPS choose 'select gps' from the menu.";
                    String[] options = {"select gps"};
                    ScreenUtil.createMenu(g, f, h, fh, options);
                }else{
                    deviceCounter = 0;
                    searchDevices();
                    text = "Searching for a GPS device...";
                }

                ScreenUtil.drawText(g, text, 10, logo.getHeight() + 15, fh);
                ScreenUtil.setRightBt(g, h, w, backBt);
                break;
            case DEVICES_STAT:
                log("show devices in menu");
                text = "Choose your GPS device from the menu";
                ScreenUtil.drawText(g, text, 10, logo.getHeight() + 15, fh);
                ScreenUtil.createMenu(g, f, h, fh, discoveredDevices);
                break;
            case SEARCHING_SERVICES_STAT:
                text = "Completing GPS connection...";
                ScreenUtil.drawText(g, text, 10, logo.getHeight() + 15, fh);
                break;
             case DEVICE_SELECTED_STAT:
                text = "Your GPS device is stored";
                ScreenUtil.drawText(g, text, 10, logo.getHeight() + 15, fh);
                ScreenUtil.setRightBt(g, h, w, backBt);
                break;
        }
    }

    /**
     * Handles all key actions.
     *
     * @param key The Key that was hit.
     */
    public void keyPressed(int key) {
        log("screenstat: " + screenStat);
        log("key: " + key);
        log("getGameAction(key): " + getGameAction(key));
        // left soft key & fire
        if (key == -6 || key == -5 || getGameAction(key) == Canvas.FIRE) {
            switch (screenStat) {
                case DEVICES_STAT:
                    if(ScreenUtil.getSelectedMenuItem()!=0){
                        deviceName = discoveredDevices[(ScreenUtil.getSelectedMenuItem() -1)];
                        log("you selected [" + device + "]");
                        remoteDevice = (RemoteDevice) devices.get(deviceName);
                        searchServices();
                        screenStat = SEARCHING_SERVICES_STAT;
                    }
                    break;
            }
            // right softkey
        } else if (key == -7) {
            switch (screenStat) {
                case HOME_STAT:
                    midlet.setScreen(WP.HOME_SCREEN);
                    break;
                case DEVICE_SELECTED_STAT:
                    midlet.setScreen(WP.HOME_SCREEN);
                    break;
        }
        // left
        }else if (key == -3 || getGameAction(key) == Canvas.LEFT) {
            switch (screenStat) {
                case HOME_STAT:
                    break;
            }
            // right
        } else if (key == -4 || getGameAction(key) == Canvas.RIGHT) {
            switch (screenStat) {
                case HOME_STAT:
                    break;
            }
            // up
        } else if (key == -1 || getGameAction(key) == Canvas.UP) {
            switch (screenStat) {
                case DEVICES_STAT:
                    log("going up");
                    ScreenUtil.nextMenuItem();
                    break;
            }
            // down
        } else if (key == -2 || getGameAction(key) == Canvas.DOWN) {
            switch (screenStat) {
                case DEVICES_STAT:
                    log("going down");
                    ScreenUtil.prevMenuItem();
                    break;
            }
        } else if (getGameAction(key) == Canvas.KEY_STAR || key == Canvas.KEY_STAR) {

        } else if (getGameAction(key) == Canvas.KEY_POUND || key == Canvas.KEY_POUND) {
            midlet.setScreen(-1);
        } else if (key == -8) {

        } else {

        }

        repaint();
    }

}
