package com.example.alaa.iot;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    TextView myLabel;
    EditText myTextbox;
    EditText MacTextbox;
    private  FirebaseDatabase database ;
    private  DatabaseReference myRef;
    BluetoothAdapter mBluetoothAdapter;
    BluetoothSocket mmSocket;
    BluetoothDevice mmDevice;
    OutputStream mmOutputStream;
    InputStream mmInputStream;
    Thread workerThread;
    Button GetData;
    String s;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;
    ListView AvailableDevices;
    final ArrayList<String> AvailableMacAddreses= new ArrayList<String>();
    ArrayAdapter adapter;
    final ArrayList<String> AvailableDevicesNames= new ArrayList<String>();
    final ArrayList<String> NameAndMac= new ArrayList<String>();
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        database = FirebaseDatabase.getInstance();
        myRef = database.getReference();
        myLabel = (TextView)findViewById(R.id.Displaybox);
        myTextbox = (EditText)findViewById(R.id.editText);
        MacTextbox=(EditText) findViewById(R.id.editText4);
        Button FindBT=(Button) findViewById(R.id.FindBT);
        FindBT.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                Intent i = new Intent(MainActivity.this,AvailablePlacesActivity.class);
                startActivity(i);
                return false;
            }
        });
        GetData=(Button) findViewById(R.id.GetData);
        GetData.setVisibility(View.INVISIBLE);
        GetData.setText("Get the data");
        FindBT.setText("Find Bluetooth");
        myTextbox.setText("Please Enter the device name or address");
        AvailableDevices=(ListView) findViewById(R.id.listView);
        adapter=new ArrayAdapter<String>(this,R.layout.listitem,R.id.textView3,NameAndMac);
        AvailableDevices.setAdapter(adapter);
        FindBT.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                findBT();
            }
        });

        GetData.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    openBT();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        AvailableDevices.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                myTextbox.setText(AvailableDevicesNames.get(position));
                MacTextbox.setText(AvailableMacAddreses.get(position));
            }
        });
    }


    void findBT()
    {
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(mBluetoothAdapter == null)
        {
            myLabel.setText("No bluetooth adapter available");
        }

        if(!mBluetoothAdapter.isEnabled())
        {
            Intent enableBluetooth = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableBluetooth, 0);
    }


        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            AvailableDevicesNames.clear();
            AvailableMacAddreses.clear();
            NameAndMac.clear();
            for(BluetoothDevice device : pairedDevices)
            {
                AvailableMacAddreses.add(device.getAddress());
                AvailableDevicesNames.add(device.getName());
                NameAndMac.add("Name: "+device.getName()+" MAC: "+device.getAddress());
                AvailableDevices.setAdapter(adapter);
            }
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getAddress().equals(MacTextbox.getText().toString()) || device.getName().equals(myTextbox.getText().toString()))
                {
                    mmDevice = device;
                    GetData.setVisibility(View.VISIBLE);
                    myLabel.setText("Bluetooth Device Found");
                    break;
                }else{
                    myLabel.setText("Bluetooth Device Not Found");
                }
            }
        }


    }

    void openBT() throws IOException {
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"); //Standard SerialPortService ID
        try {
            mmSocket =(BluetoothSocket) mmDevice.getClass().getMethod("createRfcommSocket", new Class[] {int.class}).invoke(mmDevice,1);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
//        mmSocket = mmDevice.createInsecureRfcommSocketToServiceRecord(uuid);

                try{
                    mmSocket.connect();
                    mmOutputStream = mmSocket.getOutputStream();
                    mmInputStream = mmSocket.getInputStream();
                    beginListenForData();
                    myLabel.setText("Bluetooth Opened");
                }catch (Exception e){
                    myLabel.setText("Couldn't connect to this device, Please make sure that the device is turned on");
                    GetData.setVisibility(View.INVISIBLE);
                }




    }

    void beginListenForData()
    {
        final Handler handler = new Handler();
        final byte delimiter = 10; //This is the ASCII code for a newline character

        stopWorker = false;
        readBufferPosition = 0;
        readBuffer = new byte[1024];
        workerThread = new Thread(new Runnable()
        {
            public void run()
            {
                while(!Thread.currentThread().isInterrupted() && !stopWorker)
                {
                    try
                    {
                        int bytesAvailable = mmInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mmInputStream.read(packetBytes);
                            for(int i=0;i<bytesAvailable;i++)
                            {
                                byte b = packetBytes[i];
                                if(b == delimiter)
                                {
                                    byte[] encodedBytes = new byte[readBufferPosition];
                                    System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                    final String data = new String(encodedBytes, "US-ASCII");
                                    readBufferPosition = 0;

                                    handler.post(new Runnable()
                                    {
                                        public void run()
                                        {
                                            s=data;
                                            myLabel.setText(s);
                                            myRef.child("GUC").child("Available").setValue(s+"");
                                        }
                                    });
                                }
                                else
                                {
                                    readBuffer[readBufferPosition++] = b;
                                }
                            }
                        }
                    }
                    catch (IOException ex)
                    {
                        stopWorker = true;
                    }
                }
            }
        });

        workerThread.start();
    }
}
