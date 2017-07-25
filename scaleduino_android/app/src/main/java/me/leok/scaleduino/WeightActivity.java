package me.leok.scaleduino;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public class WeightActivity extends AppCompatActivity {

    private static final String TAG = "WeightActivity";
    private Context context;
    private ProgressBar weightProgressBar;
    private TextView weightText;
    private BluetoothSocket mSocket;
    private BluetoothDevice mDevice;
    private OutputStream mOutputStream;
    private InputStream mInputStream;

    Thread workerThread;
    byte[] readBuffer;
    int readBufferPosition;
    int counter;
    volatile boolean stopWorker;

    // Standard SerialPortService ID
    // this uuid identifies the type of service in the target device
    private UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weight);

        context = WeightActivity.this;

        String scaleAddress = getIntent().getStringExtra("EXTRA_DEVICE_ADDRESS");
        Log.v(TAG, "device to connect is " + scaleAddress);
    }


    private void connect2scale() throws IOException
    {
        mSocket = mDevice.createRfcommSocketToServiceRecord(uuid);
        mSocket.connect();
        mOutputStream = mSocket.getOutputStream();
        mInputStream = mSocket.getInputStream();
    }


    void beginListenForData()
    {
        final Gson gson = new Gson();
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
                        int bytesAvailable = mInputStream.available();
                        if(bytesAvailable > 0)
                        {
                            byte[] packetBytes = new byte[bytesAvailable];
                            mInputStream.read(packetBytes);
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
                                            // Log.v(TAG, data.toString());
                                            try {
                                                ScaleData scaleData = gson.fromJson(data, ScaleData.class);

                                                weightText.setText(String.format("%sg", String.format("%.2f", scaleData.weight)));
                                                weightProgressBar.setProgress((int) scaleData.weight);
                                            }
                                            catch (Exception e) {}
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
