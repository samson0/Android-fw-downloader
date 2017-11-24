package com.example.msr.hengyu.fw_downloader;

import android.app.PendingIntent;
import android.app.ProgressDialog;
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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
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

    private static byte[] file_buf = null;

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
        private final byte CMD_FIRMWARE_VERSION_ISP = (byte)0xC0;
        private final byte CMD_CHECK_PROFILE = (byte)0xCE;
        private final byte CMD_ISP_CHECK_PROFILE = (byte)0xBF;
        private final byte CMD_ENTER_ISP = (byte)0xBA;
        private final byte CMD_CHIP_ERASE_ISP = (byte)0xBE;
        private final byte CMD_FLASH_WRITE_ISP = (byte)0xBB;
        private final byte CMD_CHIP_PROTECT_ISP = (byte)0xC2;
        private final byte CMD_RESET_ISP = (byte)0xC9;

        private final byte HY_STATUS_SUCCESS = 0x00;
        private final byte HY_STATUS_INCORRECT_PROFILE = 0x18;
        private final byte HY_STATUS_READ_FAIL = 0x23;
        private final byte HY_STATUS_WRITE_FAIL = 0x24;
        private final byte HY_CHECK_DEVICE_STATUS =	(byte)0xFE;
        private final byte HY_STATUS_FAIL = (byte)0xFF;

        private final WeakReference<MainActivity> myActivity;

        private ProgressDialog progress = null;

        private int file_header = 0;

        public MyHandler(MainActivity activity){
            myActivity = new WeakReference<MainActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg){
            MainActivity active = myActivity.get();
            if ( active != null ){
                switch(msg.what){
                    case CMD_FIRMWARE_VERSION:
                        //Toast.makeText(myActivity.get(), String.format("%02X %02X %02X", byteCmdBuf[3], byteCmdBuf[4], byteCmdBuf[5]), Toast.LENGTH_LONG).show();
                        printResult("Firmware version", new String(Arrays.copyOfRange(byteCmdBuf, 4, 12)), byteCmdBuf[3] );
                        break;
                    case CMD_FIRMWARE_VERSION_ISP:
                        printResult("Bootloader firmware version", new String(Arrays.copyOfRange(byteCmdBuf, 4, 12)), byteCmdBuf[3] );
                        break;
                    case CMD_CHECK_PROFILE:
                        if(byteCmdBuf[3] != HY_STATUS_SUCCESS){
                            printResult("Check profile", "INCORRECT PROFILE", byteCmdBuf[3]);
                        }else {
                            //Enter ISP
                            hyRFID_Reader.ISP_EnterISP();
                        }
                        break;
                    case CMD_ENTER_ISP:
                        if(byteCmdBuf[3] != HY_STATUS_SUCCESS){
                            printResult("Enter ISP", "Fail to enter ISP mode.", byteCmdBuf[3]);
                        }else{
                            new Handler().postDelayed(new Runnable(){
                                public void run() {
                                    hyRFID_Reader.ISP_CheckProfile(Arrays.copyOfRange(file_buf, file_buf.length - 14, file_buf.length - 4));
                                }
                            }, 4000);
                        }
                        break;
                    case CMD_ISP_CHECK_PROFILE:
                        if(byteCmdBuf[3] != HY_STATUS_SUCCESS){
                            printResult("ISP Check Profile", "Fail to check profile(ISP)", byteCmdBuf[3]);
                        }else{
                            hyRFID_Reader.ISP_ChipErase();
                        }
                        break;
                    case CMD_CHIP_ERASE_ISP:

                        if(byteCmdBuf[3] != HY_STATUS_SUCCESS){
                            printResult("Chip erase", "Chip erase fail", byteCmdBuf[3]);
                        }else{
                            progress = new ProgressDialog(active);
                            progress.setMessage("Downloading Firmware");
                            progress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                            progress.setProgress(0);
                            progress.setMax(100);
                            progress.show();

                            file_header = 0;

                            hyRFID_Reader.ISP_WriteFlash(file_header, Arrays.copyOfRange(file_buf, file_header, file_header + 64));
                            file_header += 64;
                        }
                        break;
                    case CMD_FLASH_WRITE_ISP:

                        if(byteCmdBuf[3] != HY_STATUS_SUCCESS){
                            printResult("Write flash", String.format("Write flash fail, %04X", file_header), byteCmdBuf[3]);
                        }else{
                            if(file_header < file_buf.length) {
                                progress.setProgress((file_header*100)/file_buf.length);

                                try {
                                    hyRFID_Reader.ISP_WriteFlash(file_header, Arrays.copyOfRange(file_buf, file_header, file_header + 64));
                                }catch(ArrayIndexOutOfBoundsException e){
                                    Toast.makeText(active, "ArrayIndexOutOfBoundsException", Toast.LENGTH_SHORT).show();
                                }

                                file_header += 64;
                            }else{
                                progress.setProgress(100);

                                hyRFID_Reader.ISP_ChipProtect();
                            }
                        }
                        break;
                    case CMD_CHIP_PROTECT_ISP:
                        if(byteCmdBuf[3] != HY_STATUS_SUCCESS){
                            printResult("Chip protect", "Chip protect fail", byteCmdBuf[3]);
                        }else{
                            hyRFID_Reader.ISP_ChipReset();
                        }
                        break;
                    case CMD_RESET_ISP:
                        if(byteCmdBuf[3] != HY_STATUS_SUCCESS){
                            printResult("Chip protect", "Chip protect fail", byteCmdBuf[3]);
                        }else
                            printResult("Firmware download", "Firmware download finished successfully.", byteCmdBuf[3]);
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
                case HY_STATUS_SUCCESS:case HY_STATUS_INCORRECT_PROFILE:
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
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MainActivity.this);

        int device_id = hyRFID_Reader.check_Device();

        if(device_id == HY_RFID.HY_DEVICE_NONE)
            alertDialog.setMessage("Can't find any devices.");
        else if(device_id == HY_RFID.HY_DEVICE_C210A)
            alertDialog.setMessage("C210A");
        else if(device_id == HY_RFID.HY_DEVICE_C215A)
            alertDialog.setMessage("C215A");
        else if(device_id == HY_RFID.HY_DEVICE_ISP)
            alertDialog.setMessage("Device works in ISP mode");

        alertDialog.setPositiveButton("OK", null);
        alertDialog.show();
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

        //Check firmware file
        boolean checkFlag = false;
        try{

            if(!fileFW.exists()){
                alertDialog.setMessage("Please select the firmware file!");
                alertDialog.setPositiveButton("OK", null);
                alertDialog.show();
            }

            file_buf = new byte[(int)fileFW.length()];

            BufferedInputStream in = new BufferedInputStream(new FileInputStream(fileFW));
            if( in.read(file_buf, 0, file_buf.length) == file_buf.length){

            }else{
                Toast.makeText(MainActivity.this, "Read file error!", Toast.LENGTH_SHORT).show();
                checkFlag = true;
            }

            in.close();
        }catch(FileNotFoundException fx){
            fx.printStackTrace();
            checkFlag = true;
        }catch(IOException ex){
            ex.printStackTrace();
            checkFlag = true;
        }

        if(checkFlag)
            return;

        if(file_buf.length < 4*1024) {
            Toast.makeText(MainActivity.this, "Wrong firmware file.", Toast.LENGTH_LONG).show();
            return;
        }

        // Check signature
        if((file_buf[file_buf.length - 16] != (byte)0x7B) && (file_buf[file_buf.length - 15] != 0x6A)){
            Toast.makeText(MainActivity.this, "Wrong firmware file.", Toast.LENGTH_LONG).show();

            return;
        }



        if(hyRFID_Reader.check_Device() != hyRFID_Reader.HY_DEVICE_ISP) {
            // Check Profile (non GLOBAL FULL)
            if ((file_buf[file_buf.length - 14] != (byte) 0x8B) || (file_buf[file_buf.length - 13] != (byte) 0x9B)) {
                if ((HY_Encrypt(file_buf[file_buf.length - 14]) != 'G' && HY_Encrypt(file_buf[file_buf.length - 14]) != 'C') || (HY_Encrypt(file_buf[file_buf.length - 13]) != 'L')) {
                    Toast.makeText(MainActivity.this, "Wrong file (check profile)", Toast.LENGTH_SHORT).show();
                    return;
                }

                hyRFID_Reader.CheckProfile(Arrays.copyOfRange(file_buf, file_buf.length - 14, file_buf.length - 12));
            } else {
                //Enter ISP
                hyRFID_Reader.ISP_EnterISP();
            }
        }else{
            hyRFID_Reader.ISP_CheckProfile(Arrays.copyOfRange(file_buf, file_buf.length - 14, file_buf.length - 4));
        }
    }

    private byte HY_Encrypt(byte data) {
        return (byte)(~((data << 4) | (data >> 4)));
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


