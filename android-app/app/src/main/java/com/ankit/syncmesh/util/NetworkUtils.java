package com.ankit.syncmesh.util;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

public final class NetworkUtils {
    private NetworkUtils() {
    }

    public static String getLocalIpv4Address() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return null;
            }
            String fallback = null;
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        if (address.isSiteLocalAddress()) {
                            return address.getHostAddress();
                        }
                        if (fallback == null) {
                            fallback = address.getHostAddress();
                        }
                    }
                }
            }
            return fallback;
        } catch (Exception exception) {
            SyncLog.e("NetworkUtils", "Failed to resolve local IPv4 address", exception);
            return null;
        }
    }

    public static List<InetAddress> getBroadcastAddresses() {
        List<InetAddress> addresses = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return addresses;
            }
            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress broadcast = interfaceAddress.getBroadcast();
                    if (broadcast instanceof Inet4Address && !addresses.contains(broadcast)) {
                        addresses.add(broadcast);
                    }
                }
            }
            InetAddress universal = InetAddress.getByName("255.255.255.255");
            if (!addresses.contains(universal)) {
                addresses.add(universal);
            }
        } catch (Exception exception) {
            SyncLog.e("NetworkUtils", "Failed to enumerate broadcast addresses", exception);
        }
        return addresses;
    }

    public static String shortenDeviceId(String deviceId) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            return "Unknown";
        }
        return deviceId.length() > 8 ? deviceId.substring(0, 8) : deviceId;
    }

    public static boolean isAccessibilityServiceEnabled(Context context, Class<?> serviceClass) {
        int accessibilityEnabled;
        try {
            accessibilityEnabled = Settings.Secure.getInt(context.getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
        } catch (Settings.SettingNotFoundException exception) {
            return false;
        }
        if (accessibilityEnabled != 1) {
            return false;
        }

        String enabledServices = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServices == null) {
            return false;
        }

        ComponentName componentName = new ComponentName(context, serviceClass);
        return enabledServices.contains(componentName.flattenToString());
    }

    public static boolean isInputMethodEnabled(Context context, Class<?> serviceClass) {
        InputMethodManager inputMethodManager = (InputMethodManager)
                context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager == null) {
            return false;
        }

        ComponentName componentName = new ComponentName(context, serviceClass);
        String shortId = componentName.flattenToShortString();
        String longId = componentName.flattenToString();
        for (InputMethodInfo inputMethodInfo : inputMethodManager.getEnabledInputMethodList()) {
            String inputMethodId = inputMethodInfo.getId();
            if (shortId.equals(inputMethodId)
                    || longId.equals(inputMethodId)
                    || (componentName.getPackageName().equals(inputMethodInfo.getPackageName())
                    && componentName.getClassName().equals(inputMethodInfo.getServiceName()))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isInputMethodSelected(Context context, Class<?> serviceClass) {
        try {
            String defaultInputMethod = Settings.Secure.getString(
                    context.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (defaultInputMethod == null || defaultInputMethod.trim().isEmpty()) {
                return false;
            }

            ComponentName componentName = new ComponentName(context, serviceClass);
            return componentName.flattenToShortString().equals(defaultInputMethod)
                    || componentName.flattenToString().equals(defaultInputMethod);
        } catch (SecurityException exception) {
            return false;
        }
    }
}
