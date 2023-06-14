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

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.example.uberapp.databinding.ActivityDriverBinding;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.EventListener;
import java.util.List;
import java.util.Map;

public class DriverActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener {

    private GoogleMap mMap;
    private ActivityDriverBinding binding;
    private GoogleApiClient googleApiClient;
    private Location currentLocation;
    private LocationRequest locationRequest;
    private LocationManager locationManagesr;
    private LocationListener locationListener;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private String driverID , clientID;
    private Button btnLogOut;
    private final DatabaseReference availableDriversRef = FirebaseDatabase.getInstance().getReference("AvailableDrivers");
    private final DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("WorkingDrivers");
    private DatabaseReference driverRef ;
    private DatabaseReference assignedClientRef ;
    private final GeoFire geoFireAvailable = new GeoFire(availableDriversRef);
    private final GeoFire geoFireWorking = new GeoFire(refWorking);
    Marker pickUpMarker;
    private ValueEventListener assignedClientEvent;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityDriverBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);

        locationRequest = new LocationRequest();

        btnLogOut = findViewById(R.id.btnLogOut);

        btnLogOut.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FirebaseAuth.getInstance().signOut();
                startActivity(new Intent(DriverActivity.this , LoginActivity.class));
            }
        });

        driverID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(driverID);
        getAssignedClient();

    }

    private void getAssignedClient() {
        driverRef.child("ClientID").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists())
                {
                    clientID = snapshot.getValue().toString();
                    if(!clientID.isEmpty())
                        getPickUpLocation();
                    else {
                        if(pickUpMarker != null)
                            pickUpMarker.remove();
                        if(assignedClientRef != null)
                            assignedClientRef.removeEventListener(assignedClientEvent);

                        geoFireWorking.removeLocation(driverID);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void getPickUpLocation() {
        assignedClientRef = FirebaseDatabase.getInstance().getReference().child("ClientRequests").child(clientID).child("l");
        assignedClientEvent = assignedClientRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(snapshot.exists())
                {
                    List<Object> locationInfos = (List<Object>) snapshot.getValue();
                    double lat = 0;
                    double lng = 0;
                    if(locationInfos.get(0) != null && locationInfos.get(1) != null)
                    {
                        lat = Double.parseDouble(locationInfos.get(0).toString());
                        lng = Double.parseDouble(locationInfos.get(1).toString());
                    }

                    LatLng driverLatLng = new LatLng(lat , lng);

                    pickUpMarker = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("PickUpLocation"));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        // Add a marker in Sydney and move the camera
        LatLng sydney = new LatLng(-34, 151);
        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));

        buildGoogleApiClient();

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mMap.setMyLocationEnabled(true);
    }

    private void buildGoogleApiClient() {
        googleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        googleApiClient.connect();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        locationRequest = new LocationRequest();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

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
    public void onLocationChanged(@NonNull Location location) {

       if(getApplicationContext() != null){
          getAssignedClient();

           currentLocation = location;
           LatLng latLng = new LatLng(location.getLatitude() , location.getLongitude());
           mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
           mMap.animateCamera(CameraUpdateFactory.zoomTo(12));

           if(clientID == null || clientID.isEmpty())
           {
               geoFireAvailable.setLocation(driverID, new GeoLocation(location.getLatitude() , location.getLongitude()));
               geoFireWorking.removeLocation(driverID);
           }
           else {
               geoFireWorking.setLocation(driverID, new GeoLocation(location.getLatitude() , location.getLongitude()));
               geoFireAvailable.removeLocation(driverID);
           }
       }
    }

    @Override
    protected void onStop() {
        //When the driver exists the app.
        super.onStop();
        LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
        GeoFire geoFire = new GeoFire(availableDriversRef);
        geoFire.removeLocation(driverID);
    }
}