package com.example.msr.hengyu.msr_config;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.StringBuilderPrinter;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import hy.HY_RFID;

public class MainActivity extends AppCompatActivity {

    private UsbManager manager = null;
    private UsbDevice device = null;
    private PendingIntent mPermissionIntent = null;
    private static final String ACTION_USB_PERMISSION = "com.example.msr.usbhost.USB_PERMISSION";

    static private HY_RFID hyRFID_Reader = null;
    private MyHandler handler = new MyHandler(this);
    private static byte[] byteCmdBuf = new byte[256];

    static private EditText etStartSentinelT1 = null, etStartSentinelT2 = null, etStartSentinelT3 = null;
    static private EditText etEndSentinelT1 = null, etEndSentinelT2 = null, etEndSentinelT3 = null;

    static private CheckBox cb_CR_T1 = null, cb_CR_T2 = null, cb_CR_T3 = null;

    private static Spinner spinnerTrackSequence = null;

    private static CheckBox cbBuzzer = null, cbT1 = null, cbT2 = null, cbT3 = null;

    private static int check_enable = 0x87;// Default: Track 1 ~ 3 enable, buzzer enable;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*set it to be no title*/
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        /*set it to be full screen*/
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        cbBuzzer = (CheckBox)findViewById(R.id.checkbox_buzzer);
        cbT1 = (CheckBox)findViewById(R.id.checkbox_enable_T1);
        cbT2 = (CheckBox)findViewById(R.id.checkbox_enable_T2);
        cbT3 = (CheckBox)findViewById(R.id.checkbox_enable_T3);
        cb_CR_T1 = (CheckBox)findViewById(R.id.checkbox_CR_T1);
        cb_CR_T2 = (CheckBox)findViewById(R.id.checkbox_CR_T2);
        cb_CR_T3 = (CheckBox)findViewById(R.id.checkbox_CR_T3);

        etStartSentinelT1 = (EditText)findViewById(R.id.et_start_sentinel_T1);
        etStartSentinelT2 = (EditText)findViewById(R.id.et_start_sentinel_T2);
        etStartSentinelT3 = (EditText)findViewById(R.id.et_start_sentinel_T3);

        etEndSentinelT1 = (EditText)findViewById(R.id.et_end_sentinel_T1);
        etEndSentinelT2 = (EditText)findViewById(R.id.et_end_sentinel_T2);
        etEndSentinelT3 = (EditText)findViewById(R.id.et_end_sentinel_T3);

        ArrayAdapter<CharSequence> adapterSpinnerInitSector = ArrayAdapter.createFromResource(this,
                R.array.track_sequence,
                R.layout.spinner_item);
        adapterSpinnerInitSector.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerTrackSequence = (Spinner) findViewById(R.id.sp_track_sequence);
        spinnerTrackSequence.setAdapter(adapterSpinnerInitSector);
        spinnerTrackSequence.setOnItemSelectedListener(new Spinner.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                spinnerTrackSequence.getSelectedItem().toString();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        //spinnerTrackSequence.setPrompt("abc");


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

        //if(hyRFID_Reader.check_firmware_version()){
        //    Log.d("MyDebug", "Get firmware version");
        //}

        /*
        manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String , UsbDevice> deviceList = manager.getDeviceList();

        if(deviceList != null) {
            Log.d("MyDebug", "deviceList != NULL");

            if (deviceList.size() == 0) {
                Log.d("MyDebug", "Device NULL");
            }

            Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
            while (deviceIterator.hasNext()) {
                device = deviceIterator.next();
                Log.d("MyDebug", String.format("Device.VID=0x%04X", device.getVendorId()));
                Log.d("MyDebug", String.format("Device.PID=0x%04X", device.getProductId()));
                if ((device.getVendorId() == 0x0F39) && (device.getProductId() == 0x2101)) {
                    manager.requestPermission(device, mPermissionIntent);

                    break;
                }
            }

        }*/
    }


    public void onCheckboxClicked(View view) {
        // Is the view now checked?
        boolean checked = ((CheckBox) view).isChecked();

        // Check which checkbox was clicked
        switch(view.getId()) {
            case R.id.checkbox_enable_T1:
                if (checked) {
                    check_enable |= 0x01;

                    //etStartSentinelT1.setHint("%");
                    //etEndSentinelT1.setHint("?");
                    etStartSentinelT1.setEnabled(true);
                    etEndSentinelT1.setEnabled(true);

                    cb_CR_T1.setEnabled(true);
                }else {
                    check_enable &= (~0x01);

                    etStartSentinelT1.setText("");
                    //etStartSentinelT1.setHint("");
                    etEndSentinelT1.setText("");
                    //etEndSentinelT1.setHint("");
                    etStartSentinelT1.setEnabled(false);
                    etEndSentinelT1.setEnabled(false);

                    cb_CR_T1.setChecked(false);
                    cb_CR_T1.setEnabled(false);
                }
                break;
            case R.id.checkbox_enable_T2:
                if (checked) {
                    check_enable |= 0x02;

                    //etStartSentinelT2.setHint(";");
                    //etEndSentinelT2.setHint("?");
                    etStartSentinelT2.setEnabled(true);
                    etEndSentinelT2.setEnabled(true);

                    cb_CR_T2.setEnabled(true);
                }else {
                    check_enable &= (~0x02);

                    etStartSentinelT2.setText("");
                    etEndSentinelT2.setText("");
                    //etStartSentinelT2.setHint("");
                    //etEndSentinelT2.setHint("");
                    etStartSentinelT2.setEnabled(false);
                    etEndSentinelT2.setEnabled(false);

                    cb_CR_T2.setChecked(false);
                    cb_CR_T2.setEnabled(false);
                }
                break;
            case R.id.checkbox_enable_T3:
                if (checked) {
                    check_enable |= 0x04;

                    //etStartSentinelT3.setHint(";");
                    //etEndSentinelT3.setHint("?");
                    etStartSentinelT3.setEnabled(true);
                    etEndSentinelT3.setEnabled(true);

                    cb_CR_T3.setEnabled(true);
                }else {
                    check_enable &= (~0x04);

                    etStartSentinelT3.setText("");
                    etEndSentinelT3.setText("");
                    //etStartSentinelT3.setHint("");
                    //etEndSentinelT3.setHint("");
                    etStartSentinelT3.setEnabled(false);
                    etEndSentinelT3.setEnabled(false);

                    cb_CR_T3.setChecked(false);
                    cb_CR_T3.setEnabled(false);
                }
                break;
            case R.id.checkbox_buzzer:
                if(checked)
                    check_enable |= 0x80;
                else
                    check_enable &= (~0x80);
                break;
        }
    }

    public void buttonUpdateOnClick(View v){
        byte[] send_dat = new byte[9];

        Arrays.fill(send_dat, (byte)0x00);

        //Log.d("MyDebug", "check_enable=" + String.format("0x%X", check_enable));

        if(!hyRFID_Reader.MSR_setConfig((byte)check_enable, (String)spinnerTrackSequence.getSelectedItem()))
            return;
    }

    static private void setMsrKey(int step){
        StringBuilder strT = new StringBuilder("");

        switch(step){
            case 0:
                /*if(etStartSentinelT1.getText().toString().equals("")){
                    strT.append("%");
                }else */
                strT.append(etStartSentinelT1.getText());
                break;
            case 1:
                /*if(etEndSentinelT1.getText().toString().equals("")){
                    strT.append("?");
                }else*/
                strT.append(etEndSentinelT1.getText());
                if(cb_CR_T1.isChecked())
                    strT.append(String.format("\n"));
                break;
            case 2:
                /*if(etStartSentinelT2.getText().toString().equals("")){
                    strT.append(";");
                }else */
                strT.append(etStartSentinelT2.getText());
                break;
            case 3:
                /*if(etEndSentinelT2.getText().toString().equals("")){
                    strT.append("?");
                }else*/
                strT.append(etEndSentinelT2.getText());
                if(cb_CR_T2.isChecked())
                    strT.append(String.format("\n"));
                break;
            case 4:
                /*if(etStartSentinelT3.getText().toString().equals("")){
                    strT.append(";");
                }else */
                strT.append(etStartSentinelT3.getText());
                break;
            case 5:
                /*if(etEndSentinelT3.getText().toString().equals("")){
                    strT.append("?");
                }else*/
                strT.append(etEndSentinelT3.getText());
                if(cb_CR_T3.isChecked())
                    strT.append(String.format("\n"));
                break;
        }

        hyRFID_Reader.MSR_programKey(step, strT.toString());
    }

    public void buttonGetOnClick(View v){
        hyRFID_Reader.MSR_getConfig();
    }


    public void buttonGetFwVerOnClick(View v){

        hyRFID_Reader.check_firmware_version();
    }

    private static int msrProgramKeyCnt = 0;
    private static int readProgramKeyFlag = 0;

    private static class MyHandler extends Handler {
        private final byte CMD_FIRMWARE_VERSION = (byte)0xCA;
        private final byte MSR_SET_CONFIGURATION_COMMAND = (byte)0x4A;
        private final byte MSR_READ_CONFIGURATION_COMMAND = (byte)0x4B;
        private final byte MSR_PROGRAM_KEY_COMMAND = (byte)0x4C;
        private final byte MSR_READ_KEY_COMMAND = (byte)0x4D;

        private final byte HY_STATUS_SUCCESS = 0x00;
        private final byte HY_STATUS_READ_FAIL = 0x23;
        private final byte HY_STATUS_WRITE_FAIL = 0x24;
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
                    case MSR_SET_CONFIGURATION_COMMAND:
                        //Log.d("MyDebug", "#B");
                        if(byteCmdBuf[3] != HY_STATUS_SUCCESS){
                            printResult("Set Magstripe Configuration", "", byteCmdBuf[3] );
                        }else {
                            msrProgramKeyCnt = 0;
                            setMsrKey(msrProgramKeyCnt++);
                        }
                        break;
                    case MSR_PROGRAM_KEY_COMMAND:
                        //Log.d("MyDebug", "#C#" + msrProgramKeyCnt);
                        if(byteCmdBuf[3] != HY_STATUS_SUCCESS){
                            printResult("Set Magstripe Key", "", byteCmdBuf[3]);
                        }else{
                            if(msrProgramKeyCnt == 6){
                                msrProgramKeyCnt = 0;
                                printResult("Set Magstripe Key", "OK!", byteCmdBuf[3]);
                            }else
                                setMsrKey(msrProgramKeyCnt++);
                        }
                        break;
                    case MSR_READ_CONFIGURATION_COMMAND:
                        if(byteCmdBuf[3] != HY_STATUS_SUCCESS){
                            printResult("Get Magstripe Configuration", "", byteCmdBuf[3] );
                        }else {
                            etStartSentinelT1.setText("");
                            etEndSentinelT1.setText("");
                            etStartSentinelT2.setText("");
                            etEndSentinelT2.setText("");
                            etStartSentinelT3.setText("");
                            etEndSentinelT3.setText("");

                            readProgramKeyFlag = 0;

                            if(byteCmdBuf[5] > 0){
                                etStartSentinelT1.setText(Byte.toString(byteCmdBuf[5]));
                            }else
                                readProgramKeyFlag |= 0x01;

                            if(byteCmdBuf[6] > 0){
                                etStartSentinelT2.setText(Byte.toString(byteCmdBuf[6]));
                            }else
                                readProgramKeyFlag |= 0x04;

                            if(byteCmdBuf[7] > 0){
                                etStartSentinelT3.setText(Byte.toString(byteCmdBuf[7]));
                            }else
                                readProgramKeyFlag |= 0x10;

                            if(byteCmdBuf[8] > 0){
                                etEndSentinelT1.setText(Byte.toString(byteCmdBuf[8]));
                            }else
                                readProgramKeyFlag |= 0x02;

                            if(byteCmdBuf[9] > 0){
                                etEndSentinelT2.setText(Byte.toString(byteCmdBuf[9]));
                            }else
                                readProgramKeyFlag |= 0x08;

                            if(byteCmdBuf[10] > 0){
                                etEndSentinelT3.setText(Byte.toString(byteCmdBuf[10]));
                            }else
                                readProgramKeyFlag |= 0x20;

                            switch(byteCmdBuf[11]){
                                case 0x39://Byte.parseByte("00111001", 2)
                                    spinnerTrackSequence.setSelection(0);
                                    break;
                                case 0x2D://Byte.parseByte("00101101", 2)
                                    spinnerTrackSequence.setSelection(1);
                                    break;
                                case 0x36://Byte.parseByte("00110110", 2)
                                    spinnerTrackSequence.setSelection(2);
                                    break;
                                case 0x1E://Byte.parseByte("00011110", 2)
                                    spinnerTrackSequence.setSelection(3);
                                    break;
                                case 0x27://Byte.parseByte("00100111", 2)
                                    spinnerTrackSequence.setSelection(4);
                                    break;
                                case 0x1B://Byte.parseByte("00011011", 2)
                                    spinnerTrackSequence.setSelection(5);
                                    break;
                            }

                            check_enable = byteCmdBuf[4];

                            //Log.d("MyDebug", "check_enable = " + String.format("0x%02X", check_enable));

                            if((check_enable & 0x01) > 0){
                                cbT1.setChecked(true);
                                etStartSentinelT1.setEnabled(true);
                                etEndSentinelT1.setEnabled(true);

                                cb_CR_T1.setEnabled(true);
                            }else {
                                cbT1.setChecked(false);
                                etStartSentinelT1.setEnabled(false);
                                etEndSentinelT1.setEnabled(false);

                                cb_CR_T1.setChecked(false);
                                cb_CR_T1.setEnabled(false);

                                readProgramKeyFlag &= ~(0x03);
                            }

                            if((check_enable & 0x02) > 0){
                                cbT2.setChecked(true);
                                etStartSentinelT2.setEnabled(true);
                                etEndSentinelT2.setEnabled(true);

                                cb_CR_T2.setEnabled(true);
                            }else {
                                cbT2.setChecked(false);
                                etStartSentinelT2.setEnabled(false);
                                etEndSentinelT2.setEnabled(false);

                                cb_CR_T2.setChecked(false);
                                cb_CR_T2.setEnabled(false);

                                readProgramKeyFlag &= ~(0x0C);
                            }

                            if((check_enable & 0x04) > 0){
                                cbT3.setChecked(true);
                                etStartSentinelT3.setEnabled(true);
                                etEndSentinelT3.setEnabled(true);

                                cb_CR_T3.setEnabled(true);
                            }else {
                                cbT3.setChecked(false);
                                etStartSentinelT3.setEnabled(false);
                                etEndSentinelT3.setEnabled(false);

                                cb_CR_T3.setChecked(false);
                                cb_CR_T3.setEnabled(false);

                                readProgramKeyFlag &= ~(0x30);
                            }

                            if((check_enable & 0x80) > 0){
                                cbBuzzer.setChecked(true);
                            }else
                                cbBuzzer.setChecked(false);

                            // Read Program Magstripe Key
                            for(int i = 0; i < 6; i++){
                                if(((readProgramKeyFlag >> i)&0x01) > 0){
                                    hyRFID_Reader.MSR_readProgramKey(0x03 + i);

                                    //Log.d("MyDebug", "A: i = " + i);

                                    break;
                                }
                            }
                        }

                        break;
                    case MSR_READ_KEY_COMMAND:
                        if(byteCmdBuf[3] != HY_STATUS_SUCCESS){
                            printResult("Read Magstripe Key", "", byteCmdBuf[3] );
                        }else {
                            for(int i = 0; i < 6; i++){
                                if(((readProgramKeyFlag >> i)&0x01) > 0){
                                    //Log.d("MyDebug", "B: i = " + i);
                                    //for(int a = 0; a < byteCmdBuf[1] + 2; a++){
                                    //    Log.d("MyDebug", String.format("# %02X ",byteCmdBuf[a]));
                                    //}

                                    switch (i){
                                        case 0:
                                            etStartSentinelT1.setText(new String(Arrays.copyOfRange(byteCmdBuf, 5, 5 + (byteCmdBuf[1] - 4))));
                                            break;
                                        case 1:
                                            if((byteCmdBuf[1] - 4) > 0){
                                                if(byteCmdBuf[5 + (byteCmdBuf[1] - 4) - 1] == '\n'){
                                                    etEndSentinelT1.setText(new String(Arrays.copyOfRange(byteCmdBuf, 5, 5 + (byteCmdBuf[1] - 4) - 1)));
                                                    cb_CR_T1.setChecked(true);
                                                }else {
                                                    etEndSentinelT1.setText(new String(Arrays.copyOfRange(byteCmdBuf, 5, 5 + (byteCmdBuf[1] - 4))));
                                                    cb_CR_T1.setChecked(false);
                                                }
                                            }
                                            break;
                                        case 2:
                                            etStartSentinelT2.setText(new String(Arrays.copyOfRange(byteCmdBuf, 5, 5 + (byteCmdBuf[1] - 4))));
                                            break;
                                        case 3:
                                            if((byteCmdBuf[1] - 4) > 0){
                                                if(byteCmdBuf[5 + (byteCmdBuf[1] - 4) - 1] == '\n'){
                                                    etEndSentinelT2.setText(new String(Arrays.copyOfRange(byteCmdBuf, 5, 5 + (byteCmdBuf[1] - 4) - 1)));
                                                    cb_CR_T2.setChecked(true);
                                                }else {
                                                    etEndSentinelT2.setText(new String(Arrays.copyOfRange(byteCmdBuf, 5, 5 + (byteCmdBuf[1] - 4))));
                                                    cb_CR_T2.setChecked(false);
                                                }
                                            }
                                            break;
                                        case 4:
                                            etStartSentinelT3.setText(new String(Arrays.copyOfRange(byteCmdBuf, 5, 5 + (byteCmdBuf[1] - 4))));
                                            break;
                                        case 5:
                                            if((byteCmdBuf[1] - 4) > 0){
                                                if(byteCmdBuf[5 + (byteCmdBuf[1] - 4) - 1] == '\n'){
                                                    etEndSentinelT3.setText(new String(Arrays.copyOfRange(byteCmdBuf, 5, 5 + (byteCmdBuf[1] - 4) - 1)));
                                                    cb_CR_T3.setChecked(true);
                                                }else {
                                                    etEndSentinelT3.setText(new String(Arrays.copyOfRange(byteCmdBuf, 5, 5 + (byteCmdBuf[1] - 4))));
                                                    cb_CR_T3.setChecked(false);
                                                }
                                            }
                                            break;
                                    }
                                    readProgramKeyFlag &= ~(0x01<<i);
                                    for(int j = i + 1; j < 6; j++){
                                        if(((readProgramKeyFlag >> j)&0x01) > 0){
                                            hyRFID_Reader.MSR_readProgramKey(0x03 + j);
                                            //Log.d("MyDebug", "B: j = " + j);
                                            break;
                                        }
                                    }

                                    break;
                                }
                            }
                        }
                        break;
                    case HY_STATUS_FAIL:
                        Toast.makeText(active, "Please check connection.", Toast.LENGTH_LONG).show();
                        break;
                    default:
                        Log.d("MyDebug", "!!!" + String.format("0x%X",msg.what));
                        break;
                }

                super.handleMessage(msg);
            }
        }

        /*
         * @param
         * start_index,start index in the byte array,including
         *
         */
        private String bytesToHexstring(byte[] in, int start_index, int length){
            final StringBuilder builder = new StringBuilder();

            for ( int i = start_index; i < start_index + length; i++){
                builder.append(String.format("0x%02X ", in[i]));
                //Log.i(DEBUG_TAG, "K" + Integer.toString(i) + ":" + String.format("0x%02X ", in[i]));
            }

            return builder.toString();
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