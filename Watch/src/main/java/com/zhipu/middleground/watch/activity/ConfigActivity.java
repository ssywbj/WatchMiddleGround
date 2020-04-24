package com.zhipu.middleground.watch.activity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.zhipu.middleground.watch.R;
import com.zhipu.middleground.watch.service.MiddleGroundService;

public class ConfigActivity extends AppCompatActivity {
    public static final String TAG = ConfigActivity.class.getSimpleName();
    private Intent mMiddleGroundService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, TAG + ", onCreate");
        setContentView(R.layout.activity_config);
        mMiddleGroundService = new Intent(this, MiddleGroundService.class);
        startService(mMiddleGroundService);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, TAG + ", onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, TAG + ", onResume");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, TAG + ", onPause");
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, TAG + ", onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, TAG + ", onDestroy");
        stopService(mMiddleGroundService);
    }

}
