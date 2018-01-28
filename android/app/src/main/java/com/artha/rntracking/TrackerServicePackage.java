package com.artha.rntracking;

import android.location.Location;
import android.widget.Toast;

import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.uimanager.ViewManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by arthaprihardana on 28/01/18.
 */

public class TrackerServicePackage implements ReactPackage {
    @Override
    public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Collections.emptyList();
    }

    @Override
    public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        List<NativeModule> modules = new ArrayList<>();

        modules.add(new TrackerServiceModule(reactContext));
//        modules.add(new TrackerServiceModule(reactContext) {
//            @Override
//            public void onLocationChanged(Location location) {
//                Toast.makeText(getReactApplicationContext(), "onLocationChanged", Toast.LENGTH_SHORT).show();
//            }
//        });

        return modules;
    }
}
