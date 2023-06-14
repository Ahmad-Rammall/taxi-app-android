package com.example.uberapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.uberapp.databinding.ActivityClientBinding;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.List;

public class ClientActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {
    private GoogleMap mMap;
    private ActivityClientBinding binding;
    private GoogleApiClient googleApiClient;
    private Location currentLocation;
    private LatLng pickUpLocation;
    private LocationRequest locationRequest;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Button btnLogOut , btnRequest;
    private String userID , driverFoundID;
    private int radius = 1;
    private boolean driverFound = false , driverNotFound = false;
    private Marker driverMarker , clientRequestMarker;
    private boolean isDriveRequested = false;
    private GeoQuery geoQuery;

    private final DatabaseReference clientReqRef = FirebaseDatabase.getInstance().getReference("ClientRequests");
    private final DatabaseReference availableDriversRef = FirebaseDatabase.getInstance().getReference("AvailableDrivers");
    private final DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference("Users").child("Drivers");
    private DatabaseReference foundDriverRef;
    private final GeoFire geoFireClientRequest= new GeoFire(clientReqRef);



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityClientBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("path/to/geofire");
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest();

        userID = FirebaseAuth.getInstance().getCurrentUser().getUid();

        btnLogOut = findViewById(R.id.btnLogOut);
        btnRequest = findViewById(R.id.requestBtn);

        btnLogOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(ClientActivity.this , LoginActivity.class));
            }
        });

        btnRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isDriveRequested){
                    //if drive requested , we can cancel it
                    isDriveRequested = false;
                    geoQuery.removeAllListeners();
                    if(foundDriverRef != null)
                        foundDriverRef.removeEventListener(driverEventListener);
                    geoFireClientRequest.removeLocation(userID);

                    if(driverFoundID != null)
                    {
                        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users")
                                .child("Drivers").child(driverFoundID).child("ClientID");
                        driverRef.setValue("");
                        driverFoundID = null;
                    }

                    driverFound = false;
                    radius = 1;

                    if(clientRequestMarker != null)
                        clientRequestMarker.remove();
                    btnRequest.setText("Request a ride");
                }
                else {
                    isDriveRequested = true;
                    pickUpLocation = new LatLng(currentLocation.getLatitude() , currentLocation.getLongitude());
                    clientRequestMarker = mMap.addMarker(new MarkerOptions().position(pickUpLocation).title("PickUp"));
                    btnRequest.setText("Getting Your Driver...");
                    getClosestDriver();
                }
            }
        });

    }
    private void getClosestDriver() {
        GeoFire geoFire = new GeoFire(availableDriversRef);
        geoQuery = geoFire.queryAtLocation(new GeoLocation(pickUpLocation.latitude , pickUpLocation.longitude) , radius);
        geoQuery.removeAllListeners(); // to be sure that there is no conflicts when recalling the method

        if(driverNotFound)
        {
            isDriveRequested = false;
            driverNotFound = false;
            Toast.makeText(this, "Driver Not Found", Toast.LENGTH_SHORT).show();
            btnRequest.setText("Request a ride");
            driverNotFound = false;
            radius = 1;
            clientRequestMarker.remove();
            geoFire.removeLocation(userID);
            return;
        }

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                //When a driver is found
                if (!driverFound && isDriveRequested) {
                    driverFound = true;
                    driverFoundID = key;
                    geoFireClientRequest.setLocation(userID , new GeoLocation(currentLocation.getLatitude() , currentLocation.getLongitude()));
                    btnRequest.setText("Looking For A Driver ...");

                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users")
                            .child("Drivers").child(driverFoundID).child("ClientID");
                    driverRef.setValue(userID);
                    getDriverLocation();
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                //When there is no driver inside the radius
                if (!driverFound) {
                    if (radius < 30)
                        radius = radius + 1;
                    else
                        driverNotFound = true;
                    getClosestDriver();

                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }
    private ValueEventListener driverEventListener;
    private void getDriverLocation() {
        foundDriverRef = FirebaseDatabase.getInstance().getReference().child("WorkingDrivers")
                .child(driverFoundID).child("l");

        driverEventListener = foundDriverRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists()){
                    List<Object> locationInfos = (List<Object>) snapshot.getValue();
                    double lat = 0;
                    double lng = 0;
                    if(locationInfos.get(0) != null && locationInfos.get(1) != null)
                    {
                        lat = Double.parseDouble(locationInfos.get(0).toString());
                        lng = Double.parseDouble(locationInfos.get(1).toString());
                    }

                    LatLng driverLatLng = new LatLng(lat , lng);

                    if(driverMarker != null)
                        driverMarker.remove();

                    Location clientLoc = new Location("");
                    clientLoc.setLatitude(pickUpLocation.latitude);
                    clientLoc.setLongitude(pickUpLocation.longitude);

                    Location driverLoc = new Location("");
                    driverLoc.setLatitude(lat);
                    driverLoc.setLongitude(lng);

                    float distance = clientLoc.distanceTo(driverLoc);

                    if(distance < 1)
                    {
                        btnRequest.setText("Request a ride");
                        Toast.makeText(ClientActivity.this, "Driver Arrived", Toast.LENGTH_SHORT).show();

                        driverRef.child(driverFoundID).child("ClientID").setValue("");
                        if(clientRequestMarker != null)
                            clientRequestMarker.remove();
                        if(driverMarker != null)
                            driverMarker.remove();
                    }
                    else
                        btnRequest.setText("Driver Found : "+distance);
                    driverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("YourDriver"));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }


    @Override
    protected void onPause() {
        super.onPause();
        if (locationManager != null && locationListener != null) {
            locationManager.removeUpdates(locationListener);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;


        // Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);

    }

    protected synchronized void buildGoogleApiClient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        googleApiClient.connect();
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        currentLocation = location;
        LatLng latLng = new LatLng(location.getLatitude() , location.getLongitude());
        mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
        mMap.animateCamera(CameraUpdateFactory.zoomTo(13));
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(googleApiClient, locationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    protected void onStop() {
        super.onStop();
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
        geoFireClientRequest.removeLocation(userID);
        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users")
                .child("Drivers").child(driverFoundID).child("ClientID");
        driverRef.setValue("");
        if(clientRequestMarker != null)
            clientRequestMarker.remove();
        if(driverMarker != null)
            driverMarker.remove();
    }
}