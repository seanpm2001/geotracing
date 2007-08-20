/*
 * Created on 04-Apr-2004 at 16:14:27.
 *
 * Copyright (c) 2004-2005 Robert Virkus / Enough Software
 *
 * This file is part of J2ME Polish.
 *
 * J2ME Polish is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * J2ME Polish is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with J2ME Polish; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Commercial licenses are also available, please
 * refer to the accompanying LICENSE.txt or visit
 * http://www.j2mepolish.org for details.
 */
package org.walkandplay.client.phone;

import de.enough.polish.util.Locale;

import javax.microedition.lcdui.*;
import javax.microedition.midlet.MIDlet;
import javax.microedition.midlet.MIDletStateChangeException;

import org.walkandplay.client.phone.Log;
import org.geotracing.client.Util;

public class WPMidlet extends MIDlet implements CommandListener {

    private WPMidlet midlet;
    private List menuScreen;
    private SelectGameDisplay selectGameDisplay;
    private CreateDisplay createDisplay;
    private AppStartDisplay activeApp;

    public final static String KW_URL = "kw-url";
    public final static String KW_USER = "kw-user";
    public final static String KW_PASSWORD = "kw-password";
    public final static String KW_SERVER = "kw-server";
    public final static String KW_PORT = "kw-port";
    public final static String KW_APP = "kw-app";
    public final static String KW_ROLE = "kw-role";
    public final static String VOLUME = "volume";

    public WPMidlet() {
        super();
        midlet = this;
        //setHome();
        //Display.getDisplay(this).setCurrent(new SplashDisplay(this, 1));
        new VersionChecker().check();
    }

    public void setHome() {
        //#style mainScreen
        menuScreen = new List("Mobile City", List.IMPLICIT);
        //#style mainPlayCommand
        menuScreen.append(Locale.get("menu.Play"), null);
        //#style mainTraceCommand
        menuScreen.append(Locale.get("menu.Create"), null);
        //#style mainGPSCommand
        menuScreen.append(Locale.get("menu.GPS"), null);
        //#style mainSettingsCommand
        menuScreen.append(Locale.get("menu.Settings"), null);
        //#style mainHelpCommand
        menuScreen.append(Locale.get("menu.Help"), null);
        //#style mainQuitCommand
        menuScreen.append(Locale.get("menu.Quit"), null);
        //#style mainLogCommand
        menuScreen.append(Locale.get("menu.Log"), null);
        //#style mainLogCommand
        /*menuScreen.append("test display", null);*/
        //#style mainLogCommand
        /*menuScreen.append("video display", null);*/
        //#style mainLogCommand
        /*menuScreen.append("video form", null);*/
        //#style mainLogCommand
        /*menuScreen.append("GPS test display", null);*/
        //#style mainLogCommand
        /*menuScreen.append("Friend Finder", null);*/

        menuScreen.setCommandListener(this);
        Display.getDisplay(this).setCurrent(menuScreen);
    }

    protected void startApp() throws MIDletStateChangeException {
        Display.getDisplay(this).setCurrent(menuScreen);
    }

    protected void pauseApp() {
        // ignore
    }

    protected void destroyApp(boolean unconditional) throws MIDletStateChangeException {
        // just quit
    }

    public void commandAction(Command cmd, Displayable screen) {
        if (screen == menuScreen) {
            if (cmd == List.SELECT_COMMAND) {
                goToScreen(menuScreen.getSelectedIndex());
            }
        }
    }

    public SelectGameDisplay getPlayApp() {
        return selectGameDisplay;
    }

    public CreateDisplay getCreateApp() {
        return createDisplay;
    }

    public String getKWUrl() {
        return getAppProperty(KW_URL);
    }

    public String getKWServer() {
        return getAppProperty(KW_SERVER);
    }

    public String getKWUser() {
        return getAppProperty(KW_USER);
    }

    public String getKWPassword() {
        return getAppProperty(KW_PASSWORD);
    }

    public int getKWPort() {
        return Integer.parseInt(getAppProperty(KW_PORT));
    }

    public String getKWApp() {
        return getAppProperty(KW_APP);
    }

    public String getKWRole() {
        return getAppProperty(KW_ROLE);
    }

    public int getVolume() {
        return Integer.parseInt(getAppProperty(VOLUME));
    }

    public AppStartDisplay getActiveApp(){
        return activeApp;
    }

    private void goToScreen(int aScreenNr) {

        switch (aScreenNr) {
            case 0:
                // Play
                selectGameDisplay = new SelectGameDisplay(this);
                activeApp = selectGameDisplay;
                Display.getDisplay(this).setCurrent(activeApp);
                break;
            case 1:
                // Create
                createDisplay = new CreateDisplay(this);
                activeApp = createDisplay;
                Display.getDisplay(this).setCurrent(activeApp);
                break;
            case 2:
                // GPS
                Display.getDisplay(this).setCurrent(new GPSDisplay(this));
                break;
            case 3:
                // Settings
                Display.getDisplay(this).setCurrent(new SettingsDisplay(this));
                break;
            case 4:
                // Help
                Display.getDisplay(this).setCurrent(new HelpDisplay(this));
                break;
            case 5:
                // Quit
                Display.getDisplay(this).setCurrent(new SplashDisplay(this, -1));
                //notifyDestroyed();
                break;
            case 6:
                // Log
                Log.view(this);
                break;
            case 7:
                // test display
                Display.getDisplay(this).setCurrent(new TestDisplay(this));
                break;
            case 8:
                // video canvas
                Display.getDisplay(this).setCurrent(new VideoDisplay(this, "http://test.mlgk.nl/wp/media.srv?id=54225", null));
                break;
            case 9:
                // video form
                Display.getDisplay(this).setCurrent(new VideoForm(this, "http://test.mlgk.nl/wp/media.srv?id=54225"));
                break;
            case 10:
                // gps test display
                Display.getDisplay(this).setCurrent(new GPSTestDisplay(this));
                break;
            case 11:
                // friendfinder display
                Display.getDisplay(this).setCurrent(new FriendFinderDisplay(this));
                break;
        }
    }

    private class VersionChecker implements CommandListener {
		private Command EXIT_CMD = new Command("Exit", Command.EXIT, 1);
		private Command CONTINUE_CMD = new Command("Continue at you own risk", Command.SCREEN, 1);
        private Command GET_CMD = new Command("Get new version", Command.SCREEN, 1);

        private Form form;
        public void check() {
            String myVersion = getAppProperty("MIDlet-Version");

            String myName = getAppProperty("MIDlet-Name");
            String versionURL = getKWUrl() + "/ota/version.html";
            try {
                String theirVersion = Util.getPage(versionURL);
                if (theirVersion != null){
                    if(!theirVersion.trim().equals(myVersion)) {
                        //#style defaultscreen
                        form = new Form("Mobile City - Version check");

                        //#style alertinfo
                        form.append("Your " + myName + " version (" + myVersion + ") differs from the version (" + theirVersion + ") available for download. \nYou may want to upgrade to " + theirVersion);
                        form.addCommand(EXIT_CMD);                        
                        form.addCommand(GET_CMD);
                        form.addCommand(CONTINUE_CMD);

                        form.setCommandListener(this);
                        Display.getDisplay(midlet).setCurrent(form);
                    }else{
                        setHome();
                    }
                }else{
                    setHome();
                }
            } catch (Throwable t) {
                setHome();
            }
		}

		public void commandAction(Command command, Displayable screen) {
			if (command == EXIT_CMD) {
                Display.getDisplay(midlet).setCurrent(new SplashDisplay(midlet, -1));
            } else if (command == CONTINUE_CMD) {
                setHome();
            } else if (command == GET_CMD) {
                try {
                    midlet.platformRequest(midlet.getKWUrl() + "/ota/mlgk.jar");
                } catch (Throwable t) {
                    //#style alertinfo
                    form.append("Could not get new version...sorry.");
                    Log.log(t.getMessage());
                }
            }
		}
	}
}
