package org.itxtech.daedalus.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.itxtech.daedalus.Daedalus;
import org.itxtech.daedalus.R;
import org.itxtech.daedalus.activity.MainActivity;
import org.itxtech.daedalus.service.DaedalusVpnService;
import org.itxtech.daedalus.util.server.DNSServer;
import org.json.JSONException;
import org.json.JSONObject;

import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.MediaType;

import java.io.IOException;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Daedalus Project
 *
 * @author iTX Technologies
 * @link https://itxtech.org
 * <p>
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
public class HomeFragment extends ToolbarFragment {

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    interface Promise {

        void onDoneGetMacAddress(String mAddr);
    }

    private String getMacAddress(Promise callback) {

        if (Build.VERSION.SDK_INT >= 23) { // Build.VERSION_CODES.M
            String macAddress = getMMacAddress();
            callback.onDoneGetMacAddress(macAddress);
            return macAddress;
        }

        String macAddress = getLegacyMacAddress();
        callback.onDoneGetMacAddress(macAddress);
        return macAddress;

    }

    private String getLegacyMacAddress() {

        String macAddress = null;

        WifiManager wm = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        macAddress = wm.getConnectionInfo().getMacAddress();

        if (macAddress == null || macAddress.length() == 0) {
            macAddress = "02:00:00:00:00:00";
        }

        return macAddress;

    }

    private String getMMacAddress() {

        try {
            List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());

            for (NetworkInterface nif : all) {
                if (!nif.getName().equalsIgnoreCase("wlan0")) continue;

                byte[] macBytes = nif.getHardwareAddress();
                if (macBytes == null) {
                    return "";
                }

                StringBuilder res1 = new StringBuilder();
                for (byte b : macBytes) {
                    res1.append(String.format("%02x", (b & 0xFF)) + ":");
                }

                if (res1.length() > 0) {
                    res1.deleteCharAt(res1.length() - 1);
                }
                return res1.toString();
            }
        } catch (Exception ex) { }

        return "02:00:00:00:00:00";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_main, container, false);

        Button but = view.findViewById(R.id.button_activate);

        EditText agwText = view.findViewById(R.id.editView_AGW);
        agwText.setText(Daedalus.AGWLink);

        EditText dnsServer = view.findViewById(R.id.editView_dns);
        dnsServer.setText(Daedalus.dnsServer);

        EditText email = view.findViewById(R.id.editView_login);
        EditText password = view.findViewById(R.id.editView_password);

        but.setOnClickListener((View v) -> {
            Log.d("Home","Button Clicked.");
            if(but.getText() == getResources().getString(R.string.button_text_activating)){
                return;
            }
            if (DaedalusVpnService.isActivated()) {
                Daedalus.deactivateService(getActivity().getApplicationContext());
            } else {
                getMacAddress(mAddr -> {
                    Log.d("MacAddress", mAddr);
                    Daedalus.setMacAddress(mAddr);

                    getActivity().runOnUiThread(() -> {
                        Button button = view.findViewById(R.id.button_activate);
                        button.setText(getResources().getString(R.string.button_text_activating));
                        TextView errorText1 = view.findViewById(R.id.textView_errorMessage);
                        errorText1.setText("");
                    });

                    Daedalus.AGWLink = agwText.getText().toString();

                    Daedalus.DNS_SERVERS = new ArrayList<DNSServer>() {{
                        add(new DNSServer(dnsServer.getText().toString(), R.string.server_fundns_south_china));
                    }};

                    OkHttpClient client = new OkHttpClient();

                    RequestBody body = RequestBody.create(JSON, "{\"username\":\"" + email.getText().toString() + "\", \"password\": \"" + get_SHA_512_SecurePassword(new String(password.getText().toString()), "") + "\"}");
                    Request request = new Request.Builder()
                            .addHeader("Content-Type", "application/json")
                            .addHeader("ATOKEN", "OWQ5ZWYwZjAtOGIzZS0xMWU0LTg0MTktNzExMmVjMDg0Yjc2")
                            .url(Daedalus.AGWLink + "/account/login")
                            .post(body)
                            .build();
                    client.newCall(request).enqueue(new Callback() {
                        @Override
                        public void onFailure(Request request, IOException e) {

                        }

                        @Override
                        public void onResponse(Response response) throws IOException {
                            if (!response.isSuccessful()) {
                                getActivity().runOnUiThread(() -> {
                                    Button button = view.findViewById(R.id.button_activate);
                                    button.setText(getResources().getString(R.string.button_text_activate));
                                    TextView errorText1 = view.findViewById(R.id.textView_errorMessage);
                                    errorText1.setText("Unexpected code error.");
                                });
                                throw new IOException("Unexpected code " + response.body().string());
                            } else {
                                try {
                                    JSONObject json = new JSONObject(response.body().string());
                                    JSONObject jsonData = json.getJSONObject("data");
                                    Log.d("Home", jsonData.toString());
                                    if(json.getBoolean("error") == true){
                                        getActivity().runOnUiThread(() -> {
                                            Button button = view.findViewById(R.id.button_activate);
                                            button.setText(getResources().getString(R.string.button_text_activate));
                                            TextView errorText1 = view.findViewById(R.id.textView_errorMessage);
                                            try {
                                                errorText1.setText(jsonData.getString("message"));
                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                        });
                                    }
                                    else {
                                        Daedalus.setAccountId(jsonData.getString("_id"));
                                        startActivity(new Intent(getActivity(), MainActivity.class)
                                                .putExtra(MainActivity.LAUNCH_ACTION, MainActivity.LAUNCH_ACTION_ACTIVATE));
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    });

                });

            }
        });

        return view;
    }

    public String get_SHA_512_SecurePassword(String passwordToHash, String   salt){
        String generatedPassword = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] bytes = md.digest(passwordToHash.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for(int i=0; i< bytes.length ;i++){
                sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
            }
            generatedPassword = sb.toString();
        }
        catch (NoSuchAlgorithmException e){
            e.printStackTrace();
        }
        return generatedPassword;
    }

    @Override
    public void checkStatus() {
        menu.findItem(R.id.nav_home).setChecked(true);
        toolbar.setTitle(R.string.action_home);
        updateUserInterface();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            updateUserInterface();
        }
    }

    private void updateUserInterface() {
        Log.d("DMainFragment", "updateInterface");
        Button but = getView().findViewById(R.id.button_activate);
        if (DaedalusVpnService.isActivated()) {
            but.setText(R.string.button_text_deactivate);
        } else {
            but.setText(R.string.button_text_activate);
        }

    }
}
