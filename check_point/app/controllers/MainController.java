package controllers;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.GenderIndex;
import models.GenderInfo;
import play.Logger;
import play.libs.F.Function0;
import play.libs.F.Promise;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import org.json.*;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

public class MainController extends Controller {

    private static class Coords {
        private double _latitude;
        private double _longitude;

        public Coords(double latitude, double longitude) {
            _latitude = latitude;
            _longitude = longitude;
        }

        public Coords(String latitude, String longitude) {
            _latitude = Double.parseDouble(latitude);
            _longitude = Double.parseDouble(longitude);
        }

        public Coords(JSONObject port) {
            JSONObject geo = port.getJSONObject("Geo");
            _latitude = geo.getDouble("Lat");
            _longitude = geo.getDouble("Long");
        }

        public double distanceFrom(Coords coords) {
            return distance(_latitude, _longitude, coords._latitude, coords._longitude);
        }

        private double distance(double lat1, double lon1, double lat2, double lon2) {
            double theta = lon1 - lon2;
            double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
            dist = Math.acos(dist);
            dist = rad2deg(dist);
            dist = dist * 60 * 1.1515;
            return (dist);
        }

        private double deg2rad(double deg) {
            return (deg * Math.PI / 180.0);
        }

        private double rad2deg(double rad) {
            return (rad * 180 / Math.PI);
        }

        public double getLatitude() {return _latitude; }
        public double getLongitude() {return _longitude; }
    }

    private final static String DefaultStationsResult = "{}";
    
    public static Result index() {
        return ok(views.html.index.render("Hello from Java"));
    }

    private static String getParam(String name) {
        String result = request().getQueryString(name);
        if (result == null) {
            result = "";
        }
        return result;
    }

    public static Result findStation() {
//        Logger.debug("MainController.find");

        String latitude = getParam("latitude");
        String longitude = getParam("longitude");
        String radiusInMiles = getParam("radiusInMiles");

        String locations = DefaultStationsResult;
        try {
            locations = getLocation(latitude, longitude, radiusInMiles);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ok(locations);
    }

    public static String getLocation(String latitude, String longitude, String radiusInMiles) throws MalformedURLException, IOException {
        String locationsXML = doGetLocation(latitude, longitude, radiusInMiles);
        Coords coords = new Coords(latitude, longitude);
        return extractLocation(coords, locationsXML);
    }

    public static String extractLocation(Coords coords, String xml) {
        String rawJson = DefaultStationsResult;
//        Logger.debug("extractLocations:" + xml);
        try  {
            org.json.JSONObject json = XML.toJSONObject(xml);

            JSONObject envelope = json.getJSONObject("soapenv:Envelope");
            if (envelope == null) return rawJson;

            JSONObject body = envelope.getJSONObject("soapenv:Body");
            if (body == null) return rawJson;

            JSONObject response = body.getJSONObject("ns1:getPublicStationsResponse");
            if (response == null) return rawJson;

            JSONArray stations = response.getJSONArray("stationData");
            if (stations == null) return rawJson;

            rawJson = doExtractLocation(coords, stations);

        } catch (JSONException e) {
            Logger.debug("extractLocations.exception:" + e.toString());
        }

        return rawJson;
    }

    public static String doExtractLocation(Coords coords, JSONArray stations) {
        JSONObject json = new JSONObject();
        if (stations.length() > 0) {
            List<JSONObject> stationsArray = createSortedStationsArray(coords, stations);

            JSONObject firstStation = stationsArray.get(0);
            JSONObject port = getPort(firstStation);
            Coords curCoord = new Coords(port);
            json.put("latitude", curCoord.getLatitude());
            json.put("longitude", curCoord.getLongitude());
            json.put("stationName", port.getString("stationName"));
        }
        return json.toString();
    }

    public static List<JSONObject> createSortedStationsArray(Coords coords, JSONArray stations) {

        List<JSONObject> stationsArray = new ArrayList<JSONObject>();
        for (int i = 0; i < stations.length(); ++i) {
            JSONObject station = stations.getJSONObject(i);
            Coords curLocation = new Coords(getPort(station));

            double distance = curLocation.distanceFrom(coords);

            station.put("Distance", distance);
            stationsArray.add(station);
        }

        Collections.sort(stationsArray, new Comparator<JSONObject>() {
            public int compare(JSONObject e1, JSONObject e2) {
                double diff = e1.getDouble("Distance") - e2.getDouble("Distance");
                if (diff > 0) return 1;
                if (diff < 0) return -1;
                return 0;
            }
        });
        return stationsArray;
    }

    public static JSONObject getPort(JSONObject station) {
        int numPorts = station.getInt("numPorts");
        if (numPorts > 1) {
            JSONArray ports = station.getJSONArray("Port");
            return  ports.getJSONObject(0);
        }
        return station.getJSONObject("Port");
    }

    public static String doGetLocation(String latitude, String longitude, String radiusInMiles) throws MalformedURLException, IOException {

        //Code to make a webservice HTTP request
        String user = "b207195d0b1684270db5aeae7970408c5179ce9f5a4dc1366937247";
        String pass = "167fb3e18980d8622f6a19fbbda3e01d";

        //String wsURL = "http://www.deeptraining.com/webservices/weather.asmx";
        String wsURL = "https://webservices.chargepoint.com/webservices/chargepoint/services/4.1";
        URL url = new URL(wsURL);
        URLConnection connection = url.openConnection();
        HttpURLConnection httpConn = (HttpURLConnection)connection;
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        String xmlInput =
                "  <soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:web=\"http://litwinconsulting.com/webservices/\">\n" +
                        "   <soap:Header>\n" +
                        "       <wsse:Security xmlns:wsse='http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd' soap:mustUnderstand='1'>\n" +
                        "           <wsse:UsernameToken xmlns:wsu='http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd' wsu:Id='UsernameToken-261'>\n" +
                        "               <wsse:Username>" + user + "</wsse:Username>\n" +
                        "               <wsse:Password Type='http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordText'>" + pass + "</wsse:Password>\n" +
                        "           </wsse:UsernameToken>\n" +
                        "       </wsse:Security>\n" +
                        "   </soap:Header>\n" +
                        "   <soap:Body>\n" +
                        "       <ns2:getPublicStations xmlns:ns2='http://www.example.org/coulombservices/'>\n" +
                        "           <searchQuery>\n" +
                        "               <Proximity>" + radiusInMiles + "</Proximity>\n" +
                        "               <proximityUnit>M</proximityUnit>\n" +
                        "               <Geo>\n" +
                        "                   <Lat>" + latitude + "</Lat>\n" +
                        "                   <Long>" + longitude + "</Long>\n" +
                        "               </Geo>\n" +
                        "           </searchQuery>\n" +
                        "       </ns2:getPublicStations>\n" +
                        "   </soap:Body>\n" +
                        "  </soap:Envelope>\n";
        byte[] buffer = new byte[xmlInput.length()];
        buffer = xmlInput.getBytes();
        bout.write(buffer);
        byte[] b = bout.toByteArray();

        //String SOAPAction = "http://litwinconsulting.com/webservices/GetWeather";
        String SOAPAction = "urn:provider/interface/chargepointservices/getPublicStations";

        // Set the appropriate HTTP parameters.
        httpConn.setRequestProperty("Content-Length",
                String.valueOf(b.length));
        httpConn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
        httpConn.setRequestProperty("SOAPAction", SOAPAction);
        httpConn.setRequestMethod("POST");
        httpConn.setDoOutput(true);
        httpConn.setDoInput(true);
        OutputStream out = httpConn.getOutputStream();
        //Write the content of the request to the outputstream of the HTTP Connection.
        out.write(b);
        out.close();
        //Ready with sending the request.

        //Read the response.
        InputStreamReader isr =
                new InputStreamReader(httpConn.getInputStream());
        BufferedReader in = new BufferedReader(isr);

        //Write the SOAP message response to a String.
        String responseString = "";
        String outputString = "";

        while ((responseString = in.readLine()) != null) {
            outputString += responseString;
        }
        return outputString;

    }
}
