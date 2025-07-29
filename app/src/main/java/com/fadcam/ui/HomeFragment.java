package com.fadcam.ui;


import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ArgbEvaluator;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.res.AssetManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.text.Html;
import android.text.Spanned;
import android.text.format.Formatter;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageButton;

import androidx.annotation.RequiresApi;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.cardview.widget.CardView; // Add this

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import com.fadcam.CameraType;
import com.fadcam.Constants;
import com.fadcam.Log;
import com.fadcam.R;
import com.fadcam.services.RecordingService;
import com.fadcam.RecordingState;
import com.fadcam.SharedPreferencesManager;

import com.fadcam.Utils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.appbar.MaterialToolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.HashSet; // For combining lists
import java.util.Set;    // For combining lists
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.widget.ArrayAdapter;
import androidx.appcompat.app.AlertDialog;
import android.text.SpannableString;

public class HomeFragment extends BaseFragment {

    private static final String TAG = "HomeFragment";

    // ----- Fix Start for this method(fields)-----
    private static final String[] CLOCK_COLOR_NAMES = {"Purple", "Blue", "Green", "Teal", "Orange", "Red", "Dark Grey", "App Theme Dark", "Amoled Gray", "Gold", "Pink"};
    private static final String[] CLOCK_COLOR_HEX_VALUES = {"#673AB7", "#2196F3", "#4CAF50", "#009688", "#FF9800", "#F44336", "#424242", "#302745", "#CCCCCC", "#FFD700", "#F06292"};
    // ----- Fix Ended for this method(fields)-----

    private long recordingStartTime;
    private long videoBitrate;

    private final Handler handlerClock = new Handler();
    private Runnable updateInfoRunnable;
    private Runnable updateClockRunnable; // Declare here

    private TextureView textureView;



    private TextView tvPreviewPlaceholder;
    private MaterialButton buttonStartStop;
    private MaterialButton buttonPauseResume;
    private Button buttonCamSwitch;
    private boolean isPreviewEnabled = true;

    private View cardPreview;
    private Vibrator vibrator;


    private String[] tips;

    private int currentTipIndex = 0;


    private List<String> messageQueue;
    private List<String> recentlyShownMessages;
    private final Random random = new Random();
    private static final int RECENT_MESSAGE_LIMIT = 3; // Adjust as needed

    private android.os.PowerManager.WakeLock wakeLock;
//    private static final String PREF_FIRST_LAUNCH = "first_launch";

    private RecordingState recordingState = RecordingState.NONE;

    private BroadcastReceiver broadcastOnRecordingStarted;
    private BroadcastReceiver broadcastOnRecordingResumed;
    private BroadcastReceiver broadcastOnRecordingPaused;
    private BroadcastReceiver broadcastOnRecordingStopped;
    private BroadcastReceiver broadcastOnRecordingStateCallback;
    // ----- Fix Start for this class (HomeFragment) -----
    private BroadcastReceiver segmentCompleteStatsReceiver; // For segment completion to update stats
    // ----- Fix Ended for this class (HomeFragment) -----

    // ----- Fix Start for camera resource availability receiver -----
    private BroadcastReceiver cameraResourceAvailabilityReceiver;
    private boolean isCameraResourceAvailabilityReceiverRegistered = false;
    private boolean areCameraResourcesAvailable = true; // Default to true
    // ----- Fix End for camera resource availability receiver -----


    private CameraManager cameraManager;
    private boolean isTorchOn = false;

    private BroadcastReceiver torchReceiver;

    // ----- Fix Start for this method(fields)-----
    private Surface textureViewSurface; // To hold the Surface from TextureView
    // ----- Fix Ended for this method(fields)-----


    // --- Fields Needed for Stats Update ---
    private SharedPreferencesManager sharedPreferencesManager;
    private ExecutorService executorService;
    private BroadcastReceiver recordingCompleteReceiver;
    // ----- Fix Start for this class (HomeFragment) -----
    // private boolean isStatsReceiverRegistered = false; // This seemed to be for the general recordingCompleteReceiver
    private boolean isSegmentCompleteStatsReceiverRegistered = false;
    // ----- Fix Ended for this class (HomeFragment) -----

    // important


    // Call this method when the recording starts to acquire wake lock
    private void acquireWakeLock() {
        android.os.PowerManager powerManager = (android.os.PowerManager) requireActivity().getSystemService(Context.POWER_SERVICE); // Full path and context adjusted
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::RecordingLock");

        if (!wakeLock.isHeld()) {
            wakeLock.acquire();
            Log.d(TAG, "WakeLock acquired.");
        }
    }

    // Call this when the recording ends to release wake lock
    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released.");
        }
    }

    private void initializeMessages() {
        messageQueue = new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.easter_eggs_array)));
        recentlyShownMessages = new ArrayList<>();
        Collections.shuffle(messageQueue); // Shuffle the list initially
    }

    private void showRandomMessage() {
        if (messageQueue == null || messageQueue.isEmpty()) {
            initializeMessages(); // Reinitialize and shuffle if queue is empty or null
        }
        // Remove recently shown messages from the queue
        messageQueue.removeAll(recentlyShownMessages);
        // Ensure there are still messages to choose from
        if (!messageQueue.isEmpty()) {
            String randomMessage = messageQueue.remove(random.nextInt(messageQueue.size()));
            tvPreviewPlaceholder.setPadding(40, tvPreviewPlaceholder.getPaddingTop(), 40, tvPreviewPlaceholder.getPaddingBottom());
            tvPreviewPlaceholder.setText(randomMessage);
            AnimatorSet animatorSet = new AnimatorSet();
            ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(tvPreviewPlaceholder, "scaleX", 0.7f);
            ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(tvPreviewPlaceholder, "scaleY", 0.7f);
            scaleDownX.setDuration(150);
            scaleDownY.setDuration(150);
            ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(tvPreviewPlaceholder, "scaleX", 1.0f);
            ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(tvPreviewPlaceholder, "scaleY", 1.0f);
            scaleUpX.setDuration(150);
            scaleUpY.setDuration(150);
            ObjectAnimator rotateLeft = ObjectAnimator.ofFloat(tvPreviewPlaceholder, "rotation", 0f, -3f);
            rotateLeft.setDuration(80);
            ObjectAnimator rotateRight = ObjectAnimator.ofFloat(tvPreviewPlaceholder, "rotation", -3f, 3f);
            rotateRight.setDuration(80);
            ObjectAnimator rotateCenter = ObjectAnimator.ofFloat(tvPreviewPlaceholder, "rotation", 3f, 0f);
            rotateCenter.setDuration(80);
            final Drawable originalBackground = cardPreview.getBackground();
            // ----- Fix Start: Use gray flash for AMOLED theme (avoid duplicate variable) -----
            String themeName = sharedPreferencesManager.sharedPreferences.getString(com.fadcam.Constants.PREF_APP_THEME, Constants.DEFAULT_APP_THEME);
            boolean isAmoledLocal = "AMOLED".equalsIgnoreCase(themeName) || "Amoled".equalsIgnoreCase(themeName) || "Faded Night".equalsIgnoreCase(themeName);
            int flashColor = isAmoledLocal ? Color.parseColor("#232323") : Color.parseColor("#302745");
            ValueAnimator colorAnim = ValueAnimator.ofObject(new ArgbEvaluator(), 
                    flashColor, 
                    Color.RED, 
                    flashColor);
            // ----- Fix End: Use gray flash for AMOLED theme (avoid duplicate variable) -----
            colorAnim.setDuration(300);
            colorAnim.addUpdateListener(animator -> {
                int color = (int) animator.getAnimatedValue();
                cardPreview.setBackgroundColor(color);
            });
            colorAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    cardPreview.setBackground(originalBackground);
                }
            });
            AnimatorSet bounceSet = new AnimatorSet();
            bounceSet.playTogether(scaleDownX, scaleDownY);
            AnimatorSet expandSet = new AnimatorSet();
            expandSet.playTogether(scaleUpX, scaleUpY);
            AnimatorSet wobbleSet = new AnimatorSet();
            wobbleSet.playSequentially(rotateLeft, rotateRight, rotateCenter);
            animatorSet.playSequentially(bounceSet, expandSet, wobbleSet);
            animatorSet.start();
            colorAnim.start();
            recentlyShownMessages.add(randomMessage);
            if (recentlyShownMessages.size() > RECENT_MESSAGE_LIMIT) {
                recentlyShownMessages.remove(0); // Remove the oldest message
            }
            Collections.shuffle(messageQueue);
        } else {
            tvPreviewPlaceholder.setText("Oops! No messages available right now.");
        }
    }

    private void setupLongPressListener() {
        cardPreview.setOnLongClickListener(v -> {
            // 1. Perform haptic feedback
            performHapticFeedback();

            // When not recording, show a random funny message
            if (!isRecordingOrPaused()) {
                showRandomMessage();
                return true;
            }

            // 2. Unified Card Bounce Animation (Down then Up) - only for recording mode
            AnimatorSet cardBounceAnim = new AnimatorSet();
            ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(cardPreview, "scaleX", 0.9f);
            ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(cardPreview, "scaleY", 0.9f);
            scaleDownX.setDuration(50); // Fast scale down
            scaleDownY.setDuration(50);

            ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(cardPreview, "scaleX", 1.0f);
            ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(cardPreview, "scaleY", 1.0f);
            scaleUpX.setDuration(70); // Fast rebound
            scaleUpY.setDuration(70);

            cardBounceAnim.play(scaleDownX).with(scaleDownY); // Play scale down
            cardBounceAnim.play(scaleUpX).with(scaleUpY).after(scaleDownX); // Play scale up immediately after scale down completes

            cardBounceAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);

                    // 3. Core logic: toggle preview, update UI, save state (runs AFTER card bounce)
                    boolean wasEnabled = isPreviewEnabled;
                    isPreviewEnabled = !isPreviewEnabled;
                    updatePreviewVisibility(); // This is the main visual change for enabling/disabling preview
                    savePreviewState();
                    
                    // If we're enabling the preview (it was disabled before), reset the TextureView
                    // to ensure we don't see any stale frames
                    if (!wasEnabled && isPreviewEnabled) {
                        resetTextureView();
                    }

                    // 4. Surface handling logic OR placeholder animations (also runs AFTER card bounce)
                    if (isRecordingOrPaused()) { // Only update service if recording/paused
                        if (isPreviewEnabled && textureView != null && textureView.isAvailable() && textureViewSurface != null) {
                            Log.d(TAG, "Preview enabled (post-anim): TextureView available, sending surface to service.");
                            updateServiceWithCurrentSurface(textureViewSurface);
                        } else {
                            Log.d(TAG, "Preview enabled (post-anim): TextureView not yet available, will send surface on callback.");
                            updateServiceWithCurrentSurface(null);
                        }
                    } else {
                        Log.d(TAG, "Preview disabled (post-anim): Sending null surface to service.");
                        updateServiceWithCurrentSurface(null);
                    }
                }
            });
            cardBounceAnim.start(); // Start the card bounce animation
            return true;
        });
    }

    private void updatePreviewVisibility() {
        // ----- Fix Start for this method(updatePreviewVisibility)-----
        if (!isAdded() || textureView == null || tvPreviewPlaceholder == null) {
            Log.e(TAG, "updatePreviewVisibility: Fragment not attached or views null");
            return;
        }
        
        if (isRecording()) {
            if (isPreviewEnabled) {
                // Show preview
                textureView.setVisibility(View.VISIBLE);
                tvPreviewPlaceholder.setVisibility(View.GONE);
                Log.d(TAG, "Preview enabled and recording - showing preview");
                
                // Ensure surface is sent to service
                if (textureViewSurface != null && textureViewSurface.isValid() && isRecordingOrPaused()) {
                    updateServiceWithCurrentSurface(textureViewSurface);
                }
            } else {
                // Hide preview
                textureView.setVisibility(View.INVISIBLE);
                tvPreviewPlaceholder.setVisibility(View.VISIBLE);
                tvPreviewPlaceholder.setText("Long press to enable preview");
                Log.d(TAG, "Preview disabled but recording - showing placeholder");
                
                // Send null surface to service
                updateServiceWithCurrentSurface(null);
            }
        } else {
            // Not recording, show placeholder
            textureView.setVisibility(View.INVISIBLE);
            tvPreviewPlaceholder.setVisibility(View.VISIBLE);
            tvPreviewPlaceholder.setText(getString(R.string.ui_preview_area));
            Log.d(TAG, "Not recording - showing placeholder text");
        }
        // ----- Fix Ended for this method(updatePreviewVisibility)-----
    }

    private void resetTimers() {
        recordingStartTime = SystemClock.elapsedRealtime();
        updateStorageInfo();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.init(requireContext());

        Log.d(TAG, "HomeFragment created.");

        // Request essential permissions on every launch
        // requestEssentialPermissions(); // <-- Disabled, handled in onboarding only

        sharedPreferencesManager = SharedPreferencesManager.getInstance(requireContext());

        // Check if it's the first launch
//        boolean isFirstLaunch = sharedPreferences.getBoolean(PREF_FIRST_LAUNCH, true);
//        if (isFirstLaunch) {
//            // Request essential permissions
//            requestEssentialPermissions();
//
//            // Set first launch to false
//            sharedPreferences.edit().putBoolean(PREF_FIRST_LAUNCH, false).apply();
//        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: HomeFragment");
        // Moved receiver registration here from onResume for consistency
        // and to ensure they are ready before any onResume logic might need them.
        registerBroadcastReceivers(); // Centralized registration

        // Initialize SharedPreferencesManager if null
        if (sharedPreferencesManager == null) {
            sharedPreferencesManager = SharedPreferencesManager.getInstance(requireContext());
        }
        // Initialize ExecutorService if null or shutdown
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }

        // ----- Fix Start: Set default camera resource availability -----
        // Set camera resources as available by default when starting
        areCameraResourcesAvailable = true;
        // ----- Fix End: Set default camera resource availability -----

        // Fetch initial state and update UI
        fetchRecordingState(); // Get current service state
        updateStats();         // Update file count/size stats
        updateStorageInfo();   // Update available storage info
        startUpdatingClock();  // Start periodic clock updates
        startUpdatingInfo();   // Start periodic storage/estimate updates
        showCurrentCameraSelection(); // Show selected camera
        // Restore preview state
        isPreviewEnabled = sharedPreferencesManager.sharedPreferences.getBoolean("preview_enabled", true);
        updatePreviewVisibility();

        // ----- Fix Start for this method(onStart) -----
        registerSegmentCompleteStatsReceiver(requireContext());
        // ----- Fix Ended for this method(onStart) -----

        // ----- Fix Start: Remove duplicate registration since it's now in registerBroadcastReceivers -----
        // Camera resource availability registration is now handled in registerBroadcastReceivers()
        // ----- Fix End: Remove duplicate registration -----

        // Ensure we have the latest state
        fetchRecordingState();
    }

    /**
     * Displays a toast message showing the currently selected camera based on shared preferences
     */
    private void showCurrentCameraSelection() {
        CameraType currentCameraType = sharedPreferencesManager.getCameraSelection();
        String currentCameraTypeString = "";
        if (currentCameraType.equals(CameraType.FRONT)) {
            currentCameraTypeString = getString(R.string.front);
        } else if (currentCameraType.equals(CameraType.BACK)) {
            currentCameraTypeString = getString(R.string.back);
        }

//        Toast.makeText(getContext(), this.getString(R.string.current_camera) + ": " + currentCameraTypeString.toLowerCase(), Toast.LENGTH_SHORT).show();
    }

    private void fetchRecordingState()
    {
        Intent startIntent = new Intent(getActivity(), RecordingService.class);
        startIntent.setAction(Constants.BROADCAST_ON_RECORDING_STATE_REQUEST);
        requireActivity().startService(startIntent);
    }

    private void registerBroadcastOnRecordingStateCallback() {
        broadcastOnRecordingStateCallback = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent i)
            {
                RecordingState recordingStateIntent = (RecordingState) i.getSerializableExtra(Constants.INTENT_EXTRA_RECORDING_STATE);
                if (recordingStateIntent == null) {
                    recordingStateIntent = RecordingState.NONE;
                }

                switch(recordingStateIntent) {
                    case NONE:
                        onRecordingStopped();
                        break;
                    case IN_PROGRESS:
                        if(isRecording()) {
                            updateRecordingSurface();
                        } else {
                            onRecordingStarted(false);
                            updateRecordingSurface();
                        }
                        break;
                    case PAUSED:
                        onRecordingPaused();
                        break;
                }

                recordingState = recordingStateIntent;
            }
        };
    }

    private void registerBroadcastOnRecordingStarted() {
        broadcastOnRecordingStarted = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent i) {
                // ----- Fix Start for this method(registerBroadcastOnRecordingStarted) -----
                // Get the timestamp from the intent with current time as fallback
                long startTimeFromService = i.getLongExtra(Constants.INTENT_EXTRA_RECORDING_START_TIME, SystemClock.elapsedRealtime());
                
                // Validate the timestamp - ensure it's not ridicuously old or in the future
                long currentTime = SystemClock.elapsedRealtime();
                
                // Check if the time from service is within a reasonable range
                // (not more than 5 seconds in the past or 1 second in the future)
                if (startTimeFromService < currentTime - 5000 || startTimeFromService > currentTime + 1000) {
                    Log.w(TAG, "Received invalid recordingStartTime from service: " + startTimeFromService 
                          + ", current time: " + currentTime + ". Using current time instead.");
                    startTimeFromService = currentTime;
                }
                
                // Set our recording start time to the validated time from service
                recordingStartTime = startTimeFromService;
                Log.d(TAG, "BROADCAST_ON_RECORDING_STARTED: Set recordingStartTime=" + recordingStartTime);
                // ----- Fix End for this method(registerBroadcastOnRecordingStarted) -----
                
                // Update our internal state first
                onRecordingStarted(true);
                
                // Force a clean surface reset when recording starts to ensure preview works
                if (textureView != null) {
                    // Try to create a new surface immediately if possible
                    if (textureView.getSurfaceTexture() != null) {
                        if (textureViewSurface != null) {
                            textureViewSurface.release();
                        }
                        textureViewSurface = new Surface(textureView.getSurfaceTexture());
                        Log.d(TAG, "BROADCAST_ON_RECORDING_STARTED: Created new surface");
                        updateServiceWithCurrentSurface(textureViewSurface);
                    }
                    
                    // Schedule a secondary attempt with a slight delay as backup
                    handlerClock.postDelayed(() -> {
                        if (isRecording() && isPreviewEnabled) {
                            if (textureView.getSurfaceTexture() != null) {
                                // Only recreate if needed
                                if (textureViewSurface == null || !textureViewSurface.isValid()) {
                                    if (textureViewSurface != null) {
                                        textureViewSurface.release();
                                    }
                                    textureViewSurface = new Surface(textureView.getSurfaceTexture());
                                }
                                updateServiceWithCurrentSurface(textureViewSurface);
                                Log.d(TAG, "BROADCAST_ON_RECORDING_STARTED: Delayed surface creation");
                            } else {
                                Log.d(TAG, "BROADCAST_ON_RECORDING_STARTED: SurfaceTexture still not available after delay");
                            }
                        }
                    }, 200); // Slightly longer delay as a final attempt
                }
            }
        };
    }

    private void registerBroadcastOnRecordingResumed() {
        broadcastOnRecordingResumed = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent i)
            {
                onRecordingResumed();
            }
        };
    }

    private void registerBroadcastOnRecordingPaused() {
        broadcastOnRecordingPaused = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { if(isAdded()) onRecordingPaused(); }
        };
    }

    private void registerBrodcastOnRecordingStopped() {
        broadcastOnRecordingStopped = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent i)
            {
                onRecordingStopped();
            }
        };
    }

    private void onRecordingStarted(boolean toast) {
        Log.d(TAG, "onRecordingStarted. Toast: " + toast);
        
        // ----- Fix Start for this method(onRecordingStarted) -----
        // Reset recording start time to ensure a fresh start - always use current time
        // This fixes cases where old stale timestamps might be causing incorrect elapsed time
        recordingStartTime = SystemClock.elapsedRealtime();
        Log.d(TAG, "onRecordingStarted: RESET recordingStartTime=" + recordingStartTime);
        // ----- Fix End for this method(onRecordingStarted) -----
        
        recordingState = RecordingState.IN_PROGRESS;
        setUIForRecordingActive();
        if(toast) Utils.showQuickToast(requireContext(), R.string.video_recording_started);
        acquireWakeLock(); // Acquire wake lock
        updateStats(); // Update stats when recording starts
        
        // Always start the info update timer to keep elapsed time current
        startUpdatingInfo();
        
        // ----- Fix Start for this method(onRecordingStarted) -----
        // Always force preview enabled on first recording start
        isPreviewEnabled = true;
        savePreviewState();
        updatePreviewVisibility();
        
        // When recording starts, ensure we have a valid surface
        if (textureView != null) {
            // If TextureView has a valid SurfaceTexture, create a Surface from it
            if (textureView.isAvailable() && textureView.getSurfaceTexture() != null) {
                // Release any existing surface to avoid leaks
                if (textureViewSurface != null) {
                    textureViewSurface.release();
                }
                
                // Create a new Surface from the SurfaceTexture
                textureViewSurface = new Surface(textureView.getSurfaceTexture());
                Log.d(TAG, "onRecordingStarted: Created new surface from available TextureView");
                
                // Send the surface to the service
                updateServiceWithCurrentSurface(textureViewSurface);
            } else {
                // If no SurfaceTexture is available, reset the TextureView to trigger creation
                Log.d(TAG, "onRecordingStarted: TextureView not available, forcing a reset");
                resetTextureView();
                
                // Add a delayed retry to create and send the surface
                handlerClock.postDelayed(() -> {
                    if (textureView.getSurfaceTexture() != null) {
                        textureViewSurface = new Surface(textureView.getSurfaceTexture());
                        updateServiceWithCurrentSurface(textureViewSurface);
                        Log.d(TAG, "onRecordingStarted: Created surface after delay");
                    }
                }, 100);
            }
        }
        // ----- Fix Ended for this method(onRecordingStarted) -----
    }

    private void onRecordingResumed() {
        recordingState = RecordingState.IN_PROGRESS;

        buttonPauseResume.setIcon(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_pause));
        buttonPauseResume.setEnabled(true);

        buttonStartStop.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.button_stop)));
        buttonStartStop.setText(getString(R.string.button_stop));
        buttonStartStop.setIcon(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_stop));
        buttonStartStop.setEnabled(true);

        buttonCamSwitch.setEnabled(false);

        startUpdatingInfo();
    }

    private void onRecordingPaused() {
        recordingState = RecordingState.PAUSED;

        buttonPauseResume.setIcon(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_play));
        buttonPauseResume.setEnabled(true);

        buttonCamSwitch.setEnabled(false);

        buttonStartStop.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.button_stop)));
        buttonStartStop.setText(getString(R.string.button_stop));
        buttonStartStop.setIcon(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_stop));
    }

    // --- Receiver for MediaRecorder Stopped signal ---
    /**
     * Called when the BROADCAST_ON_RECORDING_STOPPED is received,
     * indicating the MediaRecorder engine has stopped and hardware resources
     * are likely released or being released immediately by the service.
     * This method resets the UI to the IDLE state.
     */
    private void onRecordingStopped() {
        // ----- Fix Start: Restructure for better state management -----
        Log.d(TAG, "onRecordingStopped broadcast received.");
        
        // First update the recording state
        recordingState = RecordingState.NONE;
        
        // Release wake lock if it was acquired
        releaseWakeLock();
        
        // Reset all buttons to idle state
        try {
            resetUIButtonsToIdleState();
            
            // Handle visual elements for preview and timers
            updatePreviewVisibility();     // Show placeholder text instead of preview
            stopUpdatingInfo();            // Stop updating storage info
            
            Log.d(TAG, "onRecordingStopped: UI reset to IDLE state. Background processing may continue.");
        } catch (Exception e) {
            Log.e(TAG, "Error in onRecordingStopped", e);
        }
        // ----- Fix End: Restructure for better state management -----
    }

    // Inside HomeFragment.java

    /**
     * Safely resets the main control buttons (Start, Pause, CamSwitch, Torch)
     * and related UI elements (like preview) to their default IDLE state.
     * This means recording is stopped and the user can initiate a new one.
     * Should only be called when the fragment is attached and view is available.
     */
    private void resetUIButtonsToIdleState() {
        Log.d(TAG, "Reset UI to idle state");
        if (!isAdded() || getContext() == null || getView() == null) {
            Log.w(TAG, "resetUIButtonsToIdleState: Fragment/context unavailable");
            return;
        }
        try {
            String themeName = sharedPreferencesManager.sharedPreferences.getString(com.fadcam.Constants.PREF_APP_THEME, Constants.DEFAULT_APP_THEME);
            boolean isAmoledLocal = "AMOLED".equalsIgnoreCase(themeName) || "Amoled".equalsIgnoreCase(themeName) || "Faded Night".equalsIgnoreCase(themeName);
            if (buttonStartStop != null) {
                buttonStartStop.setText(R.string.button_start);
                buttonStartStop.setIcon(AppCompatResources.getDrawable(getContext(), R.drawable.ic_play));
                // Always use green color for start button regardless of theme
                int btnColor = Color.parseColor("#4CAF50"); // Always green
                buttonStartStop.setBackgroundTintList(ColorStateList.valueOf(btnColor));
            }
            if (buttonPauseResume != null) { 
                buttonPauseResume.setVisibility(View.VISIBLE);
                buttonPauseResume.setEnabled(false);
                buttonPauseResume.setAlpha(0.5f);
                buttonPauseResume.setIcon(AppCompatResources.getDrawable(getContext(), R.drawable.ic_pause));
            }
            if (buttonCamSwitch != null) {
                buttonCamSwitch.setEnabled(true);
                buttonCamSwitch.setVisibility(View.VISIBLE);
                buttonCamSwitch.setAlpha(1f);
            }

            updateStartButtonAvailability();
            Log.d(TAG, "resetUIButtonsToIdleState: All UI elements reset to idle state");
        } catch (Exception e) {
            Log.e(TAG, "Error in resetUIButtonsToIdleState", e);
        }
    }
    
    /**
     * Updates the start button state based on camera resource availability
     */
    private void updateStartButtonAvailability() {
        if (!isAdded() || buttonStartStop == null) {
            return;
        }
        
        // Only update if we're in a state where the start button would normally be enabled
        if (recordingState == RecordingState.NONE) {
            boolean shouldEnable = areCameraResourcesAvailable;
            buttonStartStop.setEnabled(shouldEnable);
            buttonStartStop.setAlpha(shouldEnable ? 1.0f : 0.5f);
            
            // Always maintain green color even when disabled
            buttonStartStop.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            
            if (!shouldEnable) {
                Log.d(TAG, "Start button disabled due to camera resources being released");
            } else {
                Log.d(TAG, "Start button enabled as camera resources are available");
            }
        }
    }

    /** Helper for resetUIButtonsToIdleState to check flash without throwing checked exception */
    private String getCameraWithFlashQuietly() {
        // Ensure cameraManager is initialized (e.g., in onViewCreated or onAttach)
        if(cameraManager == null) {
            try {
                cameraManager = (CameraManager) requireContext().getSystemService(Context.CAMERA_SERVICE);
            } catch (Exception e) {
                Log.e(TAG,"Failed to get CameraManager in getCameraWithFlashQuietly", e);
                return null;
            }
        }
        if(cameraManager == null) return null; // Check again if getSystemService failed

        try {
            // Assuming getCameraWithFlash is defined elsewhere and throws CameraAccessException
            return getCameraWithFlash();
        } catch (CameraAccessException e){
            Log.w(TAG,"CameraAccessException checking flash quietly: " + e.getMessage()); // Changed to warning
            return null;
        } catch (Exception e) {
            Log.e(TAG,"Unexpected error checking flash quietly", e);
            return null;
        }
    }



    @Override
    public void onStop() {
        super.onStop();
        // unregisterStatsReceiver(); // Unregister receiver when fragment stops

        Log.e(TAG, "HomeFragment stopped");

        // ----- Fix Start for this method(onStop)-----
        // Call the centralized unregister method
        unregisterBroadcastReceivers(); 

        // The following lines for sending surface update on stop if recording
        if(isRecording()) { // isRecording() checks recordingState
            Intent recordingIntent = new Intent(getActivity(), RecordingService.class);
            recordingIntent.setAction(Constants.INTENT_ACTION_CHANGE_SURFACE);
            // Check if activity is still available before starting service
            if (getActivity() != null) {
                requireActivity().startService(recordingIntent);
            }
        }
        // ----- Fix Ended for this method(onStop)-----

        // ----- Fix Start for unregistering camera resource availability receiver -----
        unregisterCameraResourceAvailabilityReceiver();
        // ----- Fix End for unregistering camera resource availability receiver -----

        stopUpdatingInfo();
    }

    // --- `onResume()` Method (Simplified - focuses on fetch state) ---
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "HomeFragment resumed.");
        if (!isAdded() || getContext() == null || getActivity() == null) { Log.e(TAG,"onResume: Not attached!"); return; }
        if (sharedPreferencesManager == null) { sharedPreferencesManager = SharedPreferencesManager.getInstance(requireContext()); }

        Log.d(TAG, "onResume: Fetching current recording state from service...");
        fetchRecordingState(); // Let service callback handle UI sync

        registerBroadcastReceivers(); // Centralized registration
        
        // ----- Fix Start for this method(onResume)-----
        // Re-load preview state from SharedPreferences to ensure consistency
        isPreviewEnabled = sharedPreferencesManager.isPreviewEnabled();
        Log.d(TAG, "onResume: Loaded isPreviewEnabled state = " + isPreviewEnabled);
        
        // Update the preview visibility based on current state
        updatePreviewVisibility();
        
        // Critical: When resuming, send the appropriate surface to the service
        // This ensures preview shows correctly after app is minimized/restored
        if (isPreviewEnabled && isRecordingOrPaused() && textureViewSurface != null && textureViewSurface.isValid()) {
            Log.d(TAG, "onResume: Preview enabled, sending valid surface to service");
            updateServiceWithCurrentSurface(textureViewSurface);
        } else if (!isPreviewEnabled || !isRecordingOrPaused()) {
            // If preview is disabled or not recording, send null surface
            Log.d(TAG, "onResume: Preview disabled or not recording, sending null surface");
            updateServiceWithCurrentSurface(null);
        }
        // ----- Fix Ended for this method(onResume)-----

        Log.d(TAG, "onResume: Triggering stats update.");
        updateStats();
    }

    // Inside HomeFragment.java

    // --- Receiver Field Declarations (Should already exist near top of HomeFragment) ---
    // private BroadcastReceiver broadcastOnRecordingStarted;
    // private BroadcastReceiver broadcastOnRecordingResumed;
    // private BroadcastReceiver broadcastOnRecordingPaused;
    // private BroadcastReceiver broadcastOnRecordingStopped; // Handles UI idle reset now
    // private BroadcastReceiver broadcastOnRecordingStateCallback; // Handles initial sync
    // private BroadcastReceiver recordingCompleteReceiver; // Handles stats update after processing
    // private BroadcastReceiver torchReceiver;

    // --- Registration Flags (Optional but recommended for robust unregistering) ---
    private boolean isStateReceiversRegistered = false;
    private boolean isCompletionReceiverRegistered = false; // Renamed from isStatsReceiverRegistered
    private boolean isTorchReceiverRegistered = false;


    // --- MAIN Registration Method ---
    /**
     * Centralized method to register all necessary BroadcastReceivers for this fragment.
     * Ensures initialization and calls individual registration helpers.
     * Should be called from onResume or onStart.
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag") // Suppress only if targeting older SDKs AND necessary
    private void registerBroadcastReceivers() {
        Context context = requireContext();
        if (context == null) {
            Log.e(TAG, "registerBroadcastReceivers: Context is null, cannot register.");
            return;
        }
        Log.d(TAG,"Registering all HomeFragment broadcast receivers...");

        // Initialize if they are null (first time or after unregistration)
        initializeRecordingStateReceivers(); // Initializes all state-related receivers
        initializeRecordingCompleteReceiver();
        initializeTorchReceiver();
        // ----- Fix Start for this method(registerBroadcastReceivers) -----
        initializeSegmentCompleteStatsReceiver();
        // ----- Fix Ended for this method(registerBroadcastReceivers) -----

        // ----- Fix Start: Also initialize camera resource availability receiver -----
        initializeCameraResourceAvailabilityReceiver();
        // ----- Fix End: Also initialize camera resource availability receiver -----

        // Register them
        // ----- Fix Start for this method(registerBroadcastReceivers_update_isStateReceiversRegistered_flag_logic)-----
        // registerRecordingStateReceivers now returns a boolean indicating success
        isStateReceiversRegistered = registerRecordingStateReceivers(context); 
        // ----- Fix Ended for this method(registerBroadcastReceivers_update_isStateReceiversRegistered_flag_logic)-----

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerRecordingCompleteReceiver(context);
        }
        registerTorchReceiver(context);
        // ----- Fix Start for this method(registerBroadcastReceivers) -----
        registerSegmentCompleteStatsReceiver(context); // Register the new one
        // ----- Fix Ended for this method(registerBroadcastReceivers) -----

        // ----- Fix Start: Also register the camera resource availability receiver -----
        registerCameraResourceAvailabilityReceiver();
        // ----- Fix End: Also register the camera resource availability receiver -----

        // ----- Fix Start for this method(registerBroadcastReceivers_update_isStateReceiversRegistered_flag_logic)-----
        // isStateReceiversRegistered = true; // Assuming registerRecordingStateReceivers sets this -> Moved up and tied to actual success
        // ----- Fix Ended for this method(registerBroadcastReceivers_update_isStateReceiversRegistered_flag_logic)-----
        // isCompletionReceiverRegistered is managed by registerRecordingCompleteReceiver
        // isTorchReceiverRegistered is managed by registerTorchReceiver
        Log.i(TAG,"All HomeFragment broadcast receivers registration attempt finished.");
    }

    // --- Initialization Helper Methods ---

    /** Initializes the BroadcastReceiver instances for recording state changes */
    private void initializeRecordingStateReceivers() {
        // Initialize Receiver for START action
        if (broadcastOnRecordingStarted == null) {
            broadcastOnRecordingStarted = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent i) {
                    if (!isAdded() || i == null) return;
                    Log.d(TAG, "Received BROADCAST_ON_RECORDING_STARTED (New Handler)");
                    
                    // Get timestamp from the service with current time as fallback
                    long startTimeFromService = i.getLongExtra(Constants.INTENT_EXTRA_RECORDING_START_TIME, SystemClock.elapsedRealtime());
                    recordingStartTime = startTimeFromService;
                    Log.d(TAG, "initializeRecordingStateReceivers: Setting recordingStartTime=" + recordingStartTime);
                    
                    // Perform non-UI actions previously in onRecordingStarted(true)
                    acquireWakeLock();
                    setVideoBitrate();
                    
                    // Call the main UI state updater
                    handleServiceStateUpdate(RecordingState.IN_PROGRESS); 

                    // Handle the toast
                    if(isAdded() && getContext() != null) { 
                       vibrateTouch();
                       Toast.makeText(getContext(), R.string.video_recording_started, Toast.LENGTH_SHORT).show();
                    }
                }
            };
            Log.d(TAG,"Initialized broadcastOnRecordingStarted receiver (New Handler)");
        }
        // Initialize Receiver for RESUME action
        if (broadcastOnRecordingResumed == null) {
            broadcastOnRecordingResumed = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { if(isAdded()) onRecordingResumed(); }
            };
            Log.d(TAG,"Initialized broadcastOnRecordingResumed receiver");
        }
        // Initialize Receiver for PAUSE action
        if (broadcastOnRecordingPaused == null) {
            broadcastOnRecordingPaused = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) { if(isAdded()) onRecordingPaused(); }
            };
            Log.d(TAG,"Initialized broadcastOnRecordingPaused receiver");
        }
        // Initialize Receiver for STOPPED action (triggers UI Idle)
        if (broadcastOnRecordingStopped == null) {
            broadcastOnRecordingStopped = new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent i) { if(isAdded()) onRecordingStopped(); }
            };
            Log.d(TAG,"Initialized broadcastOnRecordingStopped receiver");
        }
        // Initialize Receiver for SERVICE STATE CALLBACK
        if (broadcastOnRecordingStateCallback == null) {
            broadcastOnRecordingStateCallback = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent i) {
                    if (!isAdded() || i == null) return;
                    // Get the state reported by the service
                    RecordingState serviceState = (RecordingState) i.getSerializableExtra(Constants.INTENT_EXTRA_RECORDING_STATE);
                    Log.i(TAG, "Received Service State Callback: " + serviceState);
                    if (serviceState == null) serviceState = RecordingState.NONE; // Default to NONE

                    // *** CALL the handler method ***
                    handleServiceStateUpdate(serviceState);
                }
            };
            Log.d(TAG,"Initialized broadcastOnRecordingStateCallback receiver");
        }
    }



    /**
     * **Definition:** Updates the HomeFragment UI based on the definitive state
     * reported by the RecordingService's state callback.
     * @param reportedState The RecordingState received from the service.
     */
    private void handleServiceStateUpdate(RecordingState reportedState) {
        if (!isAdded()) { // Check if fragment is attached
            Log.w(TAG, "handleServiceStateUpdate: Fragment not attached, ignoring state update: " + reportedState);
            return;
        }
        Log.i(TAG, "handleServiceStateUpdate: Applying UI for Service State = " + reportedState);

        // Update the local recording state variable
        recordingState = reportedState;

        // Update UI elements based on the state
        switch (reportedState) {
            case IN_PROGRESS:
                setUIForRecordingActive(); // Call helper to set Stop/Pause buttons etc.
                break;
            case PAUSED:
                setUIForRecordingPaused(); // Call helper to set Stop/Resume buttons etc.
                break;
            case NONE:
            default:
                // Service state is NONE. Recording is stopped.
                // UI *should* be idle unless background processing is happening
                // for a *previous* video. Reset UI directly here as this confirms
                // the *current* recording attempt is definitely stopped.
                Log.d(TAG, "handleServiceStateUpdate: Service state is NONE. Resetting UI to idle.");
                resetUIButtonsToIdleState();
                break;
        }
        Log.d(TAG, "handleServiceStateUpdate finished. Fragment state is now: " + recordingState);
    }

    /** Helper to set UI elements for the ACTIVE recording state */
    private void setUIForRecordingActive() {
        if(!isAdded() || getContext() == null) return;
        Log.d(TAG,"Setting UI to: ACTIVE Recording");
        try{
            // Ensure interaction buttons reflect recording
            buttonStartStop.setEnabled(true); // Enable STOP
            buttonStartStop.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.button_stop));
            buttonStartStop.setText(getString(R.string.button_stop));
            buttonStartStop.setIcon(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_stop));

            buttonPauseResume.setEnabled(true); // Enable PAUSE
            buttonPauseResume.setIcon(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_pause));
            buttonPauseResume.setAlpha(1.0f); // Make fully visible when enabled
            buttonPauseResume.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.button_pause));

            buttonCamSwitch.setEnabled(false); // Disable CAM SWITCH

            // Manage preview and timers
            updatePreviewVisibility(); startUpdatingInfo();
        } catch(Exception e){ Log.e(TAG,"Error setting UI for Active state", e); }
    }

    /** Helper to set UI elements for the PAUSED recording state */
    private void setUIForRecordingPaused() {
        if(!isAdded() || getContext() == null) return;
        Log.d(TAG,"Setting UI to: PAUSED Recording");
        try{
            // Set buttons for Paused state (Stop ON, Resume(Play) ON, Switch OFF, Torch OFF)
            buttonStartStop.setEnabled(true); // Enable STOP
            buttonStartStop.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.button_stop));
            buttonStartStop.setText(getString(R.string.button_stop));
            buttonStartStop.setIcon(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_stop));

            buttonPauseResume.setEnabled(true); // Enable RESUME
            buttonPauseResume.setIcon(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_play)); // Show Play icon for RESUME
            buttonPauseResume.setAlpha(1.0f); // Make fully visible when enabled
            buttonPauseResume.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.button_pause));

            buttonCamSwitch.setEnabled(false); // Disable CAM SWITCH
            // ----- Fix Start for this method(setUIForRecordingPaused_torchButton)-----
            // ----- Fix Ended for this method(setUIForRecordingPaused_torchButton)-----

            // Manage preview and timers
            updatePreviewVisibility(); stopUpdatingInfo(); // Show placeholder/last frame, stop timers
        } catch(Exception e){ Log.e(TAG,"Error setting UI for Paused state", e); }
    }

    /** Initializes the BroadcastReceiver for ACTION_RECORDING_COMPLETE */
    private void initializeRecordingCompleteReceiver() {
        if (recordingCompleteReceiver == null) {
            recordingCompleteReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!isAdded() || intent == null || intent.getAction() == null) return;
                    if (Constants.ACTION_RECORDING_COMPLETE.equals(intent.getAction())) {
                        Log.i(TAG, "<<< Received ACTION_RECORDING_COMPLETE (Processing Finished) >>>");
                        if(getView() == null) { Log.w(TAG,"Completion: View null, skip stats UI"); return; }
                        try { updateStats(); Log.d(TAG,"Completion: Updated stats."); }
                        catch (Exception e) { Log.e(TAG, "Completion: Err update stats", e);}
                    }
                }
            };
            Log.d(TAG,"Initialized recordingCompleteReceiver");
        }
    }

    /** Initializes the BroadcastReceiver for Torch State Changes */
    private void initializeTorchReceiver() {
        if (torchReceiver == null) {
            torchReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!isAdded() || getActivity()==null || intent == null || intent.getAction() == null) return;
                    if (Constants.BROADCAST_ON_TORCH_STATE_CHANGED.equals(intent.getAction())) {
                        isTorchOn = intent.getBooleanExtra(Constants.INTENT_EXTRA_TORCH_STATE, false);
                        Log.d("TorchDebug", "Received state update via Broadcast: " + isTorchOn);
                    }
                }
            };
            Log.d(TAG,"Initialized torchReceiver");
        }
    }

    // --- Registration Helper Methods ---

    /** Helper to register all recording state change receivers */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    // ----- Fix Start for this method(registerRecordingStateReceivers_correct_signature_and_logic)-----
    private boolean registerRecordingStateReceivers(Context context){ // Ensure boolean return type
    // ----- Fix Ended for this method(registerRecordingStateReceivers_correct_signature_and_logic)-----
        Log.d(TAG, "Registering recording state receivers...");
        if (context == null) {
            Log.e(TAG, "Context is null in registerRecordingStateReceivers");
            // ----- Fix Start for this method(registerRecordingStateReceivers_return_boolean_and_check_receivers)-----
            isStateReceiversRegistered = false;
            return false;
            // ----- Fix Ended for this method(registerRecordingStateReceivers_return_boolean_and_check_receivers)-----
        }

        // Ensure receivers are initialized
        initializeRecordingStateReceivers();

        // ----- Fix Start for this method(registerRecordingStateReceivers_return_boolean_and_check_receivers)-----
        boolean allRegisteredSuccessfully = true;
        IntentFilter intentFilterStarted = new IntentFilter(Constants.BROADCAST_ON_RECORDING_STARTED);
        if (broadcastOnRecordingStarted != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastOnRecordingStarted, intentFilterStarted, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(broadcastOnRecordingStarted, intentFilterStarted);
            }
            Log.d(TAG,"Registered broadcastOnRecordingStarted");
        } else { allRegisteredSuccessfully = false; Log.e(TAG, "broadcastOnRecordingStarted is null, not registering"); }

        IntentFilter intentFilterResumed = new IntentFilter(Constants.BROADCAST_ON_RECORDING_RESUMED);
        if (broadcastOnRecordingResumed != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastOnRecordingResumed, intentFilterResumed, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(broadcastOnRecordingResumed, intentFilterResumed);
            }
            Log.d(TAG,"Registered broadcastOnRecordingResumed");
        } else { allRegisteredSuccessfully = false; Log.e(TAG, "broadcastOnRecordingResumed is null, not registering"); }

        IntentFilter intentFilterPaused = new IntentFilter(Constants.BROADCAST_ON_RECORDING_PAUSED);
        if (broadcastOnRecordingPaused != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastOnRecordingPaused, intentFilterPaused, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(broadcastOnRecordingPaused, intentFilterPaused);
            }
            Log.d(TAG,"Registered broadcastOnRecordingPaused");
        } else { allRegisteredSuccessfully = false; Log.e(TAG, "broadcastOnRecordingPaused is null, not registering"); }

        IntentFilter intentFilterStopped = new IntentFilter(Constants.BROADCAST_ON_RECORDING_STOPPED);
        if (broadcastOnRecordingStopped != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastOnRecordingStopped, intentFilterStopped, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(broadcastOnRecordingStopped, intentFilterStopped);
            }
            Log.d(TAG,"Registered broadcastOnRecordingStopped");
        } else { allRegisteredSuccessfully = false; Log.e(TAG, "broadcastOnRecordingStopped is null, not registering"); }

        IntentFilter intentFilterStateCallback = new IntentFilter(Constants.BROADCAST_ON_RECORDING_STATE_CALLBACK);
        if (broadcastOnRecordingStateCallback != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(broadcastOnRecordingStateCallback, intentFilterStateCallback, Context.RECEIVER_EXPORTED);
            } else {
                context.registerReceiver(broadcastOnRecordingStateCallback, intentFilterStateCallback);
            }
            Log.d(TAG,"Registered broadcastOnRecordingStateCallback");
        } else { allRegisteredSuccessfully = false; Log.e(TAG, "broadcastOnRecordingStateCallback is null, not registering"); }

        isStateReceiversRegistered = allRegisteredSuccessfully;
        if(allRegisteredSuccessfully){
            Log.i(TAG, "All recording state receivers registered successfully.");
        } else {
            Log.w(TAG, "One or more recording state receivers failed to register because they were null.");
        }
        return allRegisteredSuccessfully;
        // ----- Fix Ended for this method(registerRecordingStateReceivers_return_boolean_and_check_receivers)-----
    }

    /** Helper to register the ACTION_RECORDING_COMPLETE receiver */
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerRecordingCompleteReceiver(Context context) {
        if (!isCompletionReceiverRegistered && context != null && recordingCompleteReceiver != null) {
            context.registerReceiver(recordingCompleteReceiver, new IntentFilter(Constants.ACTION_RECORDING_COMPLETE), Context.RECEIVER_EXPORTED);
            isCompletionReceiverRegistered = true; // isCompletionReceiverRegistered is the correct flag here
            Log.d(TAG, "ACTION_RECORDING_COMPLETE receiver registered.");
        }
    }

    /** Helper to register the Torch receiver */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerTorchReceiver(Context context) {
        if (isTorchReceiverRegistered) return;
        if (torchReceiver == null) {
            initializeTorchReceiver();
            if (torchReceiver == null) {Log.e(TAG,"Cannot register: Failed init torch receiver"); return;}
        }
        IntentFilter filter = new IntentFilter(Constants.BROADCAST_ON_TORCH_STATE_CHANGED);
        try{
            // ----- Fix Start for this method(registerTorchReceiver_add_export_flag_for_Tiramisu)-----
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(torchReceiver, filter, Context.RECEIVER_EXPORTED); // Or RECEIVER_NOT_EXPORTED if purely internal
            } else {
                context.registerReceiver(torchReceiver, filter);
            }
            // ContextCompat.registerReceiver(context, torchReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
            // ----- Fix Ended for this method(registerTorchReceiver_add_export_flag_for_Tiramisu)-----
            isTorchReceiverRegistered = true; // Use specific flag
            Log.d(TAG,"Torch receiver registered.");
        } catch (Exception e) { Log.e(TAG, "Error registering Torch Receiver", e); isTorchReceiverRegistered = false;}
    }

    // --- Ensure Unregistration Logic ---
    // Place this method in HomeFragment.java and call it from onStop()
    private void unregisterBroadcastReceivers() {
        Context context = getContext(); // Use getContext() for fragment lifecycle safety
        if (context == null) {
            Log.w(TAG, "unregisterBroadcastReceivers: Context is null, cannot unregister.");
            return;
        }
        Log.d(TAG,"Unregistering all HomeFragment broadcast receivers if registered...");

        // ----- Fix Start for this method(unregisterBroadcastReceivers_check_flags)-----
        if (isStateReceiversRegistered) {
            try {
                if (broadcastOnRecordingStarted != null) context.unregisterReceiver(broadcastOnRecordingStarted);
                if (broadcastOnRecordingResumed != null) context.unregisterReceiver(broadcastOnRecordingResumed);
                if (broadcastOnRecordingPaused != null) context.unregisterReceiver(broadcastOnRecordingPaused);
                if (broadcastOnRecordingStopped != null) context.unregisterReceiver(broadcastOnRecordingStopped);
                if (broadcastOnRecordingStateCallback != null) context.unregisterReceiver(broadcastOnRecordingStateCallback);
                Log.i(TAG, "Unregistered recording state receivers.");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Error unregistering state receivers (already unregistered?): " + e.getMessage());
            }
            isStateReceiversRegistered = false;
        } else {
            Log.d(TAG, "Recording state receivers were not registered, skipping unregistration.");
        }

        if (isCompletionReceiverRegistered) {
            try {
                if (recordingCompleteReceiver != null) context.unregisterReceiver(recordingCompleteReceiver);
                Log.i(TAG, "Unregistered recordingCompleteReceiver.");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Error unregistering recordingCompleteReceiver: " + e.getMessage());
            }
            isCompletionReceiverRegistered = false;
        }

        if (isTorchReceiverRegistered) {
            try {
                if (torchReceiver != null) context.unregisterReceiver(torchReceiver);
                Log.i(TAG, "Unregistered torchReceiver.");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Error unregistering torchReceiver: " + e.getMessage());
            }
            isTorchReceiverRegistered = false;
        }

        if (isSegmentCompleteStatsReceiverRegistered) {
            try {
                if (segmentCompleteStatsReceiver != null) context.unregisterReceiver(segmentCompleteStatsReceiver);
                Log.i(TAG, "Unregistered segmentCompleteStatsReceiver.");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Error unregistering segmentCompleteStatsReceiver: " + e.getMessage());
            }
            isSegmentCompleteStatsReceiverRegistered = false;
        }
        // ----- Fix Ended for this method(unregisterBroadcastReceivers_check_flags)-----
        Log.i(TAG,"All HomeFragment broadcast receivers unregistration attempt finished.");
    }

    @Override
    public void onPause() {
        super.onPause();
        //locationHelper.stopLocationUpdates();
        Log.d(TAG, "HomeFragment paused.");

        // ----- Fix Start for this method(onPause)-----
        // When pausing, explicitly release the surface reference to avoid stale frames on resume
        if (textureViewSurface != null) {
            Log.d(TAG, "onPause: Explicitly sending null surface to service");
            updateServiceWithCurrentSurface(null);
        }
        // ----- Fix Ended for this method(onPause)-----
        
        // Only unregister if receiver exists
        if (torchReceiver != null) {
            try {
                requireContext().unregisterReceiver(torchReceiver);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Receiver was not registered: " + e.getMessage());
            }
        }
    }

    // ----- Fix Start for this method(resetTextureView)-----
    /**
     * Helper method to reset the TextureView when needed to avoid showing stale frames
     * This should be called when the preview state changes, especially from disabled to enabled
     */
    private void resetTextureView() {
        if (textureView == null) {
            Log.w(TAG, "resetTextureView: TextureView is null, can't reset");
            return;
        }
        
        Log.d(TAG, "resetTextureView: Attempting to reset TextureView");
        
        // First release any existing surface
        if (textureViewSurface != null) {
            textureViewSurface.release();
            textureViewSurface = null;
            Log.d(TAG, "resetTextureView: Released existing surface");
        }
        
        // If the TextureView is available, recreate the surface
        if (textureView.isAvailable()) {
            SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
            if (surfaceTexture != null) {
                textureViewSurface = new Surface(surfaceTexture);
                Log.d(TAG, "resetTextureView: Created new surface from existing SurfaceTexture");
                
                // If recording and preview enabled, update service with new surface
                if (isPreviewEnabled && isRecordingOrPaused()) {
                    updateServiceWithCurrentSurface(textureViewSurface);
                    Log.d(TAG, "resetTextureView: Updated service with new surface");
                }
            }
        } else {
            Log.d(TAG, "resetTextureView: TextureView not available, can't create surface yet");
        }
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Debug recording time issue
        debugRecordingTimeVariables();
        
        return inflater.inflate(R.layout.fragment_home, container, false);
    }
    
    // Debug method to help diagnose recording time issue
    private void debugRecordingTimeVariables() {
        Log.d(TAG, "======== DEBUG RECORDING TIME ========");
        Log.d(TAG, "recordingStartTime = " + recordingStartTime);
        Log.d(TAG, "currentTimeMillis = " + System.currentTimeMillis());
        Log.d(TAG, "elapsedRealtime = " + SystemClock.elapsedRealtime());
        Log.d(TAG, "recordingState = " + recordingState);
        Log.d(TAG, "isRecording() = " + isRecording());
        Log.d(TAG, "isPaused() = " + isPaused());
        Log.d(TAG, "======== END DEBUG INFO ========");
    }

    private void performHapticFeedback() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(50);
            }
        }
    }

    private void savePreviewState() {
        // ----- Fix Start for this method(savePreviewState)-----
        // Use the SharedPreferencesManager's method which uses the correct constant
        sharedPreferencesManager.setPreviewEnabled(isPreviewEnabled);
        Log.d(TAG, "Preview state saved: " + isPreviewEnabled);
        // ----- Fix Ended for this method(savePreviewState)-----
    }

    //    function to use haptic feedbacks
    private void vibrateTouch() {
        // Haptic Feedback
        Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null && vibrator.hasVibrator()) {
            VibrationEffect effect = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                effect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(effect);
            }
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        com.fadcam.Log.i(TAG, "onViewCreated: method entered");

        // Initialize SharedPreferencesManager
        sharedPreferencesManager = SharedPreferencesManager.getInstance(requireContext());

        // Initialize isAmoledTheme at the top of the method for use throughout
        String currentTheme = sharedPreferencesManager.sharedPreferences.getString(com.fadcam.Constants.PREF_APP_THEME, Constants.DEFAULT_APP_THEME);
        boolean isAmoledTheme = currentTheme != null && 
                               (currentTheme.equalsIgnoreCase("AMOLED") || 
                                currentTheme.equalsIgnoreCase("Amoled") ||
                                currentTheme.equalsIgnoreCase("Faded Night"));
        
        // ----- Fix Start: Reset clock color if theme changed (always use theme default) -----
        String lastTheme = sharedPreferencesManager.sharedPreferences.getString("last_theme_for_clock_color", null);
        
        com.fadcam.Log.i(TAG, "Theme check - Current theme: [" + currentTheme + "], Last theme: [" + lastTheme + "]");
        
        // Simple theme change detection
        if (!Objects.equals(currentTheme, lastTheme)) {
            // Theme changed - get appropriate color from SharedPreferencesManager 
            // (it handles AMOLED theme special case now)
            String clockColorPref = sharedPreferencesManager.getClockCardColor();
            
            // Apply the color to the clock card

            // Save current theme as last theme
            sharedPreferencesManager.sharedPreferences.edit()
                .putString("last_theme_for_clock_color", currentTheme)
                .apply();
            
            // Remove the toast that shows theme changed
            com.fadcam.Log.i(TAG, "Theme changed from [" + (lastTheme != null ? lastTheme : "null") + 
                           "] to [" + currentTheme + "]. Applied color: " + clockColorPref);
        } else {
            // No theme change - just apply the current color preference
            String clockColorPref = sharedPreferencesManager.getClockCardColor();
            com.fadcam.Log.i(TAG, "Applied saved clock card color: " + clockColorPref + " for theme: " + currentTheme);
        }
        // ----- Fix End: Reset clock color if theme changed (always use theme default) -----

        // Initialize ExecutorService
        if (executorService == null || executorService.isShutdown()) {
            executorService = Executors.newSingleThreadExecutor();
        }

        initializeViews(view);
        setupTextureView(view);
        setupButtonListeners();
        setupLongPressListener(); // For Easter eggs on title
        //setupAppLogoLongPressListener(view); // <<< CALL NEW METHOD
        
        // Initialize easter egg messages and setup listener for preview placeholder
        initializeMessages();

        // ----- Fix Start: Apply dynamic theme colors to preview area cards -----
        CardView cardPreview = view.findViewById(R.id.cardPreview);

        // Clock card is intentionally NOT included here as it has its own color logic

        String themeName = sharedPreferencesManager.sharedPreferences.getString(com.fadcam.Constants.PREF_APP_THEME, Constants.DEFAULT_APP_THEME);

        int colorDialog = resolveThemeColor(R.attr.colorDialog);
        int colorButton = resolveThemeColor(R.attr.colorButton);
        int colorTransparent = android.graphics.Color.TRANSPARENT;
        int colorTextPrimary = resolveThemeColor(R.attr.colorHeading);
        int colorTextSecondary = ContextCompat.getColor(requireContext(), R.color.gray_text_light);

        // ----- Fix Start: Apply dynamic theme colors to preview area cards (force override for AMOLED and Red, use *_surface_dark) -----
        if ("Crimson Bloom".equals(themeName)) {
            int redSurface = ContextCompat.getColor(requireContext(), R.color.red_theme_surface_dark);
            int redHeading = ContextCompat.getColor(requireContext(), R.color.red_theme_heading);
            int redTextSecondary = ContextCompat.getColor(requireContext(), R.color.red_theme_text_secondary_dark);
            if (cardPreview != null) cardPreview.setCardBackgroundColor(redSurface);

            setTextColorsRecursive(cardPreview, redHeading, redTextSecondary);

        } else if ("Premium Gold".equals(themeName)) {
            int goldSurface = ContextCompat.getColor(requireContext(), R.color.gold_theme_surface_dark);
            int goldHeading = ContextCompat.getColor(requireContext(), R.color.gold_theme_heading);
            int goldTextSecondary = ContextCompat.getColor(requireContext(), R.color.gold_theme_text_secondary_dark);
            if (cardPreview != null) cardPreview.setCardBackgroundColor(goldSurface);

            setTextColorsRecursive(cardPreview, goldHeading, goldTextSecondary);

        } else if ("Silent Forest".equals(themeName)) {
            // Silent Forest theme (green/teal)
            int forestSurface = ContextCompat.getColor(requireContext(), R.color.silentforest_theme_surface_dark);
            int forestHeading = ContextCompat.getColor(requireContext(), R.color.silentforest_theme_heading);
            int forestTextSecondary = ContextCompat.getColor(requireContext(), R.color.silentforest_theme_text_secondary_dark);
            if (cardPreview != null) cardPreview.setCardBackgroundColor(forestSurface);

            setTextColorsRecursive(cardPreview, forestHeading, forestTextSecondary);

        } else if ("Shadow Alloy".equals(themeName)) {
            // Shadow Alloy theme (silver/metallic)
            int alloySurface = ContextCompat.getColor(requireContext(), R.color.shadowalloy_theme_surface_dark);
            int alloyHeading = ContextCompat.getColor(requireContext(), R.color.shadowalloy_theme_heading);
            int alloyTextSecondary = ContextCompat.getColor(requireContext(), R.color.shadowalloy_theme_text_secondary_dark);
            if (cardPreview != null) cardPreview.setCardBackgroundColor(alloySurface);

            setTextColorsRecursive(cardPreview, alloyHeading, alloyTextSecondary);

        } else if ("Pookie Pink".equals(themeName)) {
            // Pookie Pink theme (pink)
            int pinkSurface = ContextCompat.getColor(requireContext(), R.color.pookiepink_theme_surface_dark);
            int pinkHeading = ContextCompat.getColor(requireContext(), R.color.pookiepink_theme_heading);
            int pinkTextSecondary = ContextCompat.getColor(requireContext(), R.color.pookiepink_theme_text_secondary_dark);
            if (cardPreview != null) cardPreview.setCardBackgroundColor(pinkSurface);

            setTextColorsRecursive(cardPreview, pinkHeading, pinkTextSecondary);

        } else if ("Snow Veil".equals(themeName)) {
            // Snow Veil theme (white/light)
            // ----- Fix Start: Use darker gray for preview area in Snow Veil theme -----
            int snowSurface = ContextCompat.getColor(requireContext(), R.color.snowveil_theme_preview_area); // Darker gray for preview
            // ----- Fix End: Use darker gray for preview area in Snow Veil theme -----
            int snowHeading = ContextCompat.getColor(requireContext(), R.color.snowveil_theme_text_primary);
            int snowTextSecondary = ContextCompat.getColor(requireContext(), R.color.snowveil_theme_text_secondary);
            if (cardPreview != null) cardPreview.setCardBackgroundColor(snowSurface);

            setTextColorsRecursive(cardPreview, snowHeading, snowTextSecondary);
            
            // Apply additional contrast improvements for the Snow Veil theme
        } else if (isAmoledTheme || "Faded Night".equals(themeName)) {
            int amoledSurface = ContextCompat.getColor(requireContext(), R.color.amoled_surface_dark);
            int amoledHeading = ContextCompat.getColor(requireContext(), R.color.amoled_heading);
            int amoledTextSecondary = ContextCompat.getColor(requireContext(), R.color.amoled_text_secondary_dark);
            if (cardPreview != null) cardPreview.setCardBackgroundColor(amoledSurface);

            setTextColorsRecursive(cardPreview, amoledHeading, amoledTextSecondary);

        } else if ("Midnight Dusk".equals(themeName)) {
            int darkSurface = ContextCompat.getColor(requireContext(), R.color.dark_purple_bar);
            int darkHeading = ContextCompat.getColor(requireContext(), R.color.colorHeading);
            int darkTextSecondary = ContextCompat.getColor(requireContext(), R.color.gray_text_light);
            if (cardPreview != null) cardPreview.setCardBackgroundColor(darkSurface);

            setTextColorsRecursive(cardPreview, darkHeading, darkTextSecondary);

        } else {
            // Fallback for other themes: use dialog color for cards
            if (cardPreview != null) cardPreview.setCardBackgroundColor(colorDialog);

            setTextColorsRecursive(cardPreview, colorTextPrimary, colorTextSecondary);

        }
        // ----- Fix End: Apply dynamic theme colors to preview area cards (force override for AMOLED and Red, use *_surface_dark) -----

        // ----- Fix Start: Storage card always darker gray for all themes -----
        // ----- Fix End: Storage card always darker gray for all themes -----

        // ----- Fix Start: Re-apply clock card color to ensure it's not affected by theme styling -----
        // This ensures the clock card maintains its own independent color regardless of general card styling
        String currentClockColor = sharedPreferencesManager.getClockCardColor();
        
        // No need for special AMOLED handling here - SharedPreferencesManager handles it
        
        // Final application of the determined color
        com.fadcam.Log.i(TAG, "Final clock card color applied: " + currentClockColor + " for theme: " + currentTheme);
        // ----- Fix End: Re-apply clock card color to ensure it's not affected by theme styling -----

        vibrator = (Vibrator) requireActivity().getSystemService(Context.VIBRATOR_SERVICE);

        // Add this debug code
        try {
            Drawable onIcon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_flashlight_on);
            Drawable offIcon = AppCompatResources.getDrawable(requireContext(), R.drawable.ic_flashlight_on);
            Log.d("TorchDebug", "Icon resources loaded - ON: " + (onIcon != null) + ", OFF: " + (offIcon != null));
        } catch (Exception e) {
            Log.e("TorchDebug", "Error checking icon resources: " + e.getMessage());
        }
        
        tips = requireActivity().getResources().getStringArray(R.array.tips_widget);
        Log.d(TAG, "onViewCreated: Setting up UI components");

        // *** ADDED FIX: Load preview enabled state from SharedPreferences ***
        if (sharedPreferencesManager == null) { // Ensure manager is initialized
            sharedPreferencesManager = SharedPreferencesManager.getInstance(requireContext());
        }
        isPreviewEnabled = sharedPreferencesManager.isPreviewEnabled(); // Initialize with saved state
        Log.d(TAG, "onViewCreated: Loaded isPreviewEnabled state = " + isPreviewEnabled);
        // --- END FIX ---

        // ----- Fix Start for this method(onViewCreated_resetTextureView)-----
        // If TextureView is already available, reset it to ensure clean state
        if (textureView != null && textureView.isAvailable()) {
            resetTextureView();
            Log.d(TAG, "onViewCreated: Reset TextureView to ensure clean startup state");
        }
        // ----- Fix Ended for this method(onViewCreated_resetTextureView)-----

        resetTimers();
        copyFontToInternalStorage();
        updateStorageInfo();
        // Initial stats update
        Log.d(TAG, "onViewCreated: Triggering initial stats update.");
        updateStats();
        startUpdatingClock();

        // Update clock and date initially

        // updateTip(); // Duplicate call? Check if startTipsAnimation is sufficient
        startTipsAnimation();
        setupButtonListeners();
        setupLongPressListener();
        updatePreviewVisibility(); // CRUCIAL: Update visibility based on the loaded state



        // Attempt to find camera with flash


        // ----- Fix Start for this method(onViewCreated_kofi_support_icon) -----
        ImageButton ivKoFiSupport = view.findViewById(R.id.ivKoFiSupport);
        if (ivKoFiSupport != null) {
            ivKoFiSupport.setOnClickListener(v -> {
                // Show Ko-fi support bottom sheet
                KoFiSupportBottomSheet bottomSheet = new KoFiSupportBottomSheet();
                bottomSheet.show(getParentFragmentManager(), "KoFiSupportBottomSheet");
            });

            // Enhanced animation: left-right wiggle + slight rotation, with longer pause
            float moveDistance = 10f; // pixels to move left/right
            float rotateAngle = 15f;  // degrees to rotate left/right
            long moveDuration = 160;  // ms for each move
            long pauseDuration = 1500; // ms pause at center

            final Runnable[] startWiggle = new Runnable[1];
            startWiggle[0] = new Runnable() {
                @Override
                public void run() {
                    // Move right + rotate right
                    ObjectAnimator moveRight = ObjectAnimator.ofFloat(ivKoFiSupport, "translationX", 0f, moveDistance);
                    ObjectAnimator rotateRight = ObjectAnimator.ofFloat(ivKoFiSupport, "rotation", 0f, rotateAngle);
                    AnimatorSet rightSet = new AnimatorSet();
                    rightSet.playTogether(moveRight, rotateRight);
                    rightSet.setDuration(moveDuration);

                    // Move left + rotate left
                    ObjectAnimator moveLeft = ObjectAnimator.ofFloat(ivKoFiSupport, "translationX", moveDistance, -moveDistance);
                    ObjectAnimator rotateLeft = ObjectAnimator.ofFloat(ivKoFiSupport, "rotation", rotateAngle, -rotateAngle);
                    AnimatorSet leftSet = new AnimatorSet();
                    leftSet.playTogether(moveLeft, rotateLeft);
                    leftSet.setDuration(moveDuration * 2);

                    // Move center + rotate back to 0
                    ObjectAnimator moveCenter = ObjectAnimator.ofFloat(ivKoFiSupport, "translationX", -moveDistance, 0f);
                    ObjectAnimator rotateCenter = ObjectAnimator.ofFloat(ivKoFiSupport, "rotation", -rotateAngle, 0f);
                    AnimatorSet centerSet = new AnimatorSet();
                    centerSet.playTogether(moveCenter, rotateCenter);
                    centerSet.setDuration(moveDuration);

                    AnimatorSet wiggleSet = new AnimatorSet();
                    wiggleSet.playSequentially(rightSet, leftSet, centerSet);
                    wiggleSet.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            ivKoFiSupport.postDelayed(startWiggle[0], pauseDuration);
                        }
                    });
                    wiggleSet.start();
                }
            };
            // Start the animation loop
            ivKoFiSupport.post(startWiggle[0]);
        }
        // ----- Fix Ended for this method(onViewCreated_kofi_support_icon) -----

        // ----- Fix Start: Apply theme color to top bar and buttons in HomeFragment -----
        MaterialToolbar toolbar = view.findViewById(R.id.topAppBar);
        if (toolbar != null) {
            int colorTopBar = resolveThemeColor(R.attr.colorTopBar);
            toolbar.setBackgroundColor(colorTopBar);
        }
        // If you have FABs or MaterialButtons, set their background tint to colorButton here
    }

    private int resolveThemeColor(int attr) {
        android.util.TypedValue typedValue = new android.util.TypedValue();
        requireContext().getTheme().resolveAttribute(attr, typedValue, true);
        return typedValue.data;
    }
    // ----- Fix End: Apply theme color to top bar and buttons in HomeFragment -----

    private void setupTextureView(@NonNull View view) {
        textureView = view.findViewById(R.id.textureView);
        textureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
                Log.d(TAG, "onSurfaceTextureAvailable: SurfaceTexture is now available.");
                
                // ----- Fix Start for this method(onSurfaceTextureAvailable)-----
                // Clean up any existing surface
                if (textureViewSurface != null) {
                    textureViewSurface.release();
                }
                
                // Create a new surface from the available texture
                textureViewSurface = new Surface(surfaceTexture);
                Log.d(TAG, "onSurfaceTextureAvailable: Created new surface from texture");
                
                // If we're currently recording and preview is enabled, send the surface to service
                if (isPreviewEnabled && isRecordingOrPaused()) {
                    Log.d(TAG, "onSurfaceTextureAvailable: Recording in progress, sending surface to service");
                    updateServiceWithCurrentSurface(textureViewSurface);
                } else {
                    Log.d(TAG, "onSurfaceTextureAvailable: Not recording or preview disabled, surface ready for later use");
                }
                // ----- Fix Ended for this method(onSurfaceTextureAvailable)-----
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                Log.d(TAG, "onSurfaceTextureDestroyed: SurfaceTexture is being destroyed.");
                // ----- Fix Start for this method(onSurfaceTextureDestroyed)-----
                if (textureViewSurface != null) {
                    if (isRecordingOrPaused()) {
                        Log.d(TAG, "onSurfaceTextureDestroyed: Recording active, sending null surface to service.");
                        updateServiceWithCurrentSurface(null);
                    }
                    textureViewSurface.release();
                    textureViewSurface = null;
                    Log.d(TAG, "onSurfaceTextureDestroyed: Released local textureViewSurface.");
                }
                // ----- Fix Ended for this method(onSurfaceTextureDestroyed)-----
                return true; // Surface is released by the listener
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
        });
    }

    private void setupButtonListeners() {
        buttonStartStop.setOnClickListener(v -> {
                if (recordingState.equals(RecordingState.NONE)) {
                    startRecording();
                } else {
                    stopRecording();
                    updateStats();
            }
        });

        buttonPauseResume.setOnClickListener(v -> {
            if (isPaused()) {
                vibrateTouch();
                resumeRecording();
            } else {
                vibrateTouch();
                pauseRecording();
            }
        });

        buttonCamSwitch.setOnClickListener(v -> {
            switchCamera();
        });
    }

    // --- Start Recording ---
    // Inside HomeFragment.java
    private void startRecording() {
        if (getContext() == null) {
            Log.e(TAG, "Context is null, cannot start recording.");
            return;
        }
        performHapticFeedback();
        // Permission checks removed; handled by onboarding

        if (isMyServiceRunning(RecordingService.class)) {
            Log.w(TAG, "Start requested, but service appears to be already running or starting. Current state: " + recordingState);
            // Query the service for its actual state if unsure
            Intent queryIntent = new Intent(getContext(), RecordingService.class);
            queryIntent.setAction(Constants.BROADCAST_ON_RECORDING_STATE_REQUEST);
            ContextCompat.startForegroundService(getContext(), queryIntent);
            // UI should update based on the broadcast from the service
            return; // Don't try to start again if it might be running
        }

        Log.d(TAG, "startRecording: Starting RecordingService.");
        Intent serviceIntent = new Intent(getContext(), RecordingService.class);
        serviceIntent.setAction(Constants.INTENT_ACTION_START_RECORDING);

        // ----- Fix Start for this method(startRecording_passTorchState)-----
        // Pass current torch state (from HomeFragment's perspective) to the service
        // The service will use this to set the initial FLASH_MODE in its CaptureRequest if it starts successfully.
        Log.d(TAG, "Passing initial torch state to service: " + isTorchOn);
        serviceIntent.putExtra(Constants.INTENT_EXTRA_INITIAL_TORCH_STATE, isTorchOn);
        // ----- Fix Ended for this method(startRecording_passTorchState)-----

        // Pass the surface if preview is enabled and surface is valid
        if (isPreviewEnabled && textureViewSurface != null && textureViewSurface.isValid()) {
            Log.d(TAG, "Preview enabled, passing valid surface to service.");
            serviceIntent.putExtra("SURFACE", textureViewSurface);
        } else {
            Log.w(TAG, "Preview disabled or surface invalid. Service will start without preview surface.");
            serviceIntent.putExtra("SURFACE", (Surface) null); // Explicitly pass null
        }

        ContextCompat.startForegroundService(getContext(), serviceIntent);
        // UI state changes will be handled by broadcast receivers
        // setUIForRecordingActive(); // Move UI update to onRecordingStarted broadcast receiver
        Log.d(TAG, "startRecording: RecordingService start initiated.");
    }

    // Inside HomeFragment.java

    /**
     * Helper method to disable buttons typically unavailable during
     * recording initiation, stopping transitions, or active recording/pausing.
     * Checks if views exist before modifying them.
     */
    private void disableInteractionButtons() {
        Log.d(TAG,"Attempting to disable interaction buttons.");
        if (!isAdded() || getView() == null) { // Extra check for view availability
            Log.w(TAG, "disableInteractionButtons: View not available, cannot disable buttons.");
            return;
        }
        try {
            // Null checks are crucial inside helper methods
            if (buttonStartStop != null) {
                buttonStartStop.setEnabled(false);
                Log.v(TAG,"Disabled: Start/Stop Button"); // Verbose log
            } else Log.w(TAG,"buttonStartStop is null in disableInteractionButtons");

            if (buttonPauseResume != null) {
                buttonPauseResume.setEnabled(false);
                Log.v(TAG,"Disabled: Pause/Resume Button");
            } else Log.w(TAG,"buttonPauseResume is null in disableInteractionButtons");

            if (buttonCamSwitch != null) {
                buttonCamSwitch.setEnabled(false);
                Log.v(TAG,"Disabled: Camera Switch Button");
            } else Log.w(TAG,"buttonCamSwitch is null in disableInteractionButtons");

            // if (buttonTorchSwitch != null) {
            //     buttonTorchSwitch.setEnabled(false); // Also disable torch during these states
            //     Log.v(TAG,"Disabled: Torch Button");
            // } else Log.w(TAG,"buttonTorchSwitch is null in disableInteractionButtons");

            Log.d(TAG,"Interaction buttons disabled.");

        } catch (Exception e) {
            // Catch potential NPE or other issues if views are somehow null unexpectedly
            Log.e(TAG, "Error occurred while disabling interaction buttons", e);
        }
    }

    private void updateRecordingSurface()
    {
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();

        Intent startIntent = new Intent(getActivity(), RecordingService.class);
        startIntent.setAction(Constants.INTENT_ACTION_CHANGE_SURFACE);

        if(surfaceTexture != null) {
            startIntent.putExtra("SURFACE", new Surface(surfaceTexture));
        }

        requireActivity().startService(startIntent);
    }

    private void startUpdatingClock() {
        updateClockRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAdded()) {
                    handlerClock.postDelayed(this, 1000);
                }
            }
        };
        handlerClock.post(updateClockRunnable);
    }

    // Method to stop updating the clock
    private void stopUpdatingClock() {
        if (updateClockRunnable != null) {
            handlerClock.removeCallbacks(updateClockRunnable);
            updateClockRunnable = null;
        }
    }




    private void showClockAppearanceDialog() {
        final String[] appearanceOptions = {"Change Clock Display", "Change Clock Color"};
        int white = ContextCompat.getColor(requireContext(), android.R.color.white);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, appearanceOptions) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                if (text1 != null) text1.setTextColor(white);
                return view;
            }
        };
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_FadCam_Dialog)
                .setTitle("Clock Appearance")
                .setAdapter(adapter, (dialog, which) -> {
                    if (which == 0) { // Change Clock Display
                        showDisplayOptionsDialog();
                    } else if (which == 1) { // Change Clock Color
                        showClockColorChooserDialog();
                    }
                })
                .setNegativeButton(R.string.universal_cancel, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            // Set button text color to white
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
        });
        dialog.show();
    }

    private void showDisplayOptionsDialog() {
        final String[] items = {
                getString(R.string.dialog_clock_timeonly),
                getString(R.string.dialog_clock_englishtime),
                getString(R.string.dialog_clock_Islamic_calendar)
        };
        int currentOption = getCurrentDisplayOption();
        int white = ContextCompat.getColor(requireContext(), android.R.color.white);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_single_choice, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                if (text1 != null) text1.setTextColor(white);
                return view;
            }
        };
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_FadCam_Dialog)
                .setTitle(getString(R.string.dialog_clock_title))
                .setSingleChoiceItems(adapter, currentOption, (dialog, which) -> {
                    saveDisplayOption(which);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.universal_cancel, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            // Set button text color to white
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
        });
        dialog.show();
    }

    private void showClockColorChooserDialog() {
        String currentSelectedColorHex = sharedPreferencesManager.getClockCardColor();
        int currentSelectedColorIndex = -1;
        for (int i = 0; i < CLOCK_COLOR_HEX_VALUES.length; i++) {
            if (CLOCK_COLOR_HEX_VALUES[i].equalsIgnoreCase(currentSelectedColorHex)) {
                currentSelectedColorIndex = i;
                break;
            }
        }
        int white = ContextCompat.getColor(requireContext(), android.R.color.white);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_single_choice, CLOCK_COLOR_NAMES) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                if (text1 != null) {
                    text1.setTextColor(white);
                    // ----- Fix Start: Add color circle before color name -----
                    int size = (int) (text1.getTextSize() * 1.2f);
                    GradientDrawable circle = new GradientDrawable();
                    circle.setShape(GradientDrawable.OVAL);
                    circle.setColor(Color.parseColor(CLOCK_COLOR_HEX_VALUES[position]));
                    circle.setSize(size, size);
                    // Set as left drawable
                    text1.setCompoundDrawablesWithIntrinsicBounds(circle, null, null, null);
                    text1.setCompoundDrawablePadding(24);
                    // ----- Fix End: Add color circle before color name -----
                }
                return view;
            }
        };
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_FadCam_Dialog)
                .setTitle("Choose Clock Background Color")
                .setSingleChoiceItems(adapter, currentSelectedColorIndex, (dialog, which) -> {
                    String selectedColorHex = CLOCK_COLOR_HEX_VALUES[which];
                    sharedPreferencesManager.setClockCardColor(selectedColorHex);

                    // Update clock text colors based on background brightness
                    int selectedColor = Color.parseColor(selectedColorHex);
                    boolean isLightColor = isLightColor(selectedColor);
                    
                    // Set text colors based on background brightness

                    Log.d(TAG, "User selected clock color: " + CLOCK_COLOR_NAMES[which] + " (" + selectedColorHex + ")");
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.universal_cancel, null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(dialogInterface -> {
            // Set button text color to white
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(Color.WHITE);
        });
        dialog.show();
    }
    
    /**
     * Determines if a color is light or dark.
     * @param color The color to check
     * @return true if the color is light, false if dark
     */
    private boolean isLightColor(int color) {
        // Calculate the perceived brightness using the formula
        // (0.299*R + 0.587*G + 0.114*B)
        double brightness = (Color.red(color) * 0.299) + 
                           (Color.green(color) * 0.587) + 
                           (Color.blue(color) * 0.114);
        // If the brightness is greater than 160, consider it a light color
        return brightness > 160;
    }

    private int getCurrentDisplayOption() {
        return requireActivity().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE).getInt("display_option", 2); // Default to "Everything"
    }

    private void saveDisplayOption(int option) {
        SharedPreferences.Editor editor = requireActivity().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE).edit();
        editor.putInt("display_option", option);
        editor.apply();
    }



    // Method to update the clock and dates

    private void updateStorageInfo() {
        Log.e(TAG,"========================================================     -=================");

        Log.d(TAG, "updateStorageInfo: Updating storage information");
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long bytesAvailable = stat.getAvailableBytes();
        long bytesTotal = stat.getTotalBytes();

        double gbAvailable = bytesAvailable / (1024.0 * 1024.0 * 1024.0);
        double gbTotal = bytesTotal / (1024.0 * 1024.0 * 1024.0);

        // Only calculate estimated bytes used if we're actually recording
        long elapsedTime = 0;
        long estimatedBytesUsed = 0;
        
        if (isRecording() || isPaused()) {
            // Check if recordingStartTime is valid, otherwise reset it
            if (recordingStartTime <= 0) {
                recordingStartTime = SystemClock.elapsedRealtime();
                Log.w(TAG, "updateStorageInfo: Invalid recordingStartTime detected, resetting to current time");
            }
            
            // Always calculate elapsed time since recording started
            elapsedTime = SystemClock.elapsedRealtime() - recordingStartTime;
            
            // Force elapsed time to be non-negative
            elapsedTime = Math.max(0, elapsedTime);
            
            Log.d(TAG, "updateStorageInfo: recordingStartTime=" + recordingStartTime + 
                  ", currentTime=" + SystemClock.elapsedRealtime() + 
                  ", calculated elapsedTime=" + elapsedTime + "ms");
            
            // Only calculate if we have valid values
            if (elapsedTime > 0 && videoBitrate > 0) {
                estimatedBytesUsed = (elapsedTime * videoBitrate) / 8000; // Convert ms and bits to bytes
                // Safety check: don't let estimated bytes exceed available bytes
                estimatedBytesUsed = Math.min(estimatedBytesUsed, bytesAvailable);
                Log.d(TAG, "updateStorageInfo: Elapsed=" + elapsedTime + "ms, Est. bytes used=" + estimatedBytesUsed);
            }
        } else {
            // Reset recording start time when not recording
            recordingStartTime = 0;
            Log.d(TAG, "updateStorageInfo: Not recording, reset recordingStartTime=0");
        }

        // Update available space based on estimated bytes used
        bytesAvailable -= estimatedBytesUsed;
        // Ensure we never show negative available space
        bytesAvailable = Math.max(0, bytesAvailable);
        Log.e(TAG,bytesAvailable+"     -=================");
        gbAvailable = Math.max(0, bytesAvailable / (1024.0 * 1024.0 * 1024.0));

        // Calculate remaining recording time based on available space and bitrate
        long remainingTime = 0;
        if (videoBitrate > 0) {
            remainingTime = (bytesAvailable * 8) / videoBitrate; 
        }
        // Ensure remaining time is never negative
        remainingTime = Math.max(0, remainingTime);
        
        // Calculate days, hours, minutes, and seconds for remaining time
        long days = remainingTime / (24 * 3600);
        long hours = (remainingTime % (24 * 3600)) / 3600;
        long minutes = (remainingTime % 3600) / 60;
        long seconds = remainingTime % 60;

        // Calculate elapsed minutes and seconds - ensure they're always non-negative
        long elapsedMinutes = elapsedTime / 60000;  // Convert ms to minutes
        long elapsedSeconds = (elapsedTime / 1000) % 60;  // Get seconds part
        
        // Log elapsed time values for debugging
        Log.d(TAG, "updateStorageInfo: Formatted elapsed time = " + elapsedMinutes + ":" + 
              String.format(Locale.US, "%02d", elapsedSeconds));

        String storageInfo = String.format(Locale.getDefault(),
                getString(R.string.mainpage_storage_indicator),
                gbAvailable, gbTotal,
                getRecordingTimeEstimate(bytesAvailable, (10 * 1024 * 1024) / 2), // 50% of 10 Mbps
                getRecordingTimeEstimate(bytesAvailable, (5 * 1024 * 1024) / 2),  // 50% of 5 Mbps
                getRecordingTimeEstimate(bytesAvailable, (1024 * 1024) / 2),      // 50% of 1 Mbps
                elapsedMinutes, elapsedSeconds,
                formatRemainingTime(days, hours, minutes, seconds)
        );

        Spanned formattedText = Html.fromHtml(storageInfo, Html.FROM_HTML_MODE_LEGACY);


    }

    private String formatRemainingTime(long days, long hours, long minutes, long seconds) {
        StringBuilder remainingTime = new StringBuilder();
        if (days > 0) {
            remainingTime.append(String.format(Locale.getDefault(), "<font color='#E43C3C'>%d</font><font color='#CCCCCC'>days</font> ", days));
        }
        if (hours > 0) {
            remainingTime.append(String.format(Locale.getDefault(), "<font color='#E43C3C'>%d</font><font color='#CCCCCC'>h</font> ", hours));
        }
        if (minutes > 0) {
            remainingTime.append(String.format(Locale.getDefault(), "<font color='#E43C3C'>%d</font><font color='#CCCCCC'>m</font> ", minutes));
        }
        if (seconds > 0 || remainingTime.length() == 0) {
            remainingTime.append(String.format(Locale.getDefault(), "<font color='#E43C3C'>%d</font><font color='#CCCCCC'>s</font>", seconds));
        }
        return remainingTime.toString();
    }

    private String getRecordingTimeEstimate(long availableBytes, long bitrate) {
        // Prevent division by zero
        if (bitrate <= 0) {
            return "∞ h ∞ min"; // Infinite time if bitrate is zero
        }
        
        // Calculate seconds, handling potential overflow
        long recordingSeconds;
        try {
            recordingSeconds = (availableBytes * 8) / bitrate;
        } catch (Exception e) {
            Log.e(TAG, "Error calculating recording time estimate", e);
            recordingSeconds = 0;
        }
        
        // Ensure non-negative values
        recordingSeconds = Math.max(0, recordingSeconds);
        
        long recordingHours = recordingSeconds / 3600;
        long recordingMinutes = (recordingSeconds % 3600) / 60;
        
        return String.format(Locale.getDefault(), "%d h %d min", recordingHours, recordingMinutes);
    }

    //    update storage and stats in real time while recording is started
    private void startUpdatingInfo() {
        // Cancel any existing runnable first
        if (updateInfoRunnable != null) {
            handlerClock.removeCallbacks(updateInfoRunnable);
            updateInfoRunnable = null;
        }
        
        // Create a new runnable
        updateInfoRunnable = new Runnable() {
            @Override
            public void run() {
                if ((isRecording() || isPaused()) && isAdded()) {
                    // Check if we have a valid recording start time
                    if (recordingStartTime <= 0) {
                        // Try to get the current system time as fallback
                        recordingStartTime = SystemClock.elapsedRealtime();
                        Log.w(TAG, "startUpdatingInfo: Invalid recordingStartTime detected, resetting to current time: " + recordingStartTime);
                    }
                    
                    Log.d(TAG, "Update timer: Refreshing storage info and stats, recordingStartTime=" + recordingStartTime);
                    updateStorageInfo();
                    updateStats();
                    handlerClock.postDelayed(this, 1000); // Update every second
                } else {
                    Log.d(TAG, "Update timer: Not recording or fragment detached, stopping updates");
                    stopUpdatingInfo(); // Clean up if recording state changed
                }
            }
        };
        
        // Post immediately to start updates
        handlerClock.post(updateInfoRunnable);
        Log.d(TAG, "startUpdatingInfo: Started real-time storage/stats updates");
    }

    private void stopUpdatingInfo() {
        Log.d(TAG, "stopUpdatingInfo: Stopping real-time updates");
        if (updateInfoRunnable != null) {
            handlerClock.removeCallbacks(updateInfoRunnable);
            updateInfoRunnable = null;
        }
    }

    private void startTipsAnimation() {
        if (tips.length > 0) {
            animateTip(tips[currentTipIndex],  100); // Adjust delay as needed
        }
    }



    private void animateTip(String fullText,  int delay) {
        final Handler handler = new Handler(Looper.getMainLooper());
        final int[] index = {0};

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (index[0] <= fullText.length()) {
                    index[0]++;
                    handler.postDelayed(this, 40); // add delay in typing the tips
                } else {
                    currentTipIndex = (currentTipIndex + 1) % tips.length;
                    handler.postDelayed(() -> animateTip(tips[currentTipIndex],  delay), 5000); // Wait 2 seconds before next tip
                }
            }
        };

        handler.post(runnable);
    }

    // --- BroadcastReceiver Implementation ---

    // private void registerStatsReceiver() {
    //     if (!isStatsReceiverRegistered && getContext() != null) {
    //         if (recordingCompleteReceiver == null) {
    //             recordingCompleteReceiver = new BroadcastReceiver() {
    //                 @Override
    //                 public void onReceive(Context context, Intent intent) {
    //                     if (intent != null && Constants.ACTION_RECORDING_COMPLETE.equals(intent.getAction())) {
    //                         android.util.Log.i(TAG, "Received ACTION_RECORDING_COMPLETE in HomeFragment, updating stats...");
    //                         updateStats(); // Trigger stats recalculation
    //                     }
    //                 }
    //             };
    //         }
    //         IntentFilter filter = new IntentFilter(Constants.ACTION_RECORDING_COMPLETE);
    //         ContextCompat.registerReceiver(requireContext(), recordingCompleteReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    //         isStatsReceiverRegistered = true;
    //         Log.d(TAG, "Stats ACTION_RECORDING_COMPLETE receiver registered.");
    //     }
    // }

//    private void unregisterStatsReceiver() {
//        if (isStatsReceiverRegistered && recordingCompleteReceiver != null && getContext() != null) {
//            try {
//                requireContext().unregisterReceiver(recordingCompleteReceiver);
//                isStatsReceiverRegistered = false;
//                Log.d(TAG, "Stats ACTION_RECORDING_COMPLETE receiver unregistered.");
//            } catch (IllegalArgumentException e) {
//                Log.w(TAG,"Attempted to unregister stats receiver but it wasn't registered?");
//                isStatsReceiverRegistered = false; // Ensure flag is reset
//            }
//        }
//    }

    // --- Updated updateStats Method ---

    private void updateStats() {
        Log.d(TAG, "updateStats: Starting calculation...");
        if (executorService == null || executorService.isShutdown()) {
            Log.w(TAG,"ExecutorService not available for updateStats");
            // Reinitialize if needed or handle gracefully
            executorService = Executors.newSingleThreadExecutor();
        }

        executorService.submit(() -> {
            // --- Get current storage settings ---
            String storageMode = sharedPreferencesManager.getStorageMode();
            String customUriString = sharedPreferencesManager.getCustomStorageUri();
            List<VideoItem> primaryItems;
            List<VideoItem> tempItems;

            // --- Load File Lists (Same logic as RecordsFragment) ---
            Log.d(TAG,"updateStats BG: Loading file lists. Mode: "+storageMode);
            if (SharedPreferencesManager.STORAGE_MODE_CUSTOM.equals(storageMode) && customUriString != null) {
                Uri treeUri = null; try { treeUri = Uri.parse(customUriString); } catch (Exception e) { Log.e(TAG,"BG updateStats: Error parsing custom URI", e);}
                if (treeUri != null && hasSafPermission(treeUri)) {
                    primaryItems = getSafRecordsList(treeUri);
                } else {
                    Log.e(TAG,"BG updateStats: Permission error or invalid URI for custom location: "+ customUriString);
                    primaryItems = new ArrayList<>();
                }
            } else {
                primaryItems = getInternalRecordsList();
            }
            tempItems = getTempCacheRecordsList();

            // Combine (ok to run on background thread)
            List<VideoItem> combinedItems = combineVideoLists(primaryItems, tempItems);

            // --- Calculate Stats (Count and Size) ---
            int numVideos = combinedItems.size();
            long totalSizeBytes = 0;
            for (VideoItem item : combinedItems) {
                if (item != null) { // Basic null check
                    totalSizeBytes += item.size;
                }
            }

            // Format size
            String totalSizeFormatted = (getContext() != null)
                    ? Formatter.formatFileSize(getContext(), totalSizeBytes)
                    : String.format(Locale.US,"%.2f GB", totalSizeBytes / (1024.0*1024.0*1024.0)); // Fallback format

            // Get current theme
            String currentTheme = sharedPreferencesManager.sharedPreferences.getString(com.fadcam.Constants.PREF_APP_THEME, Constants.DEFAULT_APP_THEME);
            boolean isSnowVeilTheme = "Snow Veil".equals(currentTheme);
            
            // Prepare final text for UI - special formatting for Snow Veil theme
            final String statsText;
            final Spanned formattedText;
            
            if (isSnowVeilTheme) {
                // Create a custom black text version for Snow Veil theme
                statsText = "\n    " +
                    "<font color='#000000' style='font-size:12sp;'><b>Videos: </b></font>" +
                    "<font color='#333333' style='font-size:11sp;'>" + numVideos + "</font><br>" +
                    "<font color='#000000' style='font-size:12sp;'><b>Used Space:</font>" +
                    "<font color='#333333' style='font-size:11sp;'>" + totalSizeFormatted + "</font>" +
                    "\n";
            } else {
                // Use the standard resource for other themes
                statsText = String.format(Locale.getDefault(),
                    getString(R.string.mainpage_video_info), // Using your existing string resource
                    numVideos, totalSizeFormatted);
            }
            
            formattedText = Html.fromHtml(statsText, Html.FROM_HTML_MODE_LEGACY);

            Log.d(TAG,"updateStats BG: Calculation complete. Count="+numVideos+", Size="+totalSizeFormatted);


        });
    }

    // --- COPIED Helper Methods (from RecordsFragment or move to shared Utils class) ---
    // You NEED these methods here or accessible via Utils

    private boolean hasSafPermission(Uri treeUri) {
        Context context = getContext();
        if (context == null || treeUri == null) return false;
        try {
            List<UriPermission> persistedUris = context.getContentResolver().getPersistedUriPermissions();
            boolean permissionFound = false;
            for (UriPermission uriPermission : persistedUris) {
                if (uriPermission.getUri().equals(treeUri) && uriPermission.isReadPermission() && uriPermission.isWritePermission()) {
                    permissionFound = true; break;
                }
            }
            if (!permissionFound) return false;
            DocumentFile docDir = DocumentFile.fromTreeUri(context, treeUri);
            return docDir != null && docDir.canRead();
        } catch (Exception e) { Log.e(TAG, "Error checking SAF permission", e); return false; }
    }

    private List<VideoItem> getInternalRecordsList() {
        List<VideoItem> items = new ArrayList<>();
        File recordsDir = getContext() != null ? getContext().getExternalFilesDir(null) : null;
        if (recordsDir == null) { Log.e(TAG, "Context or ExternalFilesDir null in getInternalRecordsList"); return items; }
        File fadCamDir = new File(recordsDir, Constants.RECORDING_DIRECTORY);
        if (fadCamDir.exists() && fadCamDir.isDirectory()) {
            File[] files = fadCamDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith("." + Constants.RECORDING_FILE_EXTENSION) && !file.getName().startsWith("temp_")) {
                        items.add(new VideoItem(Uri.fromFile(file), file.getName(), file.length(), file.lastModified()));
                    }
                }
            }
        }
        return items;
    }

    private List<VideoItem> getSafRecordsList(Uri treeUri) {
        List<VideoItem> items = new ArrayList<>();
        Context context = getContext();
        if (context == null || treeUri == null) { Log.e(TAG,"Context or treeUri null in getSafRecordsList"); return items;}
        DocumentFile dir = DocumentFile.fromTreeUri(context, treeUri);
        if (dir != null && dir.isDirectory() && dir.canRead()) {
            try {
                for (DocumentFile file : dir.listFiles()) {
                    if (file != null && file.isFile() && file.getName() != null &&
                            (file.getName().endsWith("." + Constants.RECORDING_FILE_EXTENSION) || "video/mp4".equals(file.getType())) &&
                            !file.getName().startsWith("temp_"))
                    {
                        items.add(new VideoItem(file.getUri(), file.getName(), file.length(), file.lastModified()));
                    }
                }
            } catch (Exception e) { Log.e(TAG, "Error listing SAF files in updateStats for " + treeUri, e); }
        } else { Log.e(TAG, "Cannot read/access SAF dir in updateStats: " + treeUri); }
        return items;
    }

    private List<VideoItem> getTempCacheRecordsList() {
        List<VideoItem> items = new ArrayList<>();
        Context context = getContext();
        if (context == null) return items;
        File cacheBaseDir = context.getExternalCacheDir();
        if (cacheBaseDir == null) return items;
        File recordingTempDir = new File(cacheBaseDir, "recording_temp");
        scanDirectoryForTempVideos(recordingTempDir, items);
        // Scan other temp dirs if needed based on RecordingService logic
        return items;
    }

    private void scanDirectoryForTempVideos(File directory, List<VideoItem> items) {
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().startsWith("temp_") && file.getName().endsWith("." + Constants.RECORDING_FILE_EXTENSION)) {
                        if (file.length() > 0) {
                            items.add(new VideoItem(Uri.fromFile(file), file.getName(), file.length(), file.lastModified()));
                        }
                    }
                }
            }
        }
    }

    private List<VideoItem> combineVideoLists(List<VideoItem> primary, List<VideoItem> temp) {
        List<VideoItem> combined = new ArrayList<>();
        Set<Uri> existingUris = new HashSet<>();
        for (VideoItem item : primary) { if (item != null && item.uri != null && existingUris.add(item.uri)) { combined.add(item); } }
        for (VideoItem item : temp) { if (item != null && item.uri != null && existingUris.add(item.uri)) { combined.add(item); } }
        return combined;
    }

    private void pauseRecording() {
        Log.d(TAG, "pauseRecording: Pausing video recording");

        buttonPauseResume.setIcon(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_play));
        buttonPauseResume.setEnabled(false);

        buttonCamSwitch.setEnabled(false);

        Intent stopIntent = new Intent(getActivity(), RecordingService.class);
        stopIntent.setAction(Constants.INTENT_ACTION_PAUSE_RECORDING);
        requireActivity().startService(stopIntent);
    }

    private void resumeRecording() {
        Log.d(TAG, "resumeRecording: Resuming video recording");

        buttonPauseResume.setIcon(AppCompatResources.getDrawable(requireContext(), R.drawable.ic_pause));
        buttonPauseResume.setEnabled(false);

        buttonCamSwitch.setEnabled(false);

        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();

        Intent recordingServiceIntent = new Intent(getActivity(), RecordingService.class);
        recordingServiceIntent.setAction(Constants.INTENT_ACTION_RESUME_RECORDING);
        if(surfaceTexture != null) {
            recordingServiceIntent.putExtra("SURFACE", new Surface(surfaceTexture));
        }
        requireActivity().startService(recordingServiceIntent);
    }

    private void setVideoBitrate() {
        videoBitrate = Utils.estimateBitrate(sharedPreferencesManager.getCameraResolution(), sharedPreferencesManager.getVideoFrameRate());
        Log.d(TAG, "setVideoBitrate: Set to " + videoBitrate + " bps");
    }

    // --- Stop Recording ---
    private void stopRecording() {
        if (!isAdded() || getActivity() == null) { Log.w(TAG,"Stop: Not attached"); return; }
        if(recordingState == RecordingState.NONE){ Log.w(TAG,"Stop clicked but state NONE?"); return; } // Prevent multi-stop

        Log.i(TAG, ">> stopRecording user action");
        disableInteractionButtons(); Log.d(TAG,"stopRecording: Btns disabled.");

        Intent stopIntent = new Intent(getActivity(), RecordingService.class);
        stopIntent.setAction(Constants.INTENT_ACTION_STOP_RECORDING);
        try {
            requireActivity().startService(stopIntent); Log.i(TAG, "Sent STOP intent.");
        } catch (Exception e) {
            Log.e(TAG, "Error sending STOP intent: ", e); Toast.makeText(getContext(),"Error stop svc", Toast.LENGTH_SHORT).show();
            resetUIButtonsToIdleState(); Log.d(TAG, "stopIntent fail: UI Reset.");
        }
        vibrateTouch();
    }

    private void copyFontToInternalStorage() {
        AssetManager assetManager = requireContext().getAssets();
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open("ubuntu_regular.ttf");
            File outFile = new File(requireContext().getFilesDir(), "ubuntu_regular.ttf");
            out = new FileOutputStream(outFile);
            copyFile(in, out);
            Log.d(TAG, "Font copied to internal storage.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    // NO-OP
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    // NO-OP
                }
            }
        }
    }

    public void switchCamera() {
        if (sharedPreferencesManager.getCameraSelection().equals(CameraType.BACK)) {
            sharedPreferencesManager.sharedPreferences.edit().putString(Constants.PREF_CAMERA_SELECTION, CameraType.FRONT.toString()).apply();
            Log.d(TAG, "Camera set to front");
            Toast.makeText(getContext(), R.string.switched_front_camera, Toast.LENGTH_SHORT).show();
        } else {
            sharedPreferencesManager.sharedPreferences.edit().putString(Constants.PREF_CAMERA_SELECTION, CameraType.BACK.toString()).apply();
            Log.d(TAG, "Camera set to rear");
            Toast.makeText(getContext(), R.string.switched_rear_camera, Toast.LENGTH_SHORT).show();
        }
    }

//    private class LocationHelper implements LocationListener {
//
//        private LocationManager locationManager;
//        private double latitude;
//        private double longitude;
//
//        public LocationHelper(LocationManager locationManager) {
//            this.locationManager = locationManager;
//        }
//
//        public void startListening() {
//            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
//        }
//
//        @Override
//        public void onLocationChanged(Location location) {
//            latitude = location.getLatitude();
//            longitude = location.getLongitude();
//        }
//
//        public String getLocationText() {
//            return String.format(Locale.getDefault(), "%.2f, %.2f", latitude, longitude);
//        }
//    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();


        stopUpdatingInfo();
        stopUpdatingClock();
    }

    public boolean isRecording() {
        return recordingState.equals(RecordingState.IN_PROGRESS);
    }

    public boolean isPaused() {
        return recordingState.equals(RecordingState.PAUSED);
    }



    @SuppressLint("UnspecifiedRegisterReceiverFlag")

    // Ensure toggleTorch method exists and correctly uses CameraManager.setTorchMode
    // and updates the local isTorchOn variable and calls updateTorchUI.
    // Example (ensure your actual toggleTorch matches this logic):

    private String getCameraIdForTorch() {
        // This method should return the ID of the camera that HomeFragment
        // is currently configured to use for its general operations (like preview if it had one, or torch).
        // It might be based on SharedPreferencesManager.getCameraSelection() and SharedPreferencesManager.getSelectedBackCameraId()
        // This is a simplified placeholder. You need to ensure this returns the correct, active camera ID.

        // Fallback or more complex logic to determine the appropriate camera ID:
        try {
            if (cameraManager == null && getContext() != null) {
                 cameraManager = (CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            }
            if (cameraManager == null) return null;

            CameraType selectedType = sharedPreferencesManager.getCameraSelection();
            if (selectedType == CameraType.FRONT) {
                for (String id : cameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT && flashAvailable != null && flashAvailable) {
                        return id;
                    }
                }
            } else { // BACK
                String preferredBackId = sharedPreferencesManager.getSelectedBackCameraId();
                if(preferredBackId != null){
                     CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(preferredBackId);
                     Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                     Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                     if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK && flashAvailable != null && flashAvailable) {
                         return preferredBackId;
                     }
                }
                // Fallback to default back camera if preferred is not suitable or not found
                for (String id : cameraManager.getCameraIdList()) {
                    CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK && flashAvailable != null && flashAvailable) {
                        return id; // Return first available back camera with flash
                    }
                }
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "Error accessing camera for torch ID", e);
        }
        return null; // No suitable camera found
    }





    private String getCameraWithFlash() throws CameraAccessException {
        for (String id : cameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
            Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
            if (flashAvailable != null && flashAvailable) {
                Log.d(TAG, "Found camera with flash: " + id);
                return id;
            }
        }
        Log.d(TAG, "No camera with flash found");
        return null;
    }

    private boolean isRecordingInProgress() {
        ActivityManager manager = (ActivityManager) requireContext().getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (RecordingService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }




    // ----- Fix Start for this class (HomeFragment) -----
    private void initializeViews(View view) {
        Log.d(TAG, "initializeViews: Finding UI elements.");
        tvPreviewPlaceholder = view.findViewById(R.id.tvPreviewPlaceholder);
        buttonStartStop = view.findViewById(R.id.buttonStartStop);
        buttonPauseResume = view.findViewById(R.id.buttonPauseResume);
        buttonCamSwitch = view.findViewById(R.id.buttonCamSwitch);
        cardPreview = view.findViewById(R.id.cardPreview); // Assuming R.id.cardPreview exists
        vibrator = (Vibrator) requireActivity().getSystemService(Context.VIBRATOR_SERVICE);

        // Initialize pause button to be visibly disabled from the start
        if (buttonPauseResume != null) {
            buttonPauseResume.setEnabled(false);
            buttonPauseResume.setAlpha(0.5f);
        }

        // Clock related views


        // Tip view

        // Stats view

        // Torch button (already initialized elsewhere, but good to have it consistently)

        // Initialize other views as needed here.
        // textureView is handled by setupTextureView
    }
    // ----- Fix Ended for this class (HomeFragment) -----

    // ----- Fix Start for this method(isRecordingOrPaused)-----
    private boolean isRecordingOrPaused() {
        return recordingState == RecordingState.IN_PROGRESS || recordingState == RecordingState.PAUSED;
    }
    // ----- Fix Ended for this method(isRecordingOrPaused)-----

    // ----- Fix Start for this method(updateServiceWithCurrentSurface)-----
    // This method replaces/refines the old updateRecordingSurface
    private void updateServiceWithCurrentSurface(@Nullable Surface surfaceToUse) {
        if (!isAdded() || getContext() == null) {
            Log.w(TAG, "updateServiceWithCurrentSurface: Fragment not added or context is null, cannot send surface update.");
            return;
        }

        Intent intent = new Intent(getContext(), RecordingService.class);
        intent.setAction(Constants.INTENT_ACTION_CHANGE_SURFACE);
        if (surfaceToUse != null && surfaceToUse.isValid()) {
            intent.putExtra("SURFACE", surfaceToUse);
            Log.d(TAG, "updateServiceWithCurrentSurface: Sending new VALID surface to RecordingService.");
        } else {
            intent.putExtra("SURFACE", (Surface) null); 
            Log.d(TAG, "updateServiceWithCurrentSurface: Sending NULL surface to RecordingService (preview disabled or surface invalid/destroyed).");
        }
        // Use requireContext() for starting service if preferred and appropriate for fragment version
        Context context = getContext();
        if (context != null) {
            context.startService(intent);
        }
    }
    // ----- Fix Ended for this method(updateServiceWithCurrentSurface)-----

    // ----- Fix Start for this class (HomeFragment) -----
    private boolean isMyServiceRunning(Class<?> serviceClass) {
        if (getContext() == null) {
            return false;
        }
        ActivityManager manager = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    // ----- Fix Ended for this class (HomeFragment) -----

    // ----- Fix Start for this class (HomeFragment_clock_color_picker) -----





    /**
     * Override the onBackPressed method from BaseFragment
     */
    @Override
    protected boolean onBackPressed() {
        // Handle any special cases for the HomeFragment's back button
        if (isRecordingOrPaused()) {
            // If recording is in progress, show a confirmation dialog
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Recording in Progress")
                .setMessage("Do you want to stop recording and exit?")
                .setPositiveButton("Stop and Exit", (dialog, which) -> {
                    stopRecording();
                    // Allow normal back behavior after stopping recording
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                })
                .setNegativeButton("Continue Recording", null)
                .show();
            return true; // We handled the back press
        }
        
        // For normal cases, let the base implementation handle it
        return false;
    }

    // ----- Fix Start for camera resource availability methods -----
    /**
     * Initializes the receiver for camera resource availability status updates
     */
    private void initializeCameraResourceAvailabilityReceiver() {
        if (cameraResourceAvailabilityReceiver == null) {
            cameraResourceAvailabilityReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!isAdded() || intent == null || 
                        !Constants.ACTION_CAMERA_RESOURCE_AVAILABILITY.equals(intent.getAction())) {
                        return;
                    }
                    
                    boolean isAvailable = intent.getBooleanExtra(
                        Constants.EXTRA_CAMERA_RESOURCES_AVAILABLE, true);
                    
                    areCameraResourcesAvailable = isAvailable;
                    updateStartButtonAvailability();
                    
                    Log.d(TAG, "Received camera resource availability status: " + isAvailable);
                }
            };
        }
    }
    
    /**
     * Registers the camera resource availability receiver
     */
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerCameraResourceAvailabilityReceiver() {
        if (isCameraResourceAvailabilityReceiverRegistered || getContext() == null) {
            return;
        }
        
        initializeCameraResourceAvailabilityReceiver();
        if (cameraResourceAvailabilityReceiver == null) {
            Log.e(TAG, "Cannot register: Failed to initialize camera resource availability receiver");
            return;
        }
        
        IntentFilter filter = new IntentFilter(Constants.ACTION_CAMERA_RESOURCE_AVAILABILITY);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requireContext().registerReceiver(
                    cameraResourceAvailabilityReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                requireContext().registerReceiver(cameraResourceAvailabilityReceiver, filter);
            }
            isCameraResourceAvailabilityReceiverRegistered = true;
            Log.d(TAG, "Camera resource availability receiver registered");
        } catch (Exception e) {
            Log.e(TAG, "Error registering camera resource availability receiver", e);
        }
    }
    
    /**
     * Unregisters the camera resource availability receiver
     */
    private void unregisterCameraResourceAvailabilityReceiver() {
        if (!isCameraResourceAvailabilityReceiverRegistered || getContext() == null) {
            return;
        }
        
        try {
            requireContext().unregisterReceiver(cameraResourceAvailabilityReceiver);
            isCameraResourceAvailabilityReceiverRegistered = false;
            Log.d(TAG, "Camera resource availability receiver unregistered");
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Error unregistering camera resource availability receiver: " + e.getMessage());
        }
    }
    // ----- Fix End for camera resource availability methods -----

    // ----- Fix Start for this class (HomeFragment) -----
    private void initializeSegmentCompleteStatsReceiver() {
        if (segmentCompleteStatsReceiver == null) {
            segmentCompleteStatsReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (intent != null && Constants.ACTION_RECORDING_SEGMENT_COMPLETE.equals(intent.getAction())) {
                        Log.d(TAG, "Segment complete, updating stats from HomeFragment.");
                        if (isAdded()) { // Ensure fragment is still attached
                            updateStats();
                        }
                    }
                }
            };
        }
    }
    // ----- Fix Ended for this class (HomeFragment) -----

    // ----- Fix Start for this class (HomeFragment) -----
    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerSegmentCompleteStatsReceiver(Context context) {
        if (context == null) {
            Log.e(TAG, "Context is null, cannot register segmentCompleteStatsReceiver");
            // ----- Fix Start for this method(registerSegmentCompleteStatsReceiver_set_flag)-----
            isSegmentCompleteStatsReceiverRegistered = false;
            return;
            // ----- Fix Ended for this method(registerSegmentCompleteStatsReceiver_set_flag)-----
        }
        initializeSegmentCompleteStatsReceiver(); // Ensure it's initialized

        // ----- Fix Start for this method(registerSegmentCompleteStatsReceiver_set_flag)-----
        if (segmentCompleteStatsReceiver != null) {
            IntentFilter filter = new IntentFilter(Constants.ACTION_RECORDING_SEGMENT_COMPLETE);
            // Add receiver export flag for Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(segmentCompleteStatsReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(segmentCompleteStatsReceiver, filter);
            }
            isSegmentCompleteStatsReceiverRegistered = true;
            Log.d(TAG, "Registered segmentCompleteStatsReceiver.");
        } else {
            isSegmentCompleteStatsReceiverRegistered = false;
            Log.e(TAG, "segmentCompleteStatsReceiver is null, not registering.");
        }
        // ----- Fix Ended for this method(registerSegmentCompleteStatsReceiver_set_flag)-----
    }
    // ----- Fix Ended for this class (HomeFragment) -----

    // ----- Fix Start for this method(onHiddenChanged)-----
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        Log.d(TAG, "onHiddenChanged: Fragment " + (hidden ? "hidden" : "shown"));
        
        // If fragment is becoming visible (not hidden)
        if (!hidden) {
            // Same logic as in onResume to ensure preview state is correctly applied
            if (sharedPreferencesManager == null) {
                sharedPreferencesManager = SharedPreferencesManager.getInstance(requireContext());
            }
            
            // Re-load preview state from SharedPreferences
            isPreviewEnabled = sharedPreferencesManager.isPreviewEnabled();
            Log.d(TAG, "onHiddenChanged: Loaded isPreviewEnabled state = " + isPreviewEnabled);
            
            // Update UI based on loaded state
            updatePreviewVisibility();
            
            // Update the surface accordingly
            if (isPreviewEnabled && isRecordingOrPaused() && textureViewSurface != null && textureViewSurface.isValid()) {
                Log.d(TAG, "onHiddenChanged: Preview enabled, sending valid surface to service");
                updateServiceWithCurrentSurface(textureViewSurface);
            } else if (!isPreviewEnabled || !isRecordingOrPaused()) {
                Log.d(TAG, "onHiddenChanged: Preview disabled or not recording, sending null surface");
                updateServiceWithCurrentSurface(null);
            }
        } else {
            // If fragment is being hidden, we should release the surface from the service
            // to prevent any lingering frames when coming back
            if (isRecordingOrPaused()) {
                Log.d(TAG, "onHiddenChanged: Fragment hidden while recording, sending null surface");
                updateServiceWithCurrentSurface(null);
            }
        }
    }
    // ----- Fix Ended for this method(onHiddenChanged)-----

    // ----- Fix Start: Add setTextColorsRecursive helper for dynamic theming -----
    private void setTextColorsRecursive(View view, int primary, int secondary) {
        if (view == null) return;
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            CharSequence text = tv.getText();
            if (text != null && text.length() > 0 && (tv.getTextSize() >= 16f)) {
                tv.setTextColor(primary);
            } else {
                tv.setTextColor(secondary);
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                setTextColorsRecursive(vg.getChildAt(i), primary, secondary);
            }
        }
    }
    // ----- Fix End: Add setTextColorsRecursive helper for dynamic theming -----

    // ----- Fix Start: Add method to get default clock color for theme -----
    // ----- Fix End: Add method to get default clock color for theme -----

    // ----- Fix Start: Add Snow Veil theme UI adjustments -----
    /**
     * Applies Snow Veil theme UI adjustments to improve contrast
     */

    
    /**
     * Apply tinting only to main action buttons
     */
    private void applyButtonTinting() {
        // Only tint the main action buttons that we already have references to
        // This avoids searching for IDs that might not exist
        
        // Start/Stop button
        if (buttonStartStop != null) {
            buttonStartStop.setTextColor(Color.BLACK);
            buttonStartStop.setIconTint(ColorStateList.valueOf(Color.BLACK));
        }
        
        // Pause/Resume button
        if (buttonPauseResume != null) {
            buttonPauseResume.setTextColor(Color.BLACK);
            buttonPauseResume.setIconTint(ColorStateList.valueOf(Color.BLACK));
        }
        
        // Torch switch button - special handling for on/off state

        
        // Camera switch button
        if (buttonCamSwitch != null) {
            buttonCamSwitch.setTextColor(Color.BLACK);
        }
    }
    
    /**
     * Ensure text in cards has proper contrast with focused handling for video states card
     */

    /**
     * Helper method to make all text in a ViewGroup black - more aggressive version
     */
    private void forceForceMakeAllTextBlack(ViewGroup viewGroup) {
        try {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof TextView) {
                    TextView textView = (TextView) child;
                    textView.setTextColor(Color.BLACK);
                    
                    // Handle if the text has any spans (HTML formatting)
                    CharSequence text = textView.getText();
                    if (text instanceof Spanned) {
                        // Create a new SpannableString that preserves formatting but forces black color
                        SpannableString newText = new SpannableString(text);
                        newText.setSpan(new ForegroundColorSpan(Color.BLACK), 
                            0, newText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                        textView.setText(newText);
                    }
                } else if (child instanceof ViewGroup) {
                    forceForceMakeAllTextBlack((ViewGroup) child);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error making text black: " + e.getMessage());
        }
    }
    // ----- Fix End: Add Snow Veil theme UI adjustments -----
}