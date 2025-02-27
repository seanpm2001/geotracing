using System;
using System.Collections.Generic;
using System.Text;
using System.Threading;
using System.IO;
using System.Globalization;
using System.Resources;
using Diwi;

namespace Diwi {
    /// <summary>
    /// Implements the GPS connection.
    /// singleton class; uses file with raw nmea if a GPS is not found.
    /// communicates information through callback.
    /// </summary>
    public class GpsReader
    {
        public enum sMess  {M_POS, M_SPEED, M_FIX, M_PREC, M_BEARING, M_NUMSAT, M_DEMO, M_MESSAGE };
        public static float KPHPerKnot = 1.852F;

        public delegate void CallbackHandler(int message);
        public event CallbackHandler callback;


        public static CultureInfo mUSFormat = new CultureInfo(0x0409); // constant voor en-US domain. gebruikt in nummerformat parsing 
        private System.IO.Ports.SerialPort mSerialPort;
        private Thread mReadDataThread = null;
        private Thread mOpenPortThread = null;
        
        private bool mIsRunning = false;
        private string mPort;
        private string mNMEA;
        private bool mIsLogging = true;

        static private GpsReader sGPS = null;


        private bool  mDemo = false;
        private bool  mHasFix = false;
        private float mBearing, mSpeed, mLat, mLon, mPrecision;
        private float storedLat, storedLon;
        private int   mNumSats, mSoundCount;

        #region properties

        public static float deg2Rad(float degrees) {
            return (degrees * (float)Math.PI) / 180.0f;
        }

        public static bool up {
            get {
                return GpsReader.fix || AppController.sTapMode;
            }
        }

        public static bool present {
            get {
                return (!GpsReader.demo  && sGPS.mSerialPort.IsOpen );
            }
        }

        
        static public string nmea {
            get { return sGPS.mNMEA; }
        }


        static public bool demo {
            get { return sGPS.mDemo; }
        }
        static public bool fix {
            get { return sGPS.mHasFix; }
        }
        static public int numSats {
            get { return sGPS.mNumSats; }
        }
        static public float bearing {
            get { return sGPS.mBearing; }
        }
        static public float speed {
            get { return sGPS.mSpeed; }
        }
        static public float precision {
            get { return sGPS.mPrecision; }
        }
        static public float lat {
            get { return sGPS.mLat; }
        }
        static public float lon {
            get { return sGPS.mLon; }
        }
        static public string latitude {
            get {
                string s;
                float deg = sGPS.mLat;
                if (deg < 0) {
                    deg = -deg;
                    s = " S";
                } else {
                    s = " N";
                }
                float min = (float)60.0 * (deg - (float)((int)deg));
                float sec = (float)60.0 * (min - (float)((int)min));
                s = ((int)deg).ToString() + "°" + ((int)min).ToString() + "'" + ((int)sec).ToString() + "''" + s;
                return s;
            }
        }
        static public string longtitude {
            get {
                string s;
                float deg = sGPS.mLon;
                if (deg < 0) {
                    deg = -deg;
                    s = " W";
                } else {
                    s = " E";
                }
                float min = (float)60.0 * (deg - (float)((int)deg));
                float sec = (float)60.0 * (min - (float)((int)min));
                s = ((int)deg).ToString() + "°" + ((int)min).ToString() + "'" + ((int)sec).ToString() + "''" + s;
                return s;
            }
        }


        static public float km2degLat(float km) {
            return km / (float)(40000.0 / 360.0);
        }

        static public float km2degLon(float km) {
            return km / (float)( Math.Cos( deg2Rad(GpsReader.lat) ) * (40000.0 / 360.0));
        }



#endregion


        public void storeLocation() {
            storedLat = mLat;
            storedLon = mLon;
        }

        public string storedLattitude {
            get {
                return storedLat.ToString(mUSFormat);
            }
        }

        public string storedLongtitude {
            get {
                return storedLon.ToString(mUSFormat);
            }
        }

        public static GpsReader instance {
            get {
                if (sGPS != null) return sGPS;
                return sGPS = new GpsReader();
            }
        }


        void findGpsPort() {
            mSerialPort = new System.IO.Ports.SerialPort(AppController.sComPort);
            try {
                mSerialPort.Open();
                mPort = AppController.sComPort; 
            } catch (IOException) {
                mPort = "COM0";
            }

            try {
                mSerialPort.Close();
            } catch (IOException) {
                ;
            }

            mSerialPort = null;
        }


        private GpsReader() {
            if (AppController.sTapMode == false) {
                findGpsPort();
                start();
            } else {
                mDemo = true;
            }
        }


        /// <summary>
        /// open a serial port and start reading, asynchronously.
        /// </summary>
        public void start() {

            if (mOpenPortThread == null) {
                if (mSerialPort == null) {
                    mSerialPort = new System.IO.Ports.SerialPort(mPort);
                }
 
                try {
                    mSerialPort.Close();
                } catch (IOException) {
                    ;
                }

                try {
                    mSerialPort.Open();
                } catch (IOException) {
                    ;
                }

                mOpenPortThread = new Thread(openPortThread);
            }

            if (mReadDataThread == null) {
                mReadDataThread = new Thread(readThread);
            }


            mIsRunning = true;
            mReadDataThread.Start();
            mOpenPortThread.Start();
        }

        /// <summary>
        /// stop reading, close ports.
        /// </summary>
        public void stop()
        {
            mIsRunning = false;

            if (AppController.sTapMode) return;

            Thread.Sleep(500);
            if (mSerialPort.IsOpen) {
                mSerialPort.Close();
                mSerialPort.Dispose();
                mSerialPort = null;
            }

            if (mReadDataThread != null) {
                mReadDataThread.Abort();
                mReadDataThread = null;
            }

            if (mOpenPortThread != null) {
                mOpenPortThread.Abort();
                mOpenPortThread = null;
            }
        }


        void setFixStatus(bool f) {
            if (mHasFix != f) {
                mHasFix = f;
                if (callback != null) {
                    callback((int)sMess.M_FIX);
                }
            }
        }


        private void openPortThread() {
            while (mIsRunning == true) {
                if ( !mSerialPort.IsOpen ) {
                    try {
                        mSerialPort.Open();
                        if (callback != null) {
                            callback((int)sMess.M_DEMO);
                        }
                    } catch (IOException) {
                        ;
                    }
                }
                Thread.Sleep(5000);
            }
        }


        /// <summary>
        /// thread sun loop reading GPS, or trying.
        /// </summary>
        private void readThread() {

            // keep trying to open serial port; 
            while (mIsRunning == true) {
                string data = "";

                try {
                    if(mSerialPort.IsOpen )
                    data = mSerialPort.ReadLine();
                } catch (IOException) {
                    // port broken, or somesuch
                    if (mSerialPort.IsOpen) {
                        mSerialPort.Close();
                    }
                    if (callback != null) {
                        callback((int)sMess.M_DEMO);
                    }

                } catch (TimeoutException) {
                    // no data, sleep for a while and try again
                    Thread.Sleep(1000);
                    continue;
                }

                if (data.Length > 0) {
                    // got data from GPS
                    parse(data, true);
                }
            }

        }

        #region parsing

        /// <summary>
        /// parse NMEA string received.
        /// currently only RMC and GGA strings are processed
        /// </summary>
        public bool parse(string sentence, bool real) {

            if (sentence != null) {
                string[] words;
                if (mIsLogging) {
                    AppController.sTrackLog.WriteLine(sentence);
                }

                mNMEA = sentence;

                words = sentence.Split(',');

                switch (words[0]) {
                    case "$GPRMC":
                        return ParseGPRMC(words, real);
                    case "$GPGGA":
                        return ParseGPGGA(words, real);
                    default:
                        return false;
                }
            }

            return false;
        }


        public bool ParseGPGGA(string[] words,bool real) {

            if (words[7] != "") {
                mNumSats = int.Parse(words[7]);
                if (callback != null)
                    callback((int)sMess.M_NUMSAT);
            }

            if (words[8] != "") {
                mPrecision = float.Parse(words[8], mUSFormat);
                if (callback != null)
                    callback((int)sMess.M_PREC);
            }

            return true;
        }


        public void insertLocation(float lat, float lon) {
            setFixStatus(true);
            mLat = lat;
            mLon = lon;
            AppController.sTrackLog.WriteLine("lat=" + mLat.ToString(mUSFormat) + ", lon=" + mLon.ToString(mUSFormat));
            // Notify the calling application of the change
            if (true == AppController.sKwxClient.queueSample()) {
                if (callback != null)
                    callback((int)sMess.M_POS);
            }

        }

        public bool ParseGPRMC(string[] words,bool real) {

            if (words[2] == "A")
                setFixStatus(true);
            else {
                setFixStatus(false);
                return false; // geen fix; geen interessante data!
            }

 /*           if (real) {
                mSoundCount--;
                if (mSoundCount <= 0) {
                    mSoundCount = 10;
                    AppController.SysBeep();
                }
            }
  * */

            if (words[3] != "" && words[4] != "" && words[5] != "" && words[6] != "") {

                float lat = float.Parse(words[3].Substring(0, 2), mUSFormat);
                float min = float.Parse(words[3].Substring(2), mUSFormat);

                lat += min / (float)60.0;
                if (words[4] != "N") lat = -lat;


                float lon = float.Parse(words[5].Substring(0, 3), mUSFormat);
                min = float.Parse(words[5].Substring(3), mUSFormat);

                lon += min / (float)60.0;
                if (words[6] != "E") lon = -lon;

                mLat = lat;
                mLon = lon;

                // Notify the calling application of the change
                if (true == AppController.sKwxClient.queueSample()) {
                    if (callback != null)
                        callback((int)sMess.M_POS);
                }
            }

            if (words[7] != "") {

                mSpeed = float.Parse(words[7], mUSFormat) * KPHPerKnot;
                if (callback != null)
                    callback((int)sMess.M_SPEED);
            }

            if (words[8] != "") {
                mBearing = float.Parse(words[8], mUSFormat);
                if (callback != null)
                    callback((int)sMess.M_BEARING);
            }
            return true;
        }
        #endregion
    }
}
