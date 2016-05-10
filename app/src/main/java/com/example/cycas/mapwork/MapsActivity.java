package com.example.cycas.mapwork;

import android.content.IntentSender;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.pubnub.api.Callback;
import com.pubnub.api.Pubnub;
import com.pubnub.api.PubnubError;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Locale;


public class MapsActivity extends FragmentActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    private EditText addressInput;
    private Button searchBtn;
    private Button currentLocationBtn;

    private Pubnub mPubnub;

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    public static final String TAG = MapsActivity.class.getSimpleName();

    //private double latitude, longtitude = 0;
    //private static LatLng workAddLatLng = new LatLng (21,57);

    private String workAddress = null; //"10225 willow creek rd. San Diego CA 92131";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_maps);
        setUpMapIfNeeded();

        addressInput = (EditText) findViewById(R.id.addressInput);
        searchBtn = (Button) findViewById(R.id.searchBtn);
        currentLocationBtn = (Button) findViewById(R.id.currentLocationBtn);

        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    workAddress = addressInput.getText().toString();
                    Log.v(TAG, addressInput.getText().toString());
                    searchAddress();
            }
        });

        mPubnub = new Pubnub("pub-c-4d792df2-8a83-439e-8ec5-bf4f2932d72d", "sub-c-c5d98186-8136-11e5-9720-0619f8945a4f");

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000) // 10 sec
                .setFastestInterval(1 * 1000) ; // 1 sec
    }


    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
        mGoogleApiClient.connect();

        if (mGoogleApiClient.isConnected()) {
            startLocationUpdates();
        }
    }


    @Override
    protected void onPause() {
        super.onPause();

        if(mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        stopLocationUpdates();
    }


    public void currentLocationRequestHandler(View view) {
        startLocationUpdates();
    }

    protected void startLocationUpdates() {
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    protected void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
    }

    private void setUpMapIfNeeded() {
        if (mMap == null) {
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();

            if (mMap != null) {
                setUpMap();
            }
        }
    }

    private void setUpMap() {
        mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
    }


    private void handleNewLocation(Location location) {
        Log.d(TAG, location.toString());

        double currentLatitude = location.getLatitude();
        double currentLongitude = location.getLongitude();

        LatLng latlng = new LatLng(currentLatitude, currentLongitude);

        MarkerOptions options = new MarkerOptions()
                .position(latlng)
                .title("I am here!");
        mMap.addMarker(options);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latlng, 15));

    }


    private void searchAddress() {

        LatLng workAddLatLng = new LatLng(0, 0);

        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses;
            addresses = geocoder.getFromLocationName(workAddress, 1);
            if (addresses.size() > 0) {
                double latitude = addresses.get(0).getLatitude();
                double longtitude = addresses.get(0).getLongitude();
                workAddLatLng = new LatLng(latitude, longtitude);
            }

//            if (mMap == null) {
//                mMap = ((SupportMapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
//            }
            mMap.setMapType(mMap.MAP_TYPE_HYBRID);
            Marker TP = mMap.addMarker(new MarkerOptions()
                    .position(workAddLatLng)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                    .title("Work address"));

            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(workAddLatLng, 15));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }


    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Location services connectied.");

        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if (location == null) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
        else {
            handleNewLocation(location);

        }


    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Location services suspended. Please reconnect.");
    }


    @Override
    public void onLocationChanged(Location location) {
        broadcastLocation(location);
    }

    Callback publishCallback = new Callback() {
        @Override
          public void successCallback(String channel, Object response) {
            Log.d("PUBNUB", response.toString());
          }

          @Override
          public void errorCallback(String channel, PubnubError error) {
            Log.e("PUBNUB", error.toString());
          }
    };


    private void broadcastLocation(Location location) {
        JSONObject message = new JSONObject();
        try {
            message.put("lat", location.getLatitude());
            message.put("lng", location.getLongitude());
            message.put("alt", location.getAltitude());
        } catch (JSONException e) {
            Log.e(TAG, e.toString());
        }
        mPubnub.publish("A Channel Name", message, publishCallback);
    }


    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {

        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                 * Thrown if Google Play services canceled the original
                 * PendingIntent
                 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

}
