package com.example.aakan.sensormanager;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.DecimalFormat;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private float Xacceleration = 0;
    private float Yacceleration = 0;
    private float Zacceleration = 0;

    private TextView currentX, currentY, currentZ, Saturation_setX, PWM_setX;
    private SeekBar PWM_MOTOR, Saturation;
    private TextView Angle, RGBValue;
    private int Seekbar_MOTOR_Progress;
    private double SaturationValue;

    private int RED, GREEN, BLUE, MOTOR;

    private String motorPWMKey = "MOTOR_PWM", saturationKey = "SATURATION";

    private double colorAngle;

    private boolean motorToggle = false;
    private String motorToggleKey = "MOTOR_TOGGLE";

    long lastMillis = 0;


    DecimalFormat accelerationFormat = new DecimalFormat("##.#");
    DecimalFormat angleFormat = new DecimalFormat("###");

    // Set up firebase
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    final DatabaseReference myRef = database.getReference("iotDevice1");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Set up view references in layout
        initializeViews();

        //If the app is freshly started, set seekbars to 50
        if (savedInstanceState == null) {
            Seekbar_MOTOR_Progress = 50;
            PWM_setX.setText(String.valueOf(Seekbar_MOTOR_Progress));

            SaturationValue = 50;
            Saturation_setX.setText(String.valueOf((int)SaturationValue));
        }
        //Otherwise get the motor toggle status, and the seekbar values from the saved instance state
        else {
            motorToggle = savedInstanceState.getBoolean(motorToggleKey);

            Seekbar_MOTOR_Progress = savedInstanceState.getInt(motorPWMKey);
            PWM_setX.setText(String.valueOf(Seekbar_MOTOR_Progress));

            SaturationValue = savedInstanceState.getDouble(saturationKey);
            Saturation_setX.setText(String.valueOf((int)SaturationValue));
        }

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // success! we have an accelerometer
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        } else {
            // fail, we dont have an accelerometer!
            Log.e(TAG, "Error - No accelerometer found");
        }

        //Set up the seekbar listener in a try/catch block
        try {
            // Motor PWM seekbar
            PWM_MOTOR.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    //Get the seekbar value and update the text in the view
                    Seekbar_MOTOR_Progress = progress;
                    PWM_setX.setText(String.valueOf(Seekbar_MOTOR_Progress));
                }

                //Required functions
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            // Saturation percentatge seekbar
            Saturation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    //Get the seekbar value and update the text in the view
                    SaturationValue = progress;
                    Saturation_setX.setText(String.valueOf((int) SaturationValue));
                }

                //Required functions
                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });
        }
        catch (Exception e) {
            Log.e(TAG, "Error - Exception " + e);
        }

    }

    //Set up the views from their XML references
    public void initializeViews() {
        currentX = (TextView) findViewById(R.id.currentX);
        currentY = (TextView) findViewById(R.id.currentY);
        currentZ = (TextView) findViewById(R.id.currentZ);

        PWM_MOTOR = (SeekBar) findViewById(R.id.PWM_MOTOR_SeekBar);
        Saturation = (SeekBar) findViewById(R.id.Saturation_SeekBar);
        Angle = (TextView) findViewById(R.id.Angle);
        RGBValue = (TextView) findViewById(R.id.RGBValues);
        Saturation_setX = (TextView) findViewById(R.id.Saturation_setX);
        PWM_setX = (TextView) findViewById(R.id.PWM_setX);
    }

    //onResume() register the accelerometer for listening the events
    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    //onPause() unregister the accelerometer for stop listening the events
    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    //Required Function
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    //Any time accelerometer values change, run this loop
    @Override
    public void onSensorChanged(SensorEvent event) {
        // Get the accelerometer values on all 3 axis
        Xacceleration = event.values[0];
        Yacceleration = event.values[1];
        Zacceleration = event.values[2];

        // display the current x,y,z accelerometer values
        displayCurrentValues();

        // calculate RGB values
        calculateRGB();

        // Check if the device has been shaken and toggle the motor accordingly
        checkForShake();

        //Update the database with our new values
        updateFireBase();

        // Change background color
        setActivityBackgroundColor(RED, GREEN, BLUE);
    }

    private void updateFireBase() {
        // Update the RGB PWM values, and the motor value in Google Firebase
        myRef.child("PWM_RED_LED").setValue(RED);
        myRef.child("PWM_GREEN_LED").setValue(GREEN);
        myRef.child("PWM_BLUE_LED").setValue(BLUE);
        myRef.child("PWM_MOTOR").setValue(MOTOR);
    }

    // This function gets the accelerometer values, creates a
    // color in the HSV colorspace based on device orientation,
    // then converts it to RGB
    private void calculateRGB() {
        // Algorithm found from:
        // https://en.wikipedia.org/wiki/HSL_and_HSV
        double Hue, HuePrime;
        double Saturation;
        double Value;
        double Chroma, X;
        int PWMChroma, PWMX;
        int Rprime, Gprime, Bprime, modifier;

        //Get HSV colorspace values
        //Get Hue based on X-Y plane device orientation
        Hue = calculateHue();
        //Get Saturation from app seekbar
        Saturation = SaturationValue/100.0;
        //Get the (brightness) Value from Z dimension orientation
        Value = calculateValue();

        //Calculate chroma, and the face of the color wheel we are on
        //This section implements the algorithm from Wikipedia linked above
        Chroma = Saturation * Value;

        HuePrime = Hue/60.0;

        X = Chroma * ( 1.0 - Math.abs((HuePrime%2) - 1.0) );

        //Multiply these values by 100 to get PWM values
        PWMChroma   = (int) (Chroma * 100.0);
        PWMX        = (int) (X * 100.0);

        //Modifier to be added to RGB prime values later
        modifier = (int) ((Value - Chroma) * 100.0);

        //Set the RGB Prime values based on current face of the color wheel
        if ( (HuePrime >= 0) & (HuePrime <= 1) ) {
            Rprime     = PWMChroma;
            Gprime   = PWMX;
            Bprime    = 0;
        }
        else if ( (HuePrime > 1) & (HuePrime <= 2) ) {
            Rprime     = PWMX;
            Gprime   = PWMChroma;
            Bprime    = 0;
        }
        else if ( (HuePrime > 2) & (HuePrime <= 3) ) {
            Rprime     = 0;
            Gprime   = PWMChroma;
            Bprime    = PWMX;
        }
        else if ( (HuePrime > 3) & (HuePrime <= 4) ) {
            Rprime     = 0;
            Gprime   = PWMX;
            Bprime    = PWMChroma;
        }
        else if ( (HuePrime > 4) & (HuePrime <= 5) ) {
            Rprime     = PWMX;
            Gprime   = 0;
            Bprime    = PWMChroma;
        }
        else if ( (HuePrime > 5) & (HuePrime <= 6) ) {
            Rprime     = PWMChroma;
            Gprime   = 0;
            Bprime    = PWMX;
        }
        else {
            Rprime     = 0;
            Gprime   = 0;
            Bprime    = 0;
        }

        //Add the modifier to the RGB prime values to get the RGB colors
        RED     = Rprime + modifier;
        GREEN   = Gprime + modifier;
        BLUE    = Bprime + modifier;

        //Update the view with the current RGB PWM values
        String RGBText = "(" + String.valueOf(RED) + "," + String.valueOf(GREEN) + "," + String.valueOf(BLUE) + ")";
        RGBValue.setText(RGBText);
    }

    //Get the Value based on the current Z-axis orientation
    private double calculateValue() {
        double tiltAngle, Value, xyPlaneAccel;
        final double accelerationGravity = 9.81; // m/(s^2)

        //Get the X-Y plane gravity vector
        xyPlaneAccel=  Math.sqrt(Math.pow(Xacceleration,2) + Math.pow(Yacceleration,2));

        //Set max value as the acceleration of gravity ( m/(s^2) )
        if (xyPlaneAccel > accelerationGravity)
            xyPlaneAccel = accelerationGravity;

        //Use the arcsine function to get the angle of the X-Y gravity vector  vs acceleration of gravity
        tiltAngle = Math.abs( Math.toDegrees(Math.asin((xyPlaneAccel)/(accelerationGravity))) );

        // Normalize the value to 90 degrees (min is 0, max is 1)
        Value = tiltAngle/90.0;

        return Value;
    }

    //Calculate the hue angle (X-Y plane oreintation)
    private double calculateHue() {
        double rawAngle;
        double offsetAngle;

        //Use arctangent to get the angle between X and Y acceleration vectors
        rawAngle = Math.toDegrees(Math.atan((Xacceleration)/(Yacceleration)));

        //If we are in the top-right quadrant, negate the angle, offset is 0 for this quadrant
        if ((Xacceleration < 0) & (Yacceleration >= 0)) {
            rawAngle = rawAngle * (-1);
            offsetAngle = 0;
        }
        //If we are in the bottom-right quadrant, get the angle inverse, offset is 90 for this quadrant
        else if ((Xacceleration < 0) & (Yacceleration < 0)) {
            rawAngle = 90 - rawAngle;
            offsetAngle = 90;
        }
        //If we are in the bottom-left quadrant, negate the angle, offset is 180 for this quadrant
        else if ((Xacceleration >= 0) & (Yacceleration < 0)) {
            rawAngle = rawAngle * (-1);
            offsetAngle = 180;
        }
        //If we are in the top-right quadrant, get the angle inverse, offset is 270 for this quadrant
        else {//if ((Xacceleration >= 0) & (Yacceleration >= 0)) {
            rawAngle = 90 - rawAngle;
            offsetAngle = 270;
        }

        //The hue angle is the addition of the modified raw angle, and its quadrant offset
        colorAngle = (rawAngle + offsetAngle);
        return colorAngle;
    }

    //Check accelerometer values to see if the device has been shaken
    private void checkForShake() {
        boolean shake = false;

        long currentMillis, difference;

        //Set the acceleration threshold to register a "shake"
        final int shakeThreshold = 30; // m/(s^2)

        //Require an amount of time between registered shakes
        final int shakeTimeOut = 2000; //milliseconds

        if (true) {
            // if acceleration in any direction is greater than 30 m/(s^2)
            // (about 3x acceleration of gravity) then register a shake as true
            if (Xacceleration > shakeThreshold) {
                shake = true;
            } else if (Yacceleration > shakeThreshold) {
                shake = true;
            } else if (Zacceleration > shakeThreshold) {
                shake = true;
            }

            // If a shake was detected, and it's been 2 seconds since the motor was toggled last
            // then toggle the motor
            if (shake) {
                currentMillis = System.currentTimeMillis();
                difference = currentMillis - lastMillis;
                if (difference > shakeTimeOut) {
                    motorToggle = !motorToggle;
                    lastMillis = System.currentTimeMillis();
                }
            }

            //If the motor has been toggled above, set MOTOR value to be written to firebase later
            if (motorToggle) {
                //If toggle is true, set motor to the seekbar value
                MOTOR = Seekbar_MOTOR_Progress;
            } else {
                //Otherwise turn of the motor
                MOTOR = 0;
            }
        }
    }

    // display the current x,y,z accelerometer values, and the Hue angle
    public void displayCurrentValues() {
        currentX.setText(accelerationFormat.format(Xacceleration));
        currentY.setText(accelerationFormat.format(Yacceleration));
        currentZ.setText(accelerationFormat.format(Zacceleration));

        Angle.setText(angleFormat.format(colorAngle));
    }

    //Set the app background color to the current RGB PWM values
    public void setActivityBackgroundColor(int R, int G, int B) {
        //Convert the values from PWM to byte values (0 to 255)
        R = (R*255)/100;
        G = (G*255)/100;
        B = (B*255)/100;

        //Get the app layout as a view
        View view = findViewById(com.example.aakan.sensormanager.R.id.final_proj_root);//this.getWindow().getDecorView();

        //Set the view color to RGB bytes
        view.setBackgroundColor(Color.rgb(R,G,B));
    }

    // Save the seekbar values, and the motor toggle state
    @Override
    protected void onSaveInstanceState (Bundle outState) {
        super.onSaveInstanceState(outState);

        Log.d(TAG, "onSaveInstanceState");

        outState.putBoolean(motorToggleKey, motorToggle);
        outState.putInt(motorPWMKey, Seekbar_MOTOR_Progress);
        outState.putDouble(saturationKey, SaturationValue);
    }
}