package com.example.testapplication;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class BLEListAdapter extends BaseAdapter {
    private Context context; //context
    private ArrayList<BLEDeviceItem> mLeItems; //data source of the list adapter
    private ArrayList<BluetoothDevice> mLeDevices; //data source of the list adapter

    //public constructor
    public BLEListAdapter(Context context) {
        this.context = context;

        mLeItems = new ArrayList<BLEDeviceItem>();
        mLeDevices = new ArrayList<BluetoothDevice>();

    }

    public void addDevice(BluetoothDevice device){
        if(!mLeDevices.contains(device)) {
            mLeDevices.add(device);
            addItem(new BLEDeviceItem(device));
        }
    }
    private void addItem(BLEDeviceItem item) {
        if(!mLeItems.contains(item)) {
            mLeItems.add(item);
        }
    }

    public BluetoothDevice getDevice(int position) {
        return mLeItems.get(position).device;
    }

    public BLEDeviceItem getItem(String address){

        for(int i = 0; i < mLeDevices.size(); i++){
            if(mLeDevices.get(i).getAddress().equals(address)){
                return mLeItems.get(i);
            }
        }

        return null;
    }

    public void clear() {
        mLeItems.clear();
        mLeDevices.clear();
    }

    @Override
    public int getCount() {
        return mLeItems.size();
    }

    @Override
    public Object getItem(int i) {
        return mLeItems.get(i);
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        ViewHolder viewHolder;
        // General ListView optimization code.
        if (view == null) {
            view = LayoutInflater.from(context).inflate(R.layout.listitem_device, null);
            viewHolder = new ViewHolder();
            viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
            viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
            viewHolder.connectionState = (ImageView) view.findViewById(R.id.connection_icon);
//            viewHolder.connectButton = (Button) view.findViewById(R.id.connect_button);
            view.setTag(viewHolder);
        } else {
            viewHolder = (ViewHolder) view.getTag();
        }

        BLEDeviceItem item = mLeItems.get(i);
        BluetoothDevice device = getDevice(i);
        final String deviceName = device.getName();
        if (deviceName != null && deviceName.length() > 0)
            viewHolder.deviceName.setText(deviceName);
        else
            viewHolder.deviceName.setText(R.string.unknown_device);

        viewHolder.deviceAddress.setText(device.getAddress());

        Drawable icon = viewHolder.connectionState.getDrawable();
        switch(item.state){
            case UNCONNECTED:
                setDrawableColor(icon, R.color.disconnected);
                break;
            case CONNECTED:
                setDrawableColor(icon, R.color.connected);
                break;
            case BROKEN:
                setDrawableColor(icon, R.color.broken);
                break;
        }

        return view;
    }

    void setDrawableColor(Drawable drawable, final int color){
        if (drawable instanceof ShapeDrawable) {
            ((ShapeDrawable)drawable).getPaint().setColor(ContextCompat.getColor(context, color));
        } else if (drawable instanceof GradientDrawable) {
            ((GradientDrawable)drawable).setColor(ContextCompat.getColor(context, color));
        } else if (drawable instanceof ColorDrawable) {
            ((ColorDrawable)drawable).setColor(ContextCompat.getColor(context, color));
        }
    }

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
//        Button connectButton;

        ImageView connectionState;
    }
}
