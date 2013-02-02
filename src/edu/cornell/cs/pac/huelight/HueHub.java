package edu.cornell.cs.pac.huelight;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

public class HueHub {
	private final String hostAddress;
	
	// hash key to be used in API.
	private String userNameHash;
	
	private static final String LOG_TAG = "HueHub";

	public HueHub(String host, String hash) {
		this.hostAddress = host;
		if (hash != null)
			this.userNameHash = hash;
	}
	
	/**
	 * Generates MD5 hash of given string
	 */
	private static String getMd5(String s) throws NoSuchAlgorithmException {
		MessageDigest digester = MessageDigest.getInstance("MD5");
		return digester.digest(s.getBytes()).toString();
	}
	
	/**
	 * Returns path to access lamp.
	 * 
	 * There are two types of request to access lamp --- querying a lamp
	 * using GET request to 'http://YourHueHub/api/key/lights/1' and controlling
	 * one using PUT request to 'http://YourHueHub/api/key/lights/1/state'.
	 * 
	 * @param light Light number
	 * @param isState If 'state' should be appended to the path
	 */
	private String getLightPath(int light, boolean isState) {
		StringBuilder path = new StringBuilder(this.userNameHash);
		path.append("/lights/");
		path.append(light);
		if (isState)
			path.append("/state");
		
		return path.toString();
	}
	
	/**
	 * Registers the application.
	 * 
	 * For registering, a POST call is made to http://YourHueHub/api/.
	 * The resulting hash key is saved in userNameHash variable and used
	 * by all next calls.
	 * 
	 * Unless you passed a non-null hash in the constructor, this method
	 * must be called before accessing lamps.
	 * 
	 * @throws Exception If registration fails. In such case, you might need to
	 * press the link button of the Hub.
	 * 
	 */
	public void doRegister() throws Exception {
		String hash = HueHub.getMd5(UUID.randomUUID().toString());
		
		JSONObject obj = new JSONObject();
		obj.put("username", hash);
		obj.put("devicetype", "HueHubAndroid");
		
		JSONObject response = HueHub.doCommunicate(this.hostAddress, "POST", null, obj);
		
		if ((response == null) || (response.has("error")))
			throw new Exception("Unable to register");
		
		this.userNameHash = response.getJSONObject("success").getString("username");
	}
	
	/**
	 * Returns current state of the light.
	 * @param light lamp number.
	 * @return {@link JSONObject} representing state.
	 */
	public JSONObject getState(int light) {
		String lightPath = getLightPath(light, false);
		return HueHub.doCommunicate(this.hostAddress, "GET", lightPath, null);
	}
	
	/**
	 * Sets state of light.
	 * @param light Lamp number.
	 * @param state State to be set.
	 * @return {@link JSONObject} as returned by the hub.
	 */
	public JSONObject setState(int light, JSONObject state) {
		String path = getLightPath(light, true);
		return HueHub.doCommunicate(this.hostAddress, "PUT", path, state);
		
	}
	
	/**
	 * Utility function for writing data to {@link OutputStream}.
	 */
	private static void writeStream(OutputStream out, String data)
			throws IOException {
		try {
		OutputStreamWriter o = new OutputStreamWriter(out);
		o.write(data);
		}
		finally {
			out.close();
		}
	}
	
	/**
	 * Reads data from Input Stream
	 */
	private static String readStream(InputStream inputStream)
			throws IOException {
		StringBuffer sb = new StringBuffer();
		String s;
		try {
		BufferedReader in = new BufferedReader(
				new InputStreamReader(inputStream));
		
		while ((s=in.readLine()) != null)
			sb.append(s);
		}
		finally {
			inputStream.close();
		}
		
		return sb.toString();
	}

	/**
	 * Provides functionality to communicate to the light hub.
	 * 
	 * @param host Host address.
	 * @param requestMethod Http request method.
	 * @param path Path to be used in the URL.
	 * @param data Data to pass to the hub.
	 * @return {@link JSONObject} as returned by the hub or null in case
	 * there was any error.
	 */
	private static JSONObject doCommunicate(String host, String requestMethod,
			String path, JSONObject data) {
		
		HttpURLConnection httpConnection = null;
		JSONObject responseJSON = null;
		
		try {
			StringBuffer urlAddress = new StringBuffer(host);
			urlAddress.append("/api/");

			if (path != null) 
				urlAddress.append(path);

			URL url = new URL(urlAddress.toString());
			httpConnection = (HttpURLConnection) url.openConnection();
			httpConnection.setRequestMethod(requestMethod);

			if (data != null) {
				httpConnection.setDoOutput(true);
				HueHub.writeStream(httpConnection.getOutputStream(), data.toString());
			}
			
			String response = HueHub.readStream(httpConnection.getInputStream());
			responseJSON = new JSONObject(response);
			
			
		} catch (IOException e) {
			android.util.Log.e(LOG_TAG, "Could not communicate to: " + host, e);
			
		} catch (JSONException e) {
			android.util.Log.e(LOG_TAG, "Unable to convert to JSON object", e);
		}
		finally {
			if (httpConnection != null)
				httpConnection.disconnect();
		}
		
		return responseJSON;
	}
}