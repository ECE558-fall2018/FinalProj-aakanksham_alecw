package alec.androidthingsece558;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.GpioCallback;
import com.google.android.things.contrib.driver.button.Button;
import com.google.android.things.contrib.driver.button.ButtonInputDriver;

import java.io.IOException;

public class ButtonActivity extends Activity {
    private static final String TAG = "ButtonActivity";
    private static final String BUTTON_PIN_NAME = "BCM21"; // GPIO port wired to the button

    private Gpio mButtonGpio;

    private ButtonInputDriver mButtonInputDriver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            // Step 3. Initialize button driver with selected GPIO pin
            mButtonInputDriver = new ButtonInputDriver(
                    BUTTON_PIN_NAME,
                    Button.LogicState.PRESSED_WHEN_LOW,
                    KeyEvent.KEYCODE_SPACE);
        } catch (IOException e) {
            Log.e(TAG, "Error configuring GPIO pin", e);
        }
    }

    @Override
    protected void onDestroy(){
        super.onDestroy();

        // Step 5. Close the driver and unregister
        if (mButtonInputDriver != null) {
            try {
                mButtonInputDriver.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing Button driver", e);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mButtonInputDriver.register();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mButtonInputDriver.unregister();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            // Handle button pressed event
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            // Handle button released event
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }
    /*
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PeripheralManager manager = PeripheralManager.getInstance();
        try {
            // Step 1. Create GPIO connection.
            mButtonGpio = manager.openGpio(BUTTON_PIN_NAME);
            // Step 2. Configure as an input.
            mButtonGpio.setDirection(Gpio.DIRECTION_IN);
            // Step 3. Enable edge trigger events.
            mButtonGpio.setEdgeTriggerType(Gpio.EDGE_FALLING);
            // Step 4. Register an event callback.
            mButtonGpio.registerGpioCallback(mCallback);
        } catch (IOException e) {
            Log.e(TAG, "Error on PeripheralIO API", e);
        }
    }

    // Step 4. Register an event callback.
    private GpioCallback mCallback = new GpioCallback() {
        @Override
        public boolean onGpioEdge(Gpio gpio) {
            Log.i(TAG, "GPIO changed, button pressed");

            // Step 5. Return true to keep callback active.
            return true;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // Step 6. Close the resource
        if (mButtonGpio != null) {
            mButtonGpio.unregisterGpioCallback(mCallback);
            try {
                mButtonGpio.close();
            } catch (IOException e) {
                Log.e(TAG, "Error on PeripheralIO API", e);
            }
        }
    }*/
}

//https://developer.android.com/things/training/first-device/drivers