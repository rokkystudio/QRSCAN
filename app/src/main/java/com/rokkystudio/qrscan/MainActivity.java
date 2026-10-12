package com.rokkystudio.qrscan;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.LocaleListCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import badfox.logic.neoui.NeoThemeDefaults;
import badfox.logic.neoui.NeoTopBarView;
import kotlin.Unit;

/**
 * Displays a CameraX preview, reads barcodes with ML Kit and asks the user what to do with each result.
 */
public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final String PREFS_NAME = "qrscan_preferences";
    private static final String PREF_THEME = "theme";
    private static final String PREF_LANGUAGE = "language";

    private final AtomicBoolean frameInFlight = new AtomicBoolean(false);
    private final Executor mainExecutor = command -> new Handler(Looper.getMainLooper()).post(command);

    private NeoTopBarView appTopBar;
    private PreviewView previewView;
    private Button permissionButton;
    private ImageButton torchButton;

    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;
    private String lastValue = "";
    private boolean torchEnabled = false;
    private boolean resultDialogShowing = false;

    /**
     * Initializes the scanner UI, ML Kit barcode reader and camera permission flow.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        applySavedUiSettings();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cameraExecutor = Executors.newSingleThreadExecutor();
        barcodeScanner = BarcodeScanning.getClient(
            new BarcodeScannerOptions.Builder()
                .enableAllPotentialBarcodes()
                .build()
        );

        bindViews();
        configureTopBar();
        configureActions();
        ensureCameraPermission();
    }

    /**
     * Releases camera analysis and ML Kit resources owned by the activity.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    /**
     * Handles the camera permission response and starts the scanner when access is available.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                startCamera();
            } else {
                showPermissionRequired();
            }
        }
    }

    /**
     * Resolves all views used by the scanner screen.
     */
    private void bindViews() {
        appTopBar = findViewById(R.id.appTopBar);
        previewView = findViewById(R.id.previewView);
        permissionButton = findViewById(R.id.permissionButton);
        torchButton = findViewById(R.id.torchButton);
    }

    /**
     * Applies the common NeoUI application header used by projects in this workspace.
     */
    private void configureTopBar() {
        appTopBar.setAppInfo(
            getString(R.string.app_name),
            getString(R.string.app_subtitle),
            getString(R.string.app_info_version, BuildConfig.VERSION_NAME),
            R.mipmap.ic_launcher
        );
        appTopBar.setThemeOptions(
            NeoThemeDefaults.INSTANCE.dayNight(),
            getSavedTheme()
        , theme -> {
            if (!theme.equals(getSavedTheme())) {
                saveString(PREF_THEME, theme);
                AppCompatDelegate.setDefaultNightMode(theme.equals(NeoThemeDefaults.NIGHT) ?
                    AppCompatDelegate.MODE_NIGHT_YES :
                    AppCompatDelegate.MODE_NIGHT_NO
                );
                recreate();
            }
            return Unit.INSTANCE;
        });
        appTopBar.setLanguages(
            Arrays.asList("ru", "en"),
            getSavedLanguage()
        , language -> {
            if (!language.equals(getSavedLanguage())) {
                saveString(PREF_LANGUAGE, language);
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language));
                recreate();
            }
            return Unit.INSTANCE;
        });
        appTopBar.setThemeButtonVisible(true);
        appTopBar.setLanguageButtonVisible(true);
    }

    /**
     * Applies persisted theme and language before resources are inflated.
     */
    private void applySavedUiSettings() {
        String theme = getSavedTheme();
        AppCompatDelegate.setDefaultNightMode(theme.equals(NeoThemeDefaults.NIGHT) ?
            AppCompatDelegate.MODE_NIGHT_YES :
            AppCompatDelegate.MODE_NIGHT_NO
        );
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(getSavedLanguage()));
    }

    private String getSavedTheme() {
        return getPreferencesStore().getString(PREF_THEME, NeoThemeDefaults.DAY);
    }

    private String getSavedLanguage() {
        return getPreferencesStore().getString(PREF_LANGUAGE, "ru");
    }

    private void saveString(String key, String value) {
        getPreferencesStore().edit().putString(key, value).apply();
    }

    private SharedPreferences getPreferencesStore() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    }

    /**
     * Connects visible controls to camera permission and torch actions.
     */
    private void configureActions() {
        permissionButton.setOnClickListener(view -> requestCameraPermission());
        torchButton.setOnClickListener(view -> toggleTorch());
        renderTorchButton();
    }

    /**
     * Starts the camera immediately or shows the permission action when the camera is not yet allowed.
     */
    private void ensureCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            showPermissionRequired();
            requestCameraPermission();
        }
    }

    /**
     * Requests Android camera permission from the user.
     */
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.CAMERA }, CAMERA_PERMISSION_REQUEST);
    }

    /**
     * Displays the permission action when camera access is not available.
     */
    private void showPermissionRequired() {
        permissionButton.setVisibility(View.VISIBLE);
        torchButton.setEnabled(false);
    }

    /**
     * Opens the CameraX provider and binds preview and barcode analysis use cases.
     */
    private void startCamera() {
        permissionButton.setVisibility(View.GONE);

        ListenableFuture<ProcessCameraProvider> providerFuture = ProcessCameraProvider.getInstance(this);
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bindCameraUseCases();
            } catch (Exception exception) {
                showScannerError(exception);
            }
        }, mainExecutor);
    }

    /**
     * Binds the camera preview and image analyzer to the activity lifecycle.
     */
    private void bindCameraUseCases() {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build();
        analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;

        cameraProvider.unbindAll();
        camera = cameraProvider.bindToLifecycle(this, selector, preview, analysis);
        configureTorchState();
    }

    /**
     * Sends preview frames to ML Kit and keeps only one active barcode task at a time.
     */
    @ExperimentalGetImage
    private void analyzeImage(ImageProxy imageProxy) {
        if (!frameInFlight.compareAndSet(false, true)) {
            imageProxy.close();
            return;
        }

        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage == null) {
            frameInFlight.set(false);
            imageProxy.close();
            return;
        }

        InputImage inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
        barcodeScanner.process(inputImage)
            .addOnSuccessListener(this::handleBarcodes)
            .addOnFailureListener(this::showScannerError)
            .addOnCompleteListener(task -> {
                frameInFlight.set(false);
                imageProxy.close();
            });
    }

    /**
     * Selects the first decoded barcode value and asks the user what to do next.
     */
    private void handleBarcodes(List<Barcode> barcodes) {
        if (barcodes == null || barcodes.isEmpty() || resultDialogShowing) {
            return;
        }

        String value = barcodes.get(0).getRawValue();
        if (value == null || value.trim().isEmpty() || value.equals(lastValue)) {
            return;
        }

        lastValue = value;
        showResultDialog(value);
    }

    /**
     * Shows scan result actions without keeping a persistent result panel on screen.
     */
    private void showResultDialog(String value) {
        resultDialogShowing = true;
        new AlertDialog.Builder(this)
            .setTitle(R.string.scan_result_title)
            .setMessage(value)
            .setPositiveButton(R.string.open, (dialog, which) -> openValue(value))
            .setNegativeButton(R.string.copy, (dialog, which) -> copyValue(value))
            .setNeutralButton(R.string.share, (dialog, which) -> shareValue(value))
            .setOnDismissListener(dialog -> resultDialogShowing = false)
            .show();
    }

    /**
     * Updates the flashlight button according to the currently bound camera.
     */
    private void configureTorchState() {
        boolean hasFlash = camera != null && camera.getCameraInfo().hasFlashUnit();
        torchButton.setEnabled(hasFlash);
        renderTorchButton();
        if (!hasFlash) {
            Toast.makeText(this, R.string.torch_unavailable, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Toggles the device flashlight through the current CameraX camera control.
     */
    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) {
            Toast.makeText(this, R.string.torch_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        torchEnabled = !torchEnabled;
        camera.getCameraControl().enableTorch(torchEnabled);
        renderTorchButton();
    }

    /**
     * Renders the two-state torch floating button.
     */
    private void renderTorchButton() {
        torchButton.setImageResource(torchEnabled ? R.drawable.ic_torch_on : R.drawable.ic_torch_off);
        torchButton.setContentDescription(getString(torchEnabled ? R.string.torch_on : R.string.torch_off));
    }

    /**
     * Copies the current barcode value to the clipboard.
     */
    private void copyValue(String value) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.app_name), value));
        Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show();
    }

    /**
     * Opens the current barcode value as an Android view intent when an application can handle it.
     */
    private void openValue(String value) {
        Uri uri = Uri.parse(value);
        if (uri.getScheme() == null) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Shares the current barcode value through Android text share targets.
     */
    private void shareValue(String value) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, value);
        startActivity(Intent.createChooser(intent, getString(R.string.share)));
    }

    /**
     * Displays a scanner error without adding extra scanner labels to the main UI.
     */
    private void showScannerError(Exception exception) {
        Toast.makeText(this, getString(R.string.scanner_error, exception.getMessage()), Toast.LENGTH_LONG).show();
    }
}
