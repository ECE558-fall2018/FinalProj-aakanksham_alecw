package com.example.aakan.sensormanager;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.DecimalFormat;

import static android.content.ContentValues.TAG;

public class MainActivity extends Activity implements SensorEventListener {

    private float lastX, lastY, lastZ;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;

    private float vibrateThreshold = 0;

    private TextView currentX, currentY, currentZ, Saturation_setX, PWM_setX;
    private SeekBar PWM_MOTOR, Saturation;
    private TextView Angle, RGBValue;
    private int PWM_MOTOR_Value;
    private double SaturationValue;

    public Vibrator v;

    private int RED, GREEN, BLUE, MOTOR;

    private double colorAngle;

    private boolean motorToggle = false;

    long lastMillis = 0;


    DecimalFormat df = new DecimalFormat("##.#");

    // Set up firebase
    FirebaseDatabase database = FirebaseDatabase.getInstance();
    final DatabaseReference myRef = database.getReference("iotDevice1");

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeViews();

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            // success! we have an accelerometer

            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            vibrateThreshold = accelerometer.getMaximumRange() / 2;
        } else {
            // fail, we dont have an accelerometer!
        }

        //initialize vibration
        v = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        try {
            PWM_MOTOR.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int val = (progress * (seekBar.getWidth() - 2 * seekBar.getThumbOffset())) / seekBar.getMax();
                    PWM_MOTOR_Value = progress;

                    PWM_setX.setText(String.valueOf(PWM_MOTOR_Value));

                    //PWM_setX.setX(seekBar.getX() + val + seekBar.getThumbOffset() / 2);
                    //myRef.child("PWM_MOTOR").setValue(PWM_MOTOR_Value);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            Saturation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    int val = (progress * (seekBar.getWidth() - 2 * seekBar.getThumbOffset())) / seekBar.getMax();;
                    SaturationValue = progress;

                    Saturation_setX.setText(String.valueOf((int) SaturationValue));
                    //Saturation_setX.setX(seekBar.getX() + val + seekBar.getThumbOffset() / 2);
                }

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
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    //onPause() unregister the accelerometer for stop listening the events
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        deltaX = event.values[0];
        deltaY = event.values[1];
        deltaZ = event.values[2];

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
        myRef.child("PWM_RED_LED").setValue(RED);
        myRef.child("PWM_GREEN_LED").setValue(GREEN);
        myRef.child("PWM_BLUE_LED").setValue(BLUE);
        myRef.child("PWM_MOTOR").setValue(MOTOR);
    }

    private void calculateRGB() {
        // Algorithm found from:
        // https://en.wikipedia.org/wiki/HSL_and_HSV
        double Hue, HuePrime;
        double Saturation;
        double Value;
        double Chroma, X;
        int PWMChroma, PWMX;
        int Rprime, Gprime, Bprime, modifier;

        Hue = calculateHue();
        Saturation = SaturationValue/100.0;
        Value = calculateValue();

        Chroma = Saturation * Value;

        HuePrime = Hue/60.0;

        X = Chroma * ( 1.0 - Math.abs((HuePrime%2) - 1.0) );

        //Multiply these values by 100 to get PWM values
        PWMChroma   = (int) (Chroma * 100.0);
        PWMX        = (int) (X * 100.0);

        modifier = (int) ((Value - Chroma) * 100.0);

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

        RED     = Rprime + modifier;
        GREEN   = Gprime + modifier;
        BLUE    = Bprime + modifier;

        String RGBText = "(" + String.valueOf(RED) + "," + String.valueOf(GREEN) + "," + String.valueOf(BLUE) + ")";
        RGBValue.setText(RGBText);
    }

    private double calculateValue() {
        double tiltAngle, Value, xyPlaneAccel;
        final double accelerationGravity = 9.81;

        xyPlaneAccel=  Math.sqrt(Math.pow(deltaX,2) + Math.pow(deltaY,2));

        if (xyPlaneAccel > accelerationGravity) {
            xyPlaneAccel = accelerationGravity;
        }
        tiltAngle = Math.abs( Math.toDegrees(Math.atan((xyPlaneAccel)/(accelerationGravity))) );

        // Normalize the value to 90 degrees
        Value = tiltAngle/90.0;

        return Value;
    }

    private double calculateHue() {
        double rawAngle = Math.toDegrees(Math.atan((deltaX)/(deltaY)));
        double offsetAngle;
        if ((deltaX < 0) & (deltaY >= 0)) {
            rawAngle = rawAngle * (-1);
            offsetAngle = 0;
        }
        else if ((deltaX < 0) & (deltaY < 0)) {
            rawAngle = 90 - rawAngle;
            offsetAngle = 90;
        }
        else if ((deltaX >= 0) & (deltaY < 0)) {
            rawAngle = rawAngle * (-1);
            offsetAngle = 180;
        }
        else {//if ((deltaX >= 0) & (deltaY >= 0)) {
            rawAngle = 90 - rawAngle;
            offsetAngle = 270;
        }

        colorAngle = (rawAngle + offsetAngle);
        return colorAngle;
    }

    private void checkForShake() {
        boolean shake = false;
        final int shakeThreshold = 30; // m/(s^2)
        final int shakeTimeOut = 2000; //milliseconds
        long currentMillis, difference;

        if (true) {
            if (deltaX > shakeThreshold) {
                shake = true;
            } else if (deltaY > shakeThreshold) {
                shake = true;
            } else if (deltaZ > shakeThreshold) {
                shake = true;
            }

            if (shake) {
                currentMillis = System.currentTimeMillis();
                difference = currentMillis - lastMillis;
                if (difference > shakeTimeOut) {
                    motorToggle = !motorToggle;
                    lastMillis = System.currentTimeMillis();
                }
            }

            if (motorToggle) {
                MOTOR = getMotor();
            } else {
                MOTOR = 0;
            }
        }
    }

    private int getMotor() {

        return PWM_MOTOR_Value;
    }
    // display the current x,y,z accelerometer values
    public void displayCurrentValues() {
        currentX.setText(df.format(deltaX));
        currentY.setText(df.format(deltaY));
        currentZ.setText(df.format(deltaZ));

        Angle.setText(df.format(colorAngle));
    }

    public void setActivityBackgroundColor(int R, int G, int B) {
       //Color color = new Color (R,G,B);
        R = (R*255)/100;
        G = (G*255)/100;
        B = (B*255)/100;

        View view = findViewById(com.example.aakan.sensormanager.R.id.final_proj_root);//this.getWindow().getDecorView();

        view.setBackgroundColor(Color.rgb(R,G,B));
    }
}