package com.example.aakan.sensormanager;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends Activity implements SensorEventListener {

    private float lastX, lastY, lastZ;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private float deltaXMax = 0;
    private float deltaYMax = 0;
    private float deltaZMax = 0;

    private float deltaX = 0;
    private float deltaY = 0;
    private float deltaZ = 0;

    private float vibrateThreshold = 0;

    private TextView currentX, currentY, currentZ, maxX, maxY, maxZ;

    public Vibrator v;

    private int RED, GREEN, BLUE;


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


        //myRef.addValueEventListener(ValueEventListener);
    }

    public void initializeViews() {
        currentX = (TextView) findViewById(R.id.currentX);
        currentY = (TextView) findViewById(R.id.currentY);
        currentZ = (TextView) findViewById(R.id.currentZ);

        maxX = (TextView) findViewById(R.id.maxX);
        maxY = (TextView) findViewById(R.id.maxY);
        maxZ = (TextView) findViewById(R.id.maxZ);
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

        // clean current values
        displayCleanValues();
        // display the current x,y,z accelerometer values
        displayCurrentValues();
        // calculate RGB values
        calculateRGB();
        updateFireBase();
        // display the max x,y,z accelerometer values
        displayMaxValues();

        deltaX = event.values[0];
        deltaY = event.values[1];
        deltaZ = event.values[2];
/*
        // get the change of the x,y,z values of the accelerometer
        deltaX = Math.abs(lastX - event.values[0]);
        deltaY = Math.abs(lastY - event.values[1]);
        deltaZ = Math.abs(lastZ - event.values[2]);

        // if the change is below 2, it is just plain noise
        if (deltaX < 2)
            deltaX = 0;
        if (deltaY < 2)
            deltaY = 0;
        if ((deltaZ > vibrateThreshold) || (deltaY > vibrateThreshold) || (deltaZ > vibrateThreshold)) {
            v.vibrate(50);
        }*/
    }

    public void displayCleanValues() {
        currentX.setText("0.0");
        currentY.setText("0.0");
        currentZ.setText("0.0");
    }

    private void updateFireBase() {
        myRef.child("PWM_RED_LED").setValue(RED);
        myRef.child("PWM_GREEN_LED").setValue(GREEN);
        myRef.child("PWM_BLUE_LED").setValue(BLUE);
    }

    public void calculateRGB() {
        // Algorithm found from:
        // https://en.wikipedia.org/wiki/HSL_and_HSV
        double Hue, HuePrime;
        double Saturation;
        double Value;
        double Chroma, X;
        int PWMChroma, PWMX;

        Hue = calculateHue();
        Saturation = 1.0;
        Value = calculateValue();

        Chroma = Saturation * Value;

        HuePrime = Hue/60.0;

        X = Chroma * ( 1.0 - Math.abs((HuePrime%2) - 1.0) );

        //Multiply these values by 100 to get PWM values
        PWMChroma   = (int) (Chroma * 100.0);
        PWMX        = (int) (X * 100.0);

        if ( (HuePrime >= 0) & (HuePrime <= 1) ) {
            RED     = PWMChroma;
            GREEN   = PWMX;
            BLUE    = 0;
        }
        else if ( (HuePrime > 1) & (HuePrime <= 2) ) {
            RED     = PWMX;
            GREEN   = PWMChroma;
            BLUE    = 0;
        }
        else if ( (HuePrime > 2) & (HuePrime <= 3) ) {
            RED     = 0;
            GREEN   = PWMChroma;
            BLUE    = PWMX;
        }
        else if ( (HuePrime > 3) & (HuePrime <= 4) ) {
            RED     = 0;
            GREEN   = PWMX;
            BLUE    = PWMChroma;
        }
        else if ( (HuePrime > 4) & (HuePrime <= 5) ) {
            RED     = PWMX;
            GREEN   = 0;
            BLUE    = PWMChroma;
        }
        else if ( (HuePrime > 5) & (HuePrime <= 6) ) {
            RED     = PWMChroma;
            GREEN   = 0;
            BLUE    = PWMX;
        }
        else {
            RED     = 0;
            GREEN   = 0;
            BLUE    = 0;
        }
    }

    public double calculateValue() {
        double tiltAngle, Value;

        tiltAngle = Math.abs( Math.toDegrees(Math.atan((deltaY)/(deltaZ))) );

        // Normalize the value to 90 degrees
        Value = tiltAngle/90.0;

        return Value;
    }

    public double calculateHue() {
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

        return (rawAngle + offsetAngle);
    }
    // display the current x,y,z accelerometer values
    public void displayCurrentValues() {
        currentX.setText(Float.toString(deltaX));
        currentY.setText(Float.toString(deltaY));
        currentZ.setText(Float.toString(deltaZ));

        /*myRef.child("PWM_RED_LED").setValue(deltaX);
        myRef.child("PWM_GREEN_LED").setValue(deltaY);
        myRef.child("PWM_BLUE_LED").setValue(deltaZ);*/
    }

    // display the max x,y,z accelerometer values
    public void displayMaxValues() {
        if (deltaX > deltaXMax) {
            deltaXMax = deltaX;
            maxX.setText(Float.toString(deltaXMax));
        }
        if (deltaY > deltaYMax) {
            deltaYMax = deltaY;
            maxY.setText(Float.toString(deltaYMax));
        }
        if (deltaZ > deltaZMax) {
            deltaZMax = deltaZ;
            maxZ.setText(Float.toString(deltaZMax));
        }
    }
}