package com.example.testapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.IntentService;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "MainTest";

    public static String CHANNEL_ID = "11311";

    public final static String ACTION_CLEAR = "com.example.testapplication.ACTION_CLEAR";
    public final static String ACTION_SNOOZE = "com.example.testapplication.ACTION_SNOOZE";

    Button connectButton;
    Button calibrateButton;
    Button disarmButton;

    public NotificationCompat.Builder builder;

    BluetoothManager bluetoothManager;
    BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private BLEListAdapter leDeviceListAdapter = new BLEListAdapter(this);
    private boolean scanning;
    private String deviceAddress = "";
    private boolean connected = false;
    private boolean disconnectRequested;
    private Handler handler = new Handler();
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics;
    private BluetoothGattCharacteristic characteristicTX = null;
    private BluetoothGattCharacteristic characteristicRX = null;

    public final static UUID HM_RX_TX = UUID.fromString(SampleGattAttributes.HM_RX_TX);

    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";

    private static final long SCAN_PERIOD = 10000;

    private BluetoothLeService bluetoothService;

    private final BroadcastReceiver gattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                connected = true;
                updateConnectionState(R.string.disconnectPrompt);
                Log.d(TAG, "GATT CONNECTED");
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                connected = false;
                updateConnectionState(R.string.connectPrompt);
                Log.d(TAG, "GATT DISCONNECTED");
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
//                displayGattServices(bluetoothService.getSupportedGattServices());
                Log.d(TAG, "GATT DISCOVERY " + bluetoothService.getSupportedGattServices().size());
                if (bluetoothService.getSupportedGattServices() != null){
                    Log.d(TAG, "Discovered: " + bluetoothService.getSupportedGattServices().size());
                    displayGattServices(bluetoothService.getSupportedGattServices());

                }
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)){
                process_message(intent.getStringExtra(bluetoothService.EXTRA_DATA));
            }

            leDeviceListAdapter.notifyDataSetChanged();
        }
    };

    private void updateConnectionState(int state){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
//                mConnectionState.setText(resourceId);
                connectButton.setText(state);
//                leDeviceListAdapter.
                calibrateButton.setEnabled(connected);

            }
        });

        BLEDeviceItem.ConnectionState cState = connected ? BLEDeviceItem.ConnectionState.CONNECTED : BLEDeviceItem.ConnectionState.UNCONNECTED;
        if(!connected && !disconnectRequested){
            cState = BLEDeviceItem.ConnectionState.BROKEN;

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            builder.setContentTitle("Bluetooth connection lost!");
            builder.setContentText("Connection must be restored to provide system function");
            builder.clearActions();
            notificationManager.notify(0, builder.build());
        }

        BLEDeviceItem item = leDeviceListAdapter.getItem(deviceAddress);
        if(item != null){
            item.state = cState;
            leDeviceListAdapter.notifyDataSetChanged();
        }

        disconnectRequested = false;
    }

    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));

            // If the service exists for HM 10 Serial, say so.
            Log.d(TAG, "Service: " + SampleGattAttributes.lookup(uuid, unknownServiceString));

            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            // get characteristic when UUID matches RX/TX UUID
            characteristicTX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
            characteristicRX = gattService.getCharacteristic(BluetoothLeService.UUID_HM_RX_TX);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        registerReceiver(gattUpdateReceiver, makeGattUpdateIntentFilter());
        if (bluetoothService != null) {
            final boolean result = bluetoothService.connect(deviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(gattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(serviceConnection);
        bluetoothService = null;
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        createNotificationChannel();
        // Create an explicit intent for an Activity in your app.
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("My notification")
                .setContentText("Hello World!")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                // Set the intent that fires when the user taps the notification.
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);


        Button scanButton = (Button) findViewById(R.id.scan_button);
        scanButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                scanLeDevice();
//                test();
            }
        });

        ListView itemsListView  = (ListView) findViewById(R.id.list_device_view);
        itemsListView.setAdapter(leDeviceListAdapter);
        itemsListView.setOnItemClickListener(new ListView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(!connected){
                    deviceAddress = leDeviceListAdapter.getDevice(position).getAddress();
                    connectButton.setEnabled(deviceAddress != null && !deviceAddress.isEmpty());
                }
            }
        });

        connectButton = (Button) findViewById(R.id.connect_button);
        connectButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent gattServiceIntent = new Intent(MainActivity.this, BluetoothLeService.class);
                bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

                if(connected){
                    Log.d(TAG, "Disconnect request");
                    disconnectRequested = true;
                    bluetoothService.disconnect();
                }else{
                    bluetoothService.connect(deviceAddress);
                }
            }
        });

        calibrateButton = (Button) findViewById(R.id.calibrate_button);
        calibrateButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                send_message("c");
            }
        });

        disarmButton = (Button) findViewById(R.id.disarm_button);
        disarmButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                send_message("x");

                disarmButton.setVisibility(Button.GONE);
            }
        });

        Handler handler = new Handler();
        handler.post(new Runnable(){
           @Override
           public void run(){
               if(connected){
                   send_message("p");
               }
               handler.postDelayed(this, 1000);
           }
        });

//        Button bu = (Button) findViewById(R.id.submit_button);
//        EditText field = (EditText) findViewById(R.id.data_field);
//        bu.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//                send_message(field.getText().toString());
//            }
//        });

        bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, serviceConnection, Context.BIND_AUTO_CREATE);

    }

//    private void test(){
//        NotificationManager notificationManager = getSystemService(NotificationManager.class);
//        builder.setContentTitle("Patient monitor triggered!");
//        builder.setContentText("Please check up");
//        builder.clearActions();
//
//        Intent disarmIntent = new Intent(this, NotificationActionService.class);
//        disarmIntent.setAction(ACTION_CLEAR);
//
////        disarmIntent.putExtra(EXTRA_NOTIFICATION_ID, 0);
//        PendingIntent disarmPendingIntent = PendingIntent.getService(this, 0, disarmIntent, PendingIntent.FLAG_ONE_SHOT);
//        builder.addAction(R.drawable.ic_snooze, getString(R.string.disarm), disarmPendingIntent);
//
//        Intent snoozeIntent = new Intent(this, NotificationActionService.class);
//        snoozeIntent.setAction(ACTION_SNOOZE);
////            disarmIntent.putExtra
//        PendingIntent snoozePendingIntent = PendingIntent.getService(this, 0, snoozeIntent, PendingIntent.FLAG_ONE_SHOT);
//        builder.addAction(R.drawable.ic_snooze, getString(R.string.snooze), snoozePendingIntent);
//
//        notificationManager.notify(0, builder.build());
//    }

    private void send_message(String message){
        Log.d(TAG, "Sending message: " + message);
        if(characteristicTX == null){
            displayGattServices(bluetoothService.getSupportedGattServices());

            if(characteristicTX == null){
                return;
            }
        }

        String str = message + " \n";
        byte[] data = str.getBytes();

        characteristicTX.setValue(data);
        bluetoothService.writeCharacteristic(characteristicTX);
        bluetoothService.setCharacteristicNotification(characteristicRX, true);
    }

    private void process_message(String message){
        Log.d(TAG, "Monitor output: " + message);

        char command = 'N';
        if(message.length() == 3 && (byte)message.charAt(2) == 10 && (byte)message.charAt(1) == 13){
            command = message.charAt(0);
        }
//        for(int j = 0; j < message.length(); j++){
//            Log.d(TAG, (byte)message.charAt(j) + "");
//        }

        if(command == 'T'){
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            builder.setContentTitle("Patient monitor triggered!");
            builder.setContentText("Please check up");
            notificationManager.notify(0, builder.build());

            disarmButton.setVisibility(Button.VISIBLE);
        }else if(command == 'C'){
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            builder.setContentTitle("Calibration process");
            builder.setContentText("Calibration complete!");
            builder.clearActions();
            notificationManager.notify(0, builder.build());
        }else if(command == 'g'){
            disarmButton.setVisibility(Button.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }


    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Service connection");
            bluetoothService = ((BluetoothLeService.LocalBinder) service).getService();
            if (bluetoothService != null) {
                if (!bluetoothService.initialize()) {
                    Log.e(TAG, "Unable to initialize Bluetooth");
                    finish();
                }

                // perform device connection
//                bluetoothService.connect(deviceAddress);
            }

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            bluetoothService = null;
        }
    };


    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is not in the Support Library.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this.
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    // Permission is granted. Continue the action or workflow in your
                    // app.
                } else {
                    // Explain to the user that the feature is unavailable because the
                    // feature requires a permission that the user has denied. At the
                    // same time, respect the user's decision. Don't link to system
                    // settings in an effort to convince the user to change their
                    // decision.
                }
            });

    private void scanLeDevice() {
        if (!scanning) {
            // Stops scanning after a predefined scan period.
            handler.postDelayed(new Runnable() {
                @SuppressLint("MissingPermission")
                @Override
                public void run() {
                    scanning = false;
                    bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);

            Log.d("BLE", "Here!");
            scanning = true;
//            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
//                Log.d("BLE", "Permission already granted");
//                return;
//            }else if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.BLUETOOTH_SCAN)){
//                Log.d("BLE", "X");
//            } else {
//                Log.d("BLE", "Ohh");
////                ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.BLUETOOTH_SCAN}, 1);
//                requestPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN);
//            }

            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Log.d("BLE", "Permission already granted");
            }else if (ActivityCompat.shouldShowRequestPermissionRationale(this, android.Manifest.permission.ACCESS_FINE_LOCATION)){
                Log.d("BLE", "X");
            } else {
                Log.d("BLE", "Ohh");
//                ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.BLUETOOTH_SCAN}, 1);
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            }

            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            scanning = false;
            bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    private ScanCallback leScanCallback =
            new ScanCallback() {
                @SuppressLint("MissingPermission")
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    int count = leDeviceListAdapter.getCount();
                    leDeviceListAdapter.addDevice(result.getDevice());

                    if(count != leDeviceListAdapter.getCount()) leDeviceListAdapter.notifyDataSetChanged();
                }

                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                    Log.d("BLE", "Error: " + errorCode);
                }
            };

}