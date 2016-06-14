/*
 * Copyright (C) 2016 Kane O'Riley
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package me.oriley.shiv;

import android.accounts.AccountManager;
import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.app.job.JobScheduler;
import android.app.usage.NetworkStatsManager;
import android.app.usage.UsageStatsManager;
import android.appwidget.AppWidgetManager;
import android.bluetooth.BluetoothManager;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.RestrictionsManager;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.hardware.ConsumerIrManager;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraManager;
import android.hardware.display.DisplayManager;
import android.hardware.fingerprint.FingerprintManager;
import android.hardware.input.InputManager;
import android.hardware.usb.UsbManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaRouter;
import android.media.midi.MidiManager;
import android.media.projection.MediaProjectionManager;
import android.media.session.MediaSessionManager;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.nsd.NsdManager;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pManager;
import android.nfc.NfcManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.DropBoxManager;
import android.os.PowerManager;
import android.os.UserManager;
import android.os.Vibrator;
import android.os.storage.StorageManager;
import android.print.PrintManager;
import android.service.wallpaper.WallpaperService;
import android.support.annotation.NonNull;
import android.telecom.TelecomManager;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.CaptioningManager;
import android.view.inputmethod.InputMethodManager;
import android.view.textservice.TextServicesManager;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public final class ServiceUtils {

    private static final Map<Class, String> SERVICE_MAP = new HashMap<>();

    static {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                SERVICE_MAP.put(InputManager.class, Context.INPUT_SERVICE);
                SERVICE_MAP.put(MediaRouter.class, Context.MEDIA_ROUTER_SERVICE);
                SERVICE_MAP.put(NsdManager.class, Context.NSD_SERVICE);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                SERVICE_MAP.put(DisplayManager.class, Context.DISPLAY_SERVICE);
                SERVICE_MAP.put(UserManager.class, Context.USER_SERVICE);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                SERVICE_MAP.put(BluetoothManager.class, Context.BLUETOOTH_SERVICE);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                SERVICE_MAP.put(AppOpsManager.class, Context.APP_OPS_SERVICE);
                SERVICE_MAP.put(CaptioningManager.class, Context.CAPTIONING_SERVICE);
                SERVICE_MAP.put(ConsumerIrManager.class, Context.CONSUMER_IR_SERVICE);
                SERVICE_MAP.put(PrintManager.class, Context.PRINT_SERVICE);
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                SERVICE_MAP.put(AppWidgetManager.class, Context.APPWIDGET_SERVICE);
                SERVICE_MAP.put(BatteryManager.class, Context.BATTERY_SERVICE);
                SERVICE_MAP.put(CameraManager.class, Context.CAMERA_SERVICE);
                SERVICE_MAP.put(JobScheduler.class, Context.JOB_SCHEDULER_SERVICE);
                SERVICE_MAP.put(LauncherApps.class, Context.LAUNCHER_APPS_SERVICE);
                SERVICE_MAP.put(MediaProjectionManager.class, Context.MEDIA_PROJECTION_SERVICE);
                SERVICE_MAP.put(MediaSessionManager.class, Context.MEDIA_SESSION_SERVICE);
                SERVICE_MAP.put(RestrictionsManager.class, Context.RESTRICTIONS_SERVICE);
                SERVICE_MAP.put(TelecomManager.class, Context.TELECOM_SERVICE);
                SERVICE_MAP.put(TvInputManager.class, Context.TV_INPUT_SERVICE);
            }

            // For informations sake
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SERVICE_MAP.put(CarrierConfigManager.class, Context.CARRIER_CONFIG_SERVICE);
                SERVICE_MAP.put(FingerprintManager.class, Context.FINGERPRINT_SERVICE);
                SERVICE_MAP.put(MidiManager.class, Context.MIDI_SERVICE);
                SERVICE_MAP.put(NetworkStatsManager.class, Context.NETWORK_STATS_SERVICE);
                SERVICE_MAP.put(SubscriptionManager.class, Context.TELEPHONY_SUBSCRIPTION_SERVICE);
                SERVICE_MAP.put(UsageStatsManager.class, Context.USAGE_STATS_SERVICE);
            }

            SERVICE_MAP.put(AccessibilityManager.class, Context.ACCESSIBILITY_SERVICE);
            SERVICE_MAP.put(AccountManager.class, Context.ACCOUNT_SERVICE);
            SERVICE_MAP.put(ActivityManager.class, Context.ACTIVITY_SERVICE);
            SERVICE_MAP.put(AlarmManager.class, Context.ALARM_SERVICE);
            SERVICE_MAP.put(AudioManager.class, Context.AUDIO_SERVICE);
            SERVICE_MAP.put(ClipboardManager.class, Context.CLIPBOARD_SERVICE);
            SERVICE_MAP.put(ConnectivityManager.class, Context.CONNECTIVITY_SERVICE);
            SERVICE_MAP.put(DevicePolicyManager.class, Context.DEVICE_POLICY_SERVICE);
            SERVICE_MAP.put(DownloadManager.class, Context.DOWNLOAD_SERVICE);
            SERVICE_MAP.put(DropBoxManager.class, Context.DROPBOX_SERVICE);
            SERVICE_MAP.put(InputMethodManager.class, Context.INPUT_METHOD_SERVICE);
            SERVICE_MAP.put(KeyguardManager.class, Context.KEYGUARD_SERVICE);
            SERVICE_MAP.put(LayoutInflater.class, Context.LAYOUT_INFLATER_SERVICE);
            SERVICE_MAP.put(LocationManager.class, Context.LOCATION_SERVICE);
            SERVICE_MAP.put(NfcManager.class, Context.NFC_SERVICE);
            SERVICE_MAP.put(NotificationManager.class, Context.NOTIFICATION_SERVICE);
            SERVICE_MAP.put(PowerManager.class, Context.POWER_SERVICE);
            SERVICE_MAP.put(SearchManager.class, Context.SEARCH_SERVICE);
            SERVICE_MAP.put(SensorManager.class, Context.SENSOR_SERVICE);
            SERVICE_MAP.put(StorageManager.class, Context.STORAGE_SERVICE);
            SERVICE_MAP.put(TelephonyManager.class, Context.TELEPHONY_SERVICE);
            SERVICE_MAP.put(TextServicesManager.class, Context.TEXT_SERVICES_MANAGER_SERVICE);
            SERVICE_MAP.put(UiModeManager.class, Context.UI_MODE_SERVICE);
            SERVICE_MAP.put(UsbManager.class, Context.USB_SERVICE);
            SERVICE_MAP.put(Vibrator.class, Context.VIBRATOR_SERVICE);
            SERVICE_MAP.put(WallpaperService.class, Context.WALLPAPER_SERVICE);
            SERVICE_MAP.put(WifiManager.class, Context.WIFI_SERVICE);
            SERVICE_MAP.put(WifiP2pManager.class, Context.WIFI_P2P_SERVICE);
            SERVICE_MAP.put(WindowManager.class, Context.WINDOW_SERVICE);
        }
    }

    @NonNull
    public static <T> T getService(@NonNull Context context, @NonNull Class<T> c) {
        if (c == PackageManager.class) {
            //noinspection unchecked
            return (T) context.getPackageManager();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.getSystemService(c);
        } else {
            String serviceName = SERVICE_MAP.get(c);
            if (serviceName == null) {
                throw new IllegalArgumentException("Invalid service class: " + c);
            } else {
                //noinspection unchecked,ResourceType
                return (T) context.getSystemService(serviceName);
            }
        }
    }
}