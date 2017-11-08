package com.example.msr.hengyu.fw_downloader;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hy.HY_RFID;

public class MainActivity extends AppCompatActivity {
    private UsbManager manager = null;
    private UsbDevice device = null;
    private PendingIntent mPermissionIntent = null;
    private static final String ACTION_USB_PERMISSION = "com.example.msr.usbhost.USB_PERMISSION";

    static private HY_RFID hyRFID_Reader = null;
    private MyHandler handler = new MyHandler(this);
    private static byte[] byteCmdBuf = new byte[256];

    private ListView mListView;
    private TextView mTextView, tvSelectedFile;
    private File mCurrentParent;
    File[] mCurrentFiles;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mListView = (ListView) findViewById(R.id.list);
        mTextView = (TextView) findViewById(R.id.path);
        tvSelectedFile = (TextView) findViewById(R.id.pathSelectedFile);
        //获取系统的SD卡的目录
        File root = new File(String.valueOf(Environment.getExternalStorageDirectory()));
        //如果SD卡存在
        if (root.exists()){
            mCurrentParent = root;
            mCurrentFiles = root.listFiles();
            //使用当前目录下的全部文件、文件夹来填充ListView
            inflateListView(mCurrentFiles);
        }

        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (mCurrentFiles[position].isFile()){
                    tvSelectedFile.setText("已选择文件:" + mCurrentFiles[position].getAbsolutePath());

                    return;
                }

                tvSelectedFile.setText("");

                File[] tmp = mCurrentFiles[position].listFiles();
                if (tmp == null || tmp.length == 0){
                    Toast.makeText(MainActivity.this, "当前路径不可访问或该路径下没有文件", Toast.LENGTH_SHORT).show();
                }else {
                    mCurrentParent = mCurrentFiles[position];
                    mCurrentFiles = tmp;
                    inflateListView(mCurrentFiles);
                }
            }
        });
        //获取上一级目录的按钮
        Button parent = (Button) findViewById(R.id.parent);
        parent.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                tvSelectedFile.setText("");

                try {
                    if (!mCurrentParent.getCanonicalFile().equals("/mnt/shell/emulated/0")){
                        mCurrentParent = mCurrentParent.getParentFile();
                        mCurrentFiles = mCurrentParent.listFiles();
                        inflateListView(mCurrentFiles);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });


        //++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        //USB Init
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        /*
         * this block required if you need to communicate to USB devices it's
         * take permission to device
         * if you want than you can set this to which device you want to communicate
         */
        //------------------------------------------------------------------
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
                ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        MainActivity.this.registerReceiver(mUsbReceiver, filter);
        //-------------------------------------------------------------------
        //+++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

        hyRFID_Reader = new HY_RFID(handler, manager, device, mPermissionIntent, byteCmdBuf);
    }

    private void inflateListView(File[] files) {
        //创建一个List集合，List集合的元素是Map
        List<Map<String, Object>> listItems = new ArrayList<>();
        for (int i = 0; i < files.length; i++) {
            Map<String, Object> listItem = new HashMap<>();
            if (files[i].isDirectory()){
                listItem.put("icon", R.drawable.folder);
            }else {
                listItem.put("icon", R.drawable.file);
            }
            listItem.put("fileName", files[i].getName());
            listItems.add(listItem);
        }
        //创建一个SimpleAdapter
        SimpleAdapter simpleAdapter = new SimpleAdapter(this, listItems, R.layout.line, new String[]{"icon", "fileName"}, new int[]{R.id.icon, R.id.file_name});
        mListView.setAdapter(simpleAdapter);
        try {
            mTextView.setText("当前路径为：" + mCurrentParent.getCanonicalPath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class MyHandler extends Handler {
        private final byte CMD_FIRMWARE_VERSION = (byte)0xCA;
        private final byte MSR_SET_CONFIGURATION_COMMAND = (byte)0x4A;
        private final byte MSR_READ_CONFIGURATION_COMMAND = (byte)0x4B;
        private final byte MSR_PROGRAM_KEY_COMMAND = (byte)0x4C;
        private final byte MSR_READ_KEY_COMMAND = (byte)0x4D;

        private final byte HY_STATUS_SUCCESS = 0x00;
        private final byte HY_STATUS_READ_FAIL = 0x23;
        private final byte HY_STATUS_WRITE_FAIL = 0x24;
        private final byte HY_CHECK_DEVICE_STATUS =	(byte)0xFE;
        private final byte HY_STATUS_FAIL = (byte)0xFF;

        private final WeakReference<MainActivity> myActivity;

        public MyHandler(MainActivity activity){
            myActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg){
            MainActivity active = myActivity.get();
            if ( active != null ){
                switch(msg.what){
                    case CMD_FIRMWARE_VERSION:
                        printResult("Firmware version", new String(Arrays.copyOfRange(byteCmdBuf, 4, 12)), byteCmdBuf[3] );
                        break;
                    case HY_STATUS_FAIL:
                        Toast.makeText(active, "Please check connection.", Toast.LENGTH_LONG).show();
                        break;
                    case HY_CHECK_DEVICE_STATUS:

                        break;
                    default:
                        Log.d("MyDebug", "!!!" + String.format("0x%X",msg.what));
                        break;
                }

                super.handleMessage(msg);
            }
        }

        private void printResult(String strTitle, String strMessage, byte byteResult){
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(myActivity.get());

            alertDialog.setTitle(strTitle);

            switch(byteResult){
                case HY_STATUS_SUCCESS:
                    alertDialog.setMessage(strMessage);
                    break;
                case HY_STATUS_WRITE_FAIL:
                    alertDialog.setMessage("Write Fail");
                    break;
                case HY_STATUS_READ_FAIL:
                    alertDialog.setMessage("Read Fail");
                    break;
                default:
                    alertDialog.setMessage("Fail!");
                    break;
            }

            alertDialog.setPositiveButton("OK", null);
            alertDialog.show();
        }
    }

    public void buttonGetVersionOnClick(View view){
        hyRFID_Reader.check_firmware_version();
    }

    public void buttonCheckDeviceOnClick(View view){
        int device_id = hyRFID_Reader.check_Device();

        if(device_id == HY_RFID.HY_DEVICE_NONE)
            Toast.makeText(MainActivity.this, "Can't find any HengYu devices.", Toast.LENGTH_LONG).show();
        else if(device_id == HY_RFID.HY_DEVICE_C210A)
            Toast.makeText(MainActivity.this, "C210A", Toast.LENGTH_LONG).show();
        if(device_id == HY_RFID.HY_DEVICE_C215A)
            Toast.makeText(MainActivity.this, "C215A", Toast.LENGTH_LONG).show();
    }

    public void buttonDownloadOnClick(View view){
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);

        String str = tvSelectedFile.getText().toString();
        String strFilePath = null;
        if(str.indexOf(":") > 0){
            strFilePath = str.split(":")[1];
        }else
            strFilePath = "";

        File fileFW = new File(strFilePath);

        if(!fileFW.exists()){
            alertDialog.setMessage("Can't open the file!");
            alertDialog.setPositiveButton("OK", null);
            alertDialog.show();
        }

        
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            //UsbDevice usbDevice = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);


            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    if (intent.getBooleanExtra(
                            UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            // call method to set up device communication

                        }
                    } else {
                        Log.i("ERROR", "hy permission denied for device " + device);
                    }
                }
            }

            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                // Device removed
                synchronized (this) {
                    // ... Check to see if usbDevice is yours and cleanup ...

                }
            }

            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                // Device attached
                synchronized (this) {
                    // Qualify the new device to suit your needs and request permission
                    if ((device.getVendorId() == 0x0F39) && (device.getProductId() == 0x2101)) { // 0x2051
                        manager.requestPermission(device, mPermissionIntent);
                    }
                }
            }
        }
    };
}


