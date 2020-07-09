/**
 * Copyright 2020 Huawei Technologies co, Ltd All
 * Rights reserved
 * Licenced under the Apache License,Version 2.0(the "License");
 * you may not use this file except in compliance with license
 * you may obtain a copy of the license at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by application law or agreed to in writing software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permission and
 * limitations under the License
 */
package com.hms.locationkit.fusedlocation

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.content.IntentSender.SendIntentException
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.core.app.ActivityCompat.checkSelfPermission
import androidx.core.app.ActivityCompat.requestPermissions
import com.hms.locationkit.R
import com.hms.locationkit.activity.BaseActivity
import com.hms.locationkit.logger.LocationLog
import com.huawei.hmf.tasks.Task
import com.huawei.hms.common.ApiException
import com.huawei.hms.common.ResolvableApiException
import com.huawei.hms.location.*
import kotlinx.android.synthetic.main.activity_request_location_updates_callback.*

class RequestLocationUpdatesWithCallbackActivity : BaseActivity(), View.OnClickListener {
    companion object {
        private const val TAG = "LocationUpdatesCallback"
    }

    // the callback of the request
    private var mLocationCallback: LocationCallback? = null

    private var mLocationRequest: LocationRequest? = null

    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient

    private lateinit var settingsClient: SettingsClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_location_updates_callback)
        location_requestLocationUpdatesWithCallback.setOnClickListener(this)
        location_removeLocationUpdatesWithCallback.setOnClickListener(this)
        addLogFragment()
        // create fusedLocationProviderClient
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        // create settingsClient
        settingsClient = LocationServices.getSettingsClient(this)
        mLocationRequest = LocationRequest().apply {
            interval = 1000
            needAddress = true
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        // Set the interval for location updates, in milliseconds.
        // set the priority of the request
        if (null == mLocationCallback) {
            mLocationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult?) {
                    if (locationResult != null) {
                        val locations: List<Location> =
                            locationResult.locations
                        if (locations.isNotEmpty()) {
                            for (location in locations) {
                                LocationLog.i(
                                    TAG,
                                    "onLocationResult location[Longitude,Latitude,Accuracy]:${location.longitude} , ${location.latitude} , ${location.accuracy}"
                                )
                            }
                        }
                    }
                }

                override fun onLocationAvailability(locationAvailability: LocationAvailability?) {
                    locationAvailability?.let {
                        val flag: Boolean = locationAvailability.isLocationAvailable
                        LocationLog.i(TAG, "onLocationAvailability isLocationAvailable:$flag")
                    }
                }
            }
        }

        // check location permission
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            Log.i(TAG, "sdk < 28 Q")
            if (checkSelfPermission(
                    this,
                    ACCESS_FINE_LOCATION
                ) != PERMISSION_GRANTED
                && checkSelfPermission(
                    this,
                    ACCESS_COARSE_LOCATION
                ) != PERMISSION_GRANTED
            ) {
                val strings = arrayOf(
                    ACCESS_FINE_LOCATION,
                    ACCESS_COARSE_LOCATION
                )
                requestPermissions(this, strings, 1)
            }
        } else {
            if (checkSelfPermission(this@RequestLocationUpdatesWithCallbackActivity, ACCESS_FINE_LOCATION) != PERMISSION_GRANTED && checkSelfPermission(
                    this@RequestLocationUpdatesWithCallbackActivity,
                    ACCESS_COARSE_LOCATION
                ) != PERMISSION_GRANTED && checkSelfPermission(
                    this@RequestLocationUpdatesWithCallbackActivity,
                    "android.permission.ACCESS_BACKGROUND_LOCATION"
                ) != PERMISSION_GRANTED
            ) {
                val strings = arrayOf(
                    ACCESS_FINE_LOCATION,
                    ACCESS_COARSE_LOCATION,
                    "android.permission.ACCESS_BACKGROUND_LOCATION"
                )
                requestPermissions(this, strings, 2)
            }
        }
    }

    /**
     * function：Requests location updates with a callback on the specified Looper thread.
     * first：use SettingsClient object to call checkLocationSettings(LocationSettingsRequest locationSettingsRequest) method to check device settings.
     * second： use  FusedLocationProviderClient object to call requestLocationUpdates (LocationRequest request, LocationCallback callback, Looper looper) method.
     */
    private fun requestLocationUpdatesWithCallback() {
        try {
            val builder = LocationSettingsRequest.Builder()
            builder.addLocationRequest(mLocationRequest)
            val locationSettingsRequest = builder.build()
            // check devices settings before request location updates.
            //Before requesting location update, invoke checkLocationSettings to check device settings.
            val locationSettingsResponseTask: Task<LocationSettingsResponse> = settingsClient.checkLocationSettings(locationSettingsRequest)

            locationSettingsResponseTask.addOnSuccessListener { locationSettingsResponse: LocationSettingsResponse? ->
                    Log.i(TAG, "check location settings success  {$locationSettingsResponse}")
                    // request location updates
                    fusedLocationProviderClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.getMainLooper())
                        .addOnSuccessListener {
                            LocationLog.i(TAG, "requestLocationUpdatesWithCallback onSuccess")
                        }
                        .addOnFailureListener { e ->
                            LocationLog.e(TAG, "requestLocationUpdatesWithCallback onFailure:${e.message}")
                        }
                }
                .addOnFailureListener { e: Exception -> LocationLog.e(TAG, "checkLocationSetting onFailure:${e.message}")
                    when ((e as ApiException).statusCode) {
                        LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> try {
                            val rae = e as ResolvableApiException
                            rae.startResolutionForResult(
                                this@RequestLocationUpdatesWithCallbackActivity, 0
                            )
                        } catch (sie: SendIntentException) {
                            Log.e(TAG, "PendingIntent unable to execute request.")
                        }
                    }
                }
        } catch (e: Exception) {
            LocationLog.e(TAG, "requestLocationUpdatesWithCallback exception:${e.message}")
        }
    }

    override fun onDestroy() {
        // don't need to receive callback
        removeLocationUpdatesWithCallback()
        super.onDestroy()
    }

    /**
     * remove the request with callback
     */
    private fun removeLocationUpdatesWithCallback() {
        try {
            fusedLocationProviderClient.removeLocationUpdates(mLocationCallback)
                .addOnSuccessListener {
                    LocationLog.i(
                        TAG,
                        "removeLocationUpdatesWithCallback onSuccess"
                    )
                }
                .addOnFailureListener { e ->
                    LocationLog.e(
                        TAG,
                        "removeLocationUpdatesWithCallback onFailure:${e.message}"
                    )
                }
        } catch (e: Exception) {
            LocationLog.e(
                TAG,
                "removeLocationUpdatesWithCallback exception:${e.message}"
            )
        }
    }

    override fun onClick(v: View) {
        try {
            when (v.id) {
                R.id.location_requestLocationUpdatesWithCallback -> requestLocationUpdatesWithCallback()
                R.id.location_removeLocationUpdatesWithCallback -> removeLocationUpdatesWithCallback()

            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "RequestLocationUpdatesWithCallbackActivity Exception:$e"
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.size > 1 && grantResults[0] == PERMISSION_GRANTED && grantResults[1] == PERMISSION_GRANTED
            ) {
                Log.i(
                    TAG, "onRequestPermissionsResult: apply LOCATION PERMISSION successful"
                )
            } else {
                Log.i(
                    TAG, "onRequestPermissionsResult: apply LOCATION PERMISSSION  failed"
                )
            }
        }
        if (requestCode == 2) {
            if (grantResults.size > 2 && grantResults[2] == PERMISSION_GRANTED && grantResults[0] == PERMISSION_GRANTED && grantResults[1] == PERMISSION_GRANTED
            ) {
                Log.i(
                    TAG, "onRequestPermissionsResult: apply ACCESS_BACKGROUND_LOCATION successful"
                )
            } else {
                Log.i(
                    TAG, "onRequestPermissionsResult: apply ACCESS_BACKGROUND_LOCATION  failed"
                )
            }
        }
    }
}
