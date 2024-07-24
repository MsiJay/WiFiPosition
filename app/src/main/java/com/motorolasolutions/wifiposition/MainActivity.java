package com.motorolasolutions.wifiposition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.lemmingapex.trilateration.NonLinearLeastSquaresSolver;
import com.lemmingapex.trilateration.TrilaterationFunction;

import org.apache.commons.math3.fitting.leastsquares.LeastSquaresOptimizer;
import org.apache.commons.math3.fitting.leastsquares.LevenbergMarquardtOptimizer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String[] permissions = {Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.NEARBY_WIFI_DEVICES};
    private static final int permissionCode = 777;
    private Timer timer = new Timer();
    private final HashMap<String, String> macSsidMap = new HashMap<>();
    private ImageView imageView,me;
    private ImageView r1,r2,r3;
    private  static final float mapRatioX = 0.97f, mapRatioY =0.95f;
    private static final float r1X = 0.95f* mapRatioX;
    private static final float r1Y = 0.6f*mapRatioY;
    private static final float r2X = 0.83f* mapRatioX;
    private static final float r2Y = 0.25f*mapRatioY;
    private static final float r3X = 0.7f* mapRatioX;
    private static final float r3Y = 0.9f*mapRatioY;
    private static final String r1Name = "setup61FA.ynk";
    private static final String r2Name = "setup6461.ynk";
    private static final String r3Name = "setupEB71.ynk";

    float w;

    float h;

    double ratio;


    WifiRttManager wifiRttManager;
    BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (wifiRttManager.isAvailable()) {
                Log.d(TAG, "onReceive: wifiRTT is available");
            } else {
                Log.d(TAG, "onReceive: wifiRTT is not available");
            }
        }
    };




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
       /* ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });*/
        imageView = findViewById(R.id.map);

        r1 = findViewById(R.id.R1);
        r2 = findViewById(R.id.R2);
        r3 = findViewById(R.id.R3);
        me = findViewById(R.id.me);
        printOutImageStats();
    }

    private void printOutImageStats(){
        ViewTreeObserver vto = imageView.getViewTreeObserver();
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            public boolean onPreDraw() {
                imageView.getViewTreeObserver().removeOnPreDrawListener(this);
                w = imageView.getMeasuredWidth();
                h = imageView.getMeasuredHeight();
                Log.d(TAG, "printOutImageStats: x: "+w + " y: "+h);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        r1.setX(w*r1X);
                        r1.setY(h*r1Y);
                        r2.setX(w*r2X);
                        r2.setY(h*r2Y);
                        r3.setX(w*r3X);
                        r3.setY(h*r3Y);
                        //don't change this line
                        ratio = r3.getX()/50;
                        Log.d(TAG, "run: ratio is "+ratio + " r1 x is "+r1.getX());

                    }
                });
                return true;
            }
        });


    }


    private void startScanningForWifi() {
        WifiManager wifiManager =
                (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(permissions,permissionCode);
            return;
        }


        List<ScanResult> results = wifiManager.getScanResults();
        Log.d(TAG, "startScanningForWifi: found  " + results.size());
        RangingRequest.Builder builder = new RangingRequest.Builder();
        int limit = RangingRequest.getMaxPeers();
        Log.d(TAG, "startScanningForWifi: peer limit " + limit);
        for (ScanResult result : results) {
            if (result.is80211mcResponder()) {
                String ssid = Objects.requireNonNull(result.getWifiSsid()).toString().replace("\"", "");
                Log.d(TAG,
                        "startScanningForWifi: result " + ssid + " BSSID: " + result.BSSID );
                macSsidMap.put(result.BSSID, Objects.requireNonNull(ssid));
                builder.addAccessPoint(result);
            }

        }
        if(timer != null) {
            timer.cancel();
            timer = new Timer(); // Create a new Timer instance
        }
        TimerTask task = getTimerTask(builder);
        timer.schedule(task, 0, 1500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (arePermissionsMissing()) {
            requestPermissions(permissions, permissionCode);
        }

        IntentFilter filter = new IntentFilter(WifiRttManager.ACTION_WIFI_RTT_STATE_CHANGED);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        wifiRttManager = (WifiRttManager) getSystemService(Context.WIFI_RTT_RANGING_SERVICE);

        registerReceiver(myReceiver, filter);
        boolean isRttFeatureEnabled = getApplicationContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI_RTT);
        if (isRttFeatureEnabled) {
            startScanningForWifi();
        } else {
            Toast.makeText(this, "Wifi RTT feature is not supported in this device",
                    Toast.LENGTH_SHORT).show();
        }
        startScanningForWifi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        timer.cancel();
        unregisterReceiver(myReceiver);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == permissionCode) {
            startScanningForWifi();
        }
    }

    private void calculatePositions(double distanceToR1InM, double distanceToR2InM, double distanceToR3InM){
        double[][] positions = new double[][] { { r1.getX(), r1.getY() }, { r2.getX(), r2.getY() }, { r3.getX(), r3.getY()}};

        double[] distances = new double[] { distanceToR1InM*ratio, distanceToR2InM*ratio, distanceToR3InM*ratio};
        NonLinearLeastSquaresSolver solver = new NonLinearLeastSquaresSolver(new TrilaterationFunction(positions, distances), new LevenbergMarquardtOptimizer());
        LeastSquaresOptimizer.Optimum optimum = solver.solve();
        Log.d(TAG, "calculatePositions: "+ Arrays.toString(optimum.getPoint().toArray()));
        float x = (float) optimum.getPoint().toArray()[0] ;
        float y = (float) optimum.getPoint().toArray()[1];
        if(x < 0 || y < 0 || x>w ||y> h){
            Log.w(TAG, "calculatePositions: out of bound" );
            return;
        }


        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                me.setX(x);
                me.setY(y);

            }
        });
    }



    private boolean arePermissionsMissing() {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }
    @SuppressLint("MissingPermission")
    private @NonNull TimerTask getTimerTask(@NonNull RangingRequest.Builder builder) {
        RangingRequest req = builder.build();
        return new TimerTask() {
            @Override
            public void run() {
                if (arePermissionsMissing()) {
                   requestPermissions(permissions,permissionCode);
                    return;
                }
                wifiRttManager.startRanging(req, getMainExecutor(), new RangingResultCallback() {
                    @Override
                    public void onRangingFailure(int code) {
                        Log.d(TAG, "onRangingFailure() called with: code = [" + code + "]");
                    }
                    @Override
                    public void onRangingResults(@NonNull List<RangingResult> results) {
                        if(results.size() <3){
                            Log.w(TAG, "onRangingResults: need at least 3 ap to get locations" );
                            return;
                        }

                        Double d1 = null, d2 =null, d3 =null;

                        for (RangingResult result : results) {
                            if (result != null && result.getStatus() == RangingResult.STATUS_SUCCESS) {
                                String ssid = macSsidMap.get(Objects.requireNonNull(result.getMacAddress()).toString());
                                Log.d(TAG, "onRangingResults: " +ssid + " distance: " + result.getDistanceMm() + " mm");
                                if(r1Name.equals(ssid)){
                                   d1 =  ((double)result.getDistanceMm()/1000);
                                }

                                if(r2Name.equals(ssid)){
                                    d2 =  ((double)result.getDistanceMm()/1000);
                                }

                                if(r3Name.equals(ssid)){
                                    d3 =  ((double)result.getDistanceMm()/1000);
                                }

                            }
                        }

                        if(d1 == null || d2 == null || d3 == null){
                            Log.w(TAG, "onRangingResults: not enough data" );
                            return;
                        }

                        Log.d(TAG, "onRangingResults: d1 "+ d1 +" d2 "+d2 + " d3 "+d3);
                        calculatePositions(d1,d2,d3);

                    }
                });
            }
        };
    }

}