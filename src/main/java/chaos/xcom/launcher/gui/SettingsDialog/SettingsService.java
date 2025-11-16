package chaos.xcom.launcher.gui.SettingsDialog;

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;

@Singleton
@RequiredArgsConstructor
public class SettingsService {

    private final Instance<SettingsDialog> settingsDialog;

    public void openSettingsDialog() {
        settingsDialog.get().openSettings();
    }


}
