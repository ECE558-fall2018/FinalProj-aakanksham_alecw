package alec.androidthingsece558;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Gpio;

import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import java.util.TimeZone;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;


public class HomeActivity extends Activity {
    private static final String TAG = "HomeActivity";
    //I2C Device Name
    private static final String I2C_DEVICE_NAME = "I2C1";

    //Delay time for runnables
    private static final int INTERVAL_BETWEEN_BLINKS_MS = 1000;

    // I2C Slave Address
    private static final int I2C_ADDRESS = 0x08;
    private I2cDevice mDevice;

    // GPIO port wired to the LED
    private static final String LED_PIN_NAME = "BCM4";
    private Gpio mLedGpio;

    //Set up byte adresses for each register
    private final byte DAC1_ADDRESS     = 0x04;

    private final byte ADC3_ADDRESS_MSB = 0x08;
    private final byte ADC3_ADDRESS_LSB = 0x07;

    private final byte ADC4_ADDRESS_MSB = 0x0a;
    private final byte ADC4_ADDRESS_LSB = 0x09;

    private final byte ADC5_ADDRESS_MSB = 0x0c;
    private final byte ADC5_ADDRESS_LSB = 0x0b;

    private final byte ADA5_ADDRESS_MSB = 0x06;
    private final byte ADA5_ADDRESS_LSB = 0x05;

    //PWM 3 is the blue LED
    final byte addressLEDBlue   = 0x02;
    //PWM 4 is the green LED
    final byte addressLEDGreen  = 0x01;
    //PWM 5 is the red LED
    final byte addressLEDRed    = 0x00;
    //PWM 6 is the motor
    final byte addressMotor     = 0x03;

    //Set up value key names from firebase server
    private final String PWM_BLUE_LED = "PWM_BLUE_LED";
    private final String PWM_GREEN_LED = "PWM_GREEN_LED";
    private final String PWM_RED_LED = "PWM_RED_LED";
    private final String PWM_MOTOR = "PWM_MOTOR";

    private Handler mHandler = new Handler();

    private DatabaseReference mDatabase;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Starting HomeActivity");

        setContentView(R.layout.activity_home);

        //Set up manager for I/O
        PeripheralManager manager = PeripheralManager.getInstance();

        Log.d(TAG, "Available GPIO: " + manager.getGpioList());
        //GPIO/LED blink code from https://developer.android.com/things/training/first-device/peripherals
        // Step 1. Create GPIO connection.
        //PeripheralManager manager = PeripheralManager.getInstance();
        try {
            mLedGpio = manager.openGpio(LED_PIN_NAME);

            // Step 2. Configure as an output.
            mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

            // Step 4. Repeat using a handler.
            mHandler.post(mBlinkRunnable);
            //mHandler.post(mUpdateRunnable);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
        catch (Exception e2) {
            Log.e(TAG, "Error", e2);

        }
        Log.d(TAG, "Available GPIO: " + manager.getGpioList());

        List<String> deviceList = manager.getI2cBusList();
        if (deviceList.isEmpty()) {
            Log.i(TAG, "No I2C bus available on this device.");
        } else {
            Log.i(TAG, "List of available devices: " + deviceList);
        }

        //https://developer.android.com/things/sdk/pio/i2c#java
        // Attempt to access the I2C device
        try {
            mDevice = manager.openI2cDevice(I2C_DEVICE_NAME, I2C_ADDRESS);
        } catch (IOException e) {
            Log.w(TAG, "Unable to access I2C device", e);
        }

        //Set up the reference to the database
        mDatabase = FirebaseDatabase.getInstance().getReference();

        //Define the listener for new data from the database
        ValueEventListener project3listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                try {
                    //Get the red PWM byte to send
                    Object redPWM = dataSnapshot.child("iotDevice1").child(PWM_RED_LED).getValue();
                    byte redPWMbyte = getPWMByte(redPWM);

                    //Get the green PWM byte to send
                    Object greenPWM = dataSnapshot.child("iotDevice1").child(PWM_GREEN_LED).getValue();
                    byte greenPWMbyte = getPWMByte(greenPWM);

                    //Get the blue PWM byte to send
                    Object bluePWM = dataSnapshot.child("iotDevice1").child(PWM_BLUE_LED).getValue();
                    byte bluePWMbyte = getPWMByte(bluePWM);

                    //Get the Motor PWM byte to send
                    Object motorPWM = dataSnapshot.child("iotDevice1").child(PWM_MOTOR).getValue();
                    byte motorPWMbyte = getPWMByte(motorPWM);

                    //Write the LED and motor PWM values to their respective registers
                    writeRegister(mDevice, addressLEDRed, redPWMbyte);
                    writeRegister(mDevice, addressLEDGreen, greenPWMbyte);
                    writeRegister(mDevice, addressLEDBlue, bluePWMbyte);
                    writeRegister(mDevice, addressMotor, motorPWMbyte);

                    //Get the DAC output value that we want to set (5 bits)
                    Object DAC1OUT = dataSnapshot.child("iotDevice1").child("DAC1OUT").getValue();
                    //Use custom function to turn encapsulated string to a byte
                    byte DACval = stringObjToByte(DAC1OUT);
                    //Write it to the register
                    writeRegister(mDevice, DAC1_ADDRESS, DACval);

                }
                catch (Exception e) {
                    //Catch any exceptions that may arise and log them
                    //Could be database errors or data conversion errors
                    Log.e(TAG, "Exception", e);
                }

                Log.v(TAG, "\n");

            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                // Getting Post failed, log a message
                Log.w(TAG, "loadPost:onCancelled", databaseError.toException());
                //Required function
            }
        };

        //Start the listener that we set up
        mDatabase.addValueEventListener(project3listener);


    }

    //GPIO/LED blink code from https://developer.android.com/things/training/first-device/peripherals
    //This "runnable" should run every second to toggle the LED
    private Runnable mBlinkRunnable = new Runnable() {
        @Override
        public void run() {
            // Exit if the GPIO is already closed
            //Log.v(TAG, "Run");
            if (mLedGpio == null) {
                Log.v(TAG, "Info: GPIO already closed");
                return;
            }

            try {
                // Step 3. Toggle the LED state
                mLedGpio.setValue(!mLedGpio.getValue());

                // Step 4. Schedule another event after delay.
                mHandler.postDelayed(mBlinkRunnable, INTERVAL_BETWEEN_BLINKS_MS);

                //value =  mLedGpio.getValue();

            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }
    };

    /**
     * Converts encapsulated string object into a byte.
     * Does not handle overflow
     * @param inputStrObj Input string of hex values (e.g. 0F, 0A23)
     * @return The value from the string as byte
     */
    public byte stringObjToByte(Object inputStrObj) {
        //Convert Object to decimal string
        String inputStr = inputStrObj.toString();

        //Convert decimal string to int
        int integer = Integer.parseInt(inputStr);

        //Then to a hexadecimal string
        String HexString = Integer.toHexString(integer);

        //Convert the hex value to a byte
        byte[] PWMHex = hexStringToByteArray(HexString);

        //Get lowest byte as value to be written
        byte outputByte = PWMHex[0];

        return outputByte;
    }

    /**
     * Turns encapsulated string into a byte with max of 100 and min of 0 (out of 100)
     * @param inputPWM Is the object encapsulated PWM value as a string
     * @return The PWM in byte format
     */
    public byte getPWMByte(Object inputPWM) {
        //Convert Object to decimal string
        String PWMString = inputPWM.toString();

        //Convert decimal string to int
        int PWMInt = Integer.parseInt(PWMString);

        //Define maximum and minimum values
        if (PWMInt > 100) {
            PWMInt = 100;
        }
        else if (PWMInt < 0) {
            PWMInt = 0;
        }

        //Then to a hexadecimal string
        String HexString = Integer.toHexString(PWMInt);

        //Convert the hex value to a byte
        byte[] PWMHex = hexStringToByteArray(HexString);

        //Get lowest byte as value to be written
        byte PWMbyte = PWMHex[0];

        return PWMbyte;
    }

    /**
     * Write byte value to a register via I2C
     * @param device Slave device identifier
     * @param address Register address to be written to
     * @param value Byte value to be written
     */
    //Uses code from https://developer.android.com/things/sdk/pio/i2c#java
    public void writeRegister(I2cDevice device, int address, byte value) {
        // Write value to slave
        try {
            device.writeRegByte(address, value);
        } catch (IOException e) {
            Log.w(TAG, "Unable to write to I2C device", e);
            return;
        }

        //Log for debug
        Log.d(TAG, "Set register " + address + " to " + value);
    }

    /**
     * Read byte value from a register via I2C
     * @param device Slave device identifier
     * @param address Register to be read from
     * @return Value read from address
     */
    //https://developer.android.com/things/sdk/pio/i2c#java
    public byte readRegister(I2cDevice device, int address)  {
        // Read one register from slave, default result to 0 for return
        byte value;
        try {
            value =  device.readRegByte(address);
        } catch (IOException e) {
            Log.w(TAG, "Unable to read from I2C device", e);
            return 0x00;//set to 0
        }

        //Log the result for debug
        Log.d(TAG, "Read register " + address + " as " + value);

        return value;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //Close I2C device
        if (mDevice != null) {
            try {
                mDevice.close();
                mDevice = null;
            } catch (IOException e) {
                Log.w(TAG, "Unable to close I2C device", e);
            }
        }

        //GPIO/LED blink code from https://developer.android.com/things/training/first-device/peripherals
        // Step 4. Remove handler events on close.
        mHandler.removeCallbacks(mBlinkRunnable);
        //mHandler.removeCallbacks(mUpdateRunnable);

        // Step 5. Close the resource.
        if (mLedGpio != null) {
            try {
                mLedGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }

    }

    /**
     * Converts a string of hex values into a byte array
     * Uses code from https://stackoverflow.com/questions/140131/convert-a-string-representation-of-a-hex-dump-to-a-byte-array-using-java
     * @param s Input hex string
     * @return Byte array interpreted from string
     */
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data;
        if (len == 1) {
            data = new byte[1];
        }
        else {
            data = new byte[len / 2];
        }

        for (int i = 0; i < len; i += 2) {
            if (len == 1) {
                //Condition added by Alec for "nibble" case
                Log.v(TAG, "Length " + len);
                data[0] = (byte) (Character.digit(s.charAt(0), 16));
            }
            else {
                data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
            }
        }
        return data;
    }
}
