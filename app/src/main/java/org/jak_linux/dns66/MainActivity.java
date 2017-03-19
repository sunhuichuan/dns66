/* Copyright (C) 2016 Julian Andres Klode <jak@jak-linux.org>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package org.jak_linux.dns66;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;

import com.aurelhubert.ahbottomnavigation.AHBottomNavigation;
import com.aurelhubert.ahbottomnavigation.AHBottomNavigationAdapter;
import com.stephentuso.welcome.WelcomeHelper;

import org.jak_linux.dns66.main.MainFragmentPagerAdapter;
import org.jak_linux.dns66.vpn.AdVpnService;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLEncoder;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final int REQUEST_FILE_OPEN = 1;
    private static final int REQUEST_FILE_STORE = 2;
    private static final int REQUEST_ITEM_EDIT = 3;
    private static final int REQUEST_SHOW_WELCOME_SCREEN = 5;
    public static Configuration config;
    private ViewPager viewPager;
    private final BroadcastReceiver vpnServiceBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int str_id = intent.getIntExtra(AdVpnService.VPN_UPDATE_STATUS_EXTRA, R.string.notification_stopped);
            updateStatus(str_id);
        }
    };
    private AHBottomNavigation bottomNavigation;
    private ItemChangedListener itemChangedListener = null;
    private WelcomeHelper welcomeScreen;
    private MenuItem showNotificationMenuItem = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        welcomeScreen = new WelcomeHelper(this, DnsWelcomeActivity.class);
        welcomeScreen.show(savedInstanceState, REQUEST_SHOW_WELCOME_SCREEN);

        if (savedInstanceState == null) {
            config = FileHelper.loadCurrentSettings(this);
            //将需要屏蔽视频广告的host直接写死到本地

            BufferedInputStream bufferedIS = null;
            BufferedOutputStream bufferedOS = null;
            try {
                Configuration.Item item = config.hosts.items.get(0);
                String fireHostName = URLEncoder.encode(item.location);
                File contextFolder = this.getExternalFilesDir(null);
                File fileStreamPath = new File(contextFolder,fireHostName);
                if (!fileStreamPath.exists()){
                    InputStream hostInputStream = getAssets().open("shchosts.txt");
                    bufferedIS = new BufferedInputStream(hostInputStream);
                    bufferedOS = new BufferedOutputStream(new FileOutputStream(fileStreamPath));
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = bufferedIS.read(buffer))!=-1){
                        bufferedOS.write(buffer,0,length);
                    }
                    bufferedOS.flush();
                }
            } catch (IOException e) {
                Log.e(TAG,"拷贝异常",e);
            }finally {
                if (bufferedIS!=null){
                    try {
                        bufferedIS.close();
                    } catch (IOException e) {
                        Log.e(TAG,"关流异常",e);
                    }
                }
                if (bufferedOS!=null){
                    try {
                        bufferedOS.close();
                    } catch (IOException e) {
                        Log.e(TAG,"关流异常",e);
                    }
                }
            }


        }
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        viewPager = (ViewPager) findViewById(R.id.view_pager);


        int[] tabColors = {R.color.colorBottomNavigationPrimary, R.color.colorBottomNavigationPrimary, R.color.colorBottomNavigationPrimary, R.color.colorBottomNavigationPrimary, R.color.colorBottomNavigationPrimary,};
        bottomNavigation = (AHBottomNavigation) findViewById(R.id.bottom_navigation);
        AHBottomNavigationAdapter navigationAdapter = new AHBottomNavigationAdapter(this, R.menu.bottom_navigation);

        bottomNavigation.setForceTitlesDisplay(true);
        navigationAdapter.setupWithBottomNavigation(bottomNavigation, tabColors);

        reload();

        bottomNavigation.setOnTabSelectedListener(new AHBottomNavigation.OnTabSelectedListener() {
            @Override
            public boolean onTabSelected(int position, boolean wasSelected) {
                if (wasSelected) {
                    return true;
                }

                viewPager.setCurrentItem(position, false);
                return true;
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        welcomeScreen.onSaveInstanceState(outState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        showNotificationMenuItem = menu.findItem(R.id.setting_show_notification);
        showNotificationMenuItem.setChecked(config.showNotification);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_restore:
                config = FileHelper.loadPreviousSettings(this);
                FileHelper.writeSettings(this, MainActivity.config);
                reload();
                break;
            case R.id.action_refresh:
                refresh();
                break;
            case R.id.action_load_defaults:
                config = FileHelper.loadDefaultSettings(this);
                reload();
                FileHelper.writeSettings(this, MainActivity.config);
                break;
            case R.id.action_import:
                Intent intent = new Intent()
                        .setType("*/*")
                        .setAction(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE);

                startActivityForResult(intent, REQUEST_FILE_OPEN);
                break;
            case R.id.action_export:
                Intent exportIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*")
                        .putExtra(Intent.EXTRA_TITLE, "dns66.json");

                startActivityForResult(exportIntent, REQUEST_FILE_STORE);
                break;
            case R.id.action_whitelist:
                startActivity(new Intent(this, WhitelistActivity.class));
                break;
            case R.id.setting_show_notification:
                item.setChecked(!item.isChecked());
                MainActivity.config.showNotification = item.isChecked();
                FileHelper.writeSettings(this, MainActivity.config);
                break;
            case R.id.action_about:
                Intent infoIntent = new Intent(this, InfoActivity.class);
                startActivity(infoIntent);
                break;
        }

        return super.onOptionsItemSelected(item);
    }

    private void refresh() {
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        for (Configuration.Item item : config.hosts.items) {
            File file = FileHelper.getItemFile(this, item);

            if (file != null && item.state != 2) {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(item.location));
                Log.d("MainActivity", String.format("refresh: Downkoading %s to %s", item.location, file.getAbsolutePath()));
                file.delete();
                request.setDestinationUri(Uri.fromFile(file));
                request.setTitle(item.title);
                request.setVisibleInDownloadsUi(false);
                dm.enqueue(request);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.d("MainActivity", "onActivityResult: Received result=" + resultCode + " for request=" + requestCode);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_FILE_OPEN && resultCode == RESULT_OK) {
            Uri selectedfile = data.getData(); //The uri with the location of the file

            try {
                Configuration newConfig = new Configuration();
                newConfig.read(new JsonReader(new InputStreamReader(getContentResolver().openInputStream(selectedfile))));
                config = newConfig;
            } catch (Exception e) {
                Toast.makeText(this, "Cannot read file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            reload();
            FileHelper.writeSettings(this, MainActivity.config);
        }
        if (requestCode == REQUEST_FILE_STORE && resultCode == RESULT_OK) {
            Uri selectedfile = data.getData(); //The uri with the location of the file
            JsonWriter writer = null;
            try {
                writer = new JsonWriter(new OutputStreamWriter(getContentResolver().openOutputStream(selectedfile)));
                config.write(writer);
                writer.close();
            } catch (Exception e) {
                Toast.makeText(this, "Cannot write file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            } finally {
                try {
                    writer.close();
                } catch (Exception ignored) {

                }
            }
            reload();
        }
        if (requestCode == REQUEST_ITEM_EDIT && resultCode == RESULT_OK) {
            Configuration.Item item = new Configuration.Item();
            Log.d("FOOOO", "onActivityResult: item title = " + data.getStringExtra("ITEM_TITLE"));
            item.title = data.getStringExtra("ITEM_TITLE");
            item.location = data.getStringExtra("ITEM_LOCATION");
            item.state = data.getIntExtra("ITEM_STATE", 0);
            this.itemChangedListener.onItemChanged(item);
        }
    }

    private void updateStatus(int status) {
        if (viewPager.getChildAt(0) == null)
            return;
        TextView stateText = (TextView) viewPager.getChildAt(0).getRootView().findViewById(R.id.state_textview);
        if (stateText != null)
            stateText.setText(getString(AdVpnService.vpnStatusToTextId(status)));
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(vpnServiceBroadcastReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus(AdVpnService.vpnStatus);
        LocalBroadcastManager.getInstance(this)
                .registerReceiver(vpnServiceBroadcastReceiver, new IntentFilter(AdVpnService.VPN_UPDATE_STATUS_INTENT));
    }

    private void reload() {
        if (showNotificationMenuItem != null)
            showNotificationMenuItem.setChecked(config.showNotification);
        viewPager.setAdapter(new MainFragmentPagerAdapter(getSupportFragmentManager()));
        viewPager.setCurrentItem(bottomNavigation.getCurrentItem());
    }

    /**
     * Start the item editor activity
     *
     * @param item     an item to edit, may be null
     * @param listener A listener that will be called once the editor returns
     */
    public void editItem(Configuration.Item item, ItemChangedListener listener) {
        Intent editIntent = new Intent(this, ItemActivity.class);

        this.itemChangedListener = listener;
        if (item != null) {
            editIntent.putExtra("ITEM_TITLE", item.title);
            editIntent.putExtra("ITEM_LOCATION", item.location);
            editIntent.putExtra("ITEM_STATE", item.state);
        }
        startActivityForResult(editIntent, REQUEST_ITEM_EDIT);
    }
}
