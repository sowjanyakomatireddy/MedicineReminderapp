package app.medreminder;

import android.os.Bundle;

import app.medreminder.alarm.MedAlarmPlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MedAlarmPlugin.class); // the native alarm (local plugin, not an npm package)
        super.onCreate(savedInstanceState);
    }
}
