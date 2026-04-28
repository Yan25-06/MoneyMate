package com.group10.moneymate.ui.transaction;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExposureState;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.common.util.concurrent.ListenableFuture;
import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentCameraBinding;

import java.io.File;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class CameraFragment extends Fragment {

    private static final int CAPTURE_JPEG_QUALITY = 95;

    public static final String REQUEST_KEY_CAPTURED_IMAGE = "camera_fragment_captured_image";
    public static final String RESULT_KEY_IMAGE_PATH = "image_path";
    public static final String RESULT_KEY_IMAGE_URI = "image_uri";

    private FragmentCameraBinding binding;
    @Nullable
    private ProcessCameraProvider cameraProvider;
    @Nullable
    private Camera activeCamera;
    @Nullable
    private ImageCapture imageCapture;
    @Nullable
    private ScaleGestureDetector zoomGestureDetector;
    private boolean isCaptureInProgress;
    private ActivityResultLauncher<String> cameraPermissionLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        showPreviewState();
                        startCameraPreview();
                    } else {
                        showPermissionState();
                    }
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCameraBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.btnCameraBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.btnRequestCameraPermission.setOnClickListener(v -> requestCameraPermission());
        binding.btnCameraCapture.setOnClickListener(v -> captureReceiptImage());
        binding.btnExposureDown.setOnClickListener(v -> adjustExposureBy(-1));
        binding.btnExposureReset.setOnClickListener(v -> resetExposure());
        binding.btnExposureUp.setOnClickListener(v -> adjustExposureBy(1));

        if (hasCameraPermission()) {
            showPreviewState();
            startCameraPreview();
        } else {
            requestCameraPermission();
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
    }

    private void showPermissionState() {
        if (binding == null) {
            return;
        }
        binding.layoutCameraPermissionState.setVisibility(View.VISIBLE);
        binding.previewCamera.setVisibility(View.INVISIBLE);
        binding.viewReceiptGuide.setVisibility(View.INVISIBLE);
        binding.layoutCameraControls.setVisibility(View.GONE);
        binding.btnCameraCapture.setEnabled(false);
        binding.btnCameraCapture.setText(R.string.transaction_scan_capture);
        binding.tvCameraPermissionTitle.setText(R.string.transaction_scan_permission_title);
        binding.tvCameraPermissionMessage.setText(R.string.transaction_scan_permission_message);
        binding.btnRequestCameraPermission.setText(R.string.transaction_scan_permission_action);
        binding.tvCameraStatus.setText(R.string.transaction_scan_permission_denied_message);
    }

    private void showPreviewState() {
        if (binding == null) {
            return;
        }
        binding.layoutCameraPermissionState.setVisibility(View.GONE);
        binding.previewCamera.setVisibility(View.VISIBLE);
        binding.viewReceiptGuide.setVisibility(View.VISIBLE);
        binding.layoutCameraControls.setVisibility(View.VISIBLE);
        binding.btnCameraCapture.setEnabled(false);
        binding.btnExposureDown.setEnabled(false);
        binding.btnExposureReset.setEnabled(false);
        binding.btnExposureUp.setEnabled(false);
        binding.btnCameraCapture.setText(R.string.transaction_scan_capture);
        binding.tvCameraStatus.setText(R.string.transaction_scan_camera_loading);
    }

    private void startCameraPreview() {
        ListenableFuture<ProcessCameraProvider> providerFuture =
                ProcessCameraProvider.getInstance(requireContext());
        providerFuture.addListener(() -> {
            try {
                cameraProvider = providerFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException exception) {
                showCameraUnavailableState();
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindCameraUseCases() {
        if (binding == null || cameraProvider == null) {
            return;
        }

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.previewCamera.getSurfaceProvider());

        int targetRotation = resolveTargetRotation();
        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setJpegQuality(CAPTURE_JPEG_QUALITY)
                .setTargetRotation(targetRotation)
                .build();

        try {
            cameraProvider.unbindAll();
            activeCamera = cameraProvider.bindToLifecycle(
                    getViewLifecycleOwner(),
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
            );
            bindPreviewGestures();
            syncExposureControls();
            binding.btnCameraCapture.setEnabled(true);
            binding.tvCameraStatus.setText(R.string.transaction_scan_camera_ready);
        } catch (RuntimeException exception) {
            showCameraUnavailableState();
        }
    }

    private void showCameraUnavailableState() {
        if (binding == null) {
            return;
        }
        binding.layoutCameraPermissionState.setVisibility(View.VISIBLE);
        binding.previewCamera.setVisibility(View.INVISIBLE);
        binding.viewReceiptGuide.setVisibility(View.INVISIBLE);
        binding.layoutCameraControls.setVisibility(View.GONE);
        binding.btnCameraCapture.setEnabled(false);
        binding.btnCameraCapture.setText(R.string.transaction_scan_capture);
        binding.tvCameraStatus.setText(R.string.transaction_scan_camera_unavailable);
        binding.tvCameraPermissionTitle.setText(R.string.transaction_scan_camera_unavailable_title);
        binding.tvCameraPermissionMessage.setText(R.string.transaction_scan_camera_unavailable_message);
        binding.btnRequestCameraPermission.setText(R.string.transaction_scan_camera_retry);
    }

    private void captureReceiptImage() {
        if (binding == null || imageCapture == null || isCaptureInProgress) {
            return;
        }
        imageCapture.setTargetRotation(resolveTargetRotation());

        File outputFile = createReceiptImageFile();
        if (outputFile == null) {
            Toast.makeText(requireContext(), R.string.transaction_scan_capture_storage_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        isCaptureInProgress = true;
        binding.btnCameraCapture.setEnabled(false);
        binding.btnCameraCapture.setText(R.string.transaction_scan_capture_in_progress);
        binding.tvCameraStatus.setText(R.string.transaction_scan_capture_in_progress);

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(outputFile).build();

        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        isCaptureInProgress = false;
                        deliverCaptureResult(outputFile);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        isCaptureInProgress = false;
                        if (binding != null) {
                            binding.btnCameraCapture.setEnabled(true);
                            binding.btnCameraCapture.setText(R.string.transaction_scan_capture);
                            binding.tvCameraStatus.setText(R.string.transaction_scan_camera_ready);
                        }
                        Toast.makeText(requireContext(), R.string.transaction_scan_capture_failed, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Nullable
    private File createReceiptImageFile() {
        File receiptDirectory = new File(requireContext().getFilesDir(), "receipts");
        if (!receiptDirectory.exists() && !receiptDirectory.mkdirs()) {
            return null;
        }
        return new File(
                receiptDirectory,
                "receipt_" + System.currentTimeMillis() + ".jpg"
        );
    }

    private int resolveTargetRotation() {
        if (binding == null || binding.previewCamera.getDisplay() == null) {
            return Surface.ROTATION_0;
        }
        return binding.previewCamera.getDisplay().getRotation();
    }

    private void bindPreviewGestures() {
        if (binding == null || activeCamera == null) {
            return;
        }
        zoomGestureDetector = new ScaleGestureDetector(
                requireContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        if (activeCamera == null || detector == null) {
                            return false;
                        }
                        float scaleFactor = detector.getScaleFactor();
                        Float currentZoom = activeCamera.getCameraInfo().getZoomState().getValue() != null
                                ? activeCamera.getCameraInfo().getZoomState().getValue().getLinearZoom()
                                : 0f;
                        float targetZoom = clamp(currentZoom + ((scaleFactor - 1f) * 0.6f), 0f, 1f);
                        activeCamera.getCameraControl().setLinearZoom(targetZoom);
                        return true;
                    }
                }
        );

        binding.previewCamera.setOnTouchListener((view, motionEvent) -> {
            if (motionEvent == null) {
                return false;
            }
            if (zoomGestureDetector != null) {
                zoomGestureDetector.onTouchEvent(motionEvent);
            }
            if (motionEvent.getActionMasked() == MotionEvent.ACTION_UP
                    && (zoomGestureDetector == null || !zoomGestureDetector.isInProgress())) {
                startFocusAndMetering(motionEvent.getX(), motionEvent.getY());
            }
            return true;
        });
    }

    private void startFocusAndMetering(float x, float y) {
        if (binding == null || activeCamera == null) {
            return;
        }
        MeteringPointFactory meteringPointFactory = binding.previewCamera.getMeteringPointFactory();
        MeteringPoint meteringPoint = meteringPointFactory.createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(
                meteringPoint,
                FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE
        )
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build();

        binding.tvCameraStatus.setText(R.string.transaction_scan_camera_focusing);
        activeCamera.getCameraControl().startFocusAndMetering(action)
                .addListener(
                        () -> {
                            if (binding != null) {
                                binding.tvCameraStatus.setText(R.string.transaction_scan_camera_focus_locked);
                            }
                        },
                        ContextCompat.getMainExecutor(requireContext())
                );
    }

    private void syncExposureControls() {
        if (binding == null || activeCamera == null) {
            return;
        }
        ExposureState exposureState = activeCamera.getCameraInfo().getExposureState();
        int minIndex = exposureState.getExposureCompensationRange().getLower();
        int maxIndex = exposureState.getExposureCompensationRange().getUpper();
        int currentIndex = exposureState.getExposureCompensationIndex();

        binding.btnExposureDown.setEnabled(minIndex < maxIndex && currentIndex > minIndex);
        binding.btnExposureReset.setEnabled(minIndex < maxIndex && currentIndex != 0);
        binding.btnExposureUp.setEnabled(minIndex < maxIndex && currentIndex < maxIndex);
    }

    private void adjustExposureBy(int delta) {
        if (binding == null || activeCamera == null) {
            return;
        }
        ExposureState exposureState = activeCamera.getCameraInfo().getExposureState();
        int currentIndex = exposureState.getExposureCompensationIndex();
        int targetIndex = currentIndex + delta;
        int minIndex = exposureState.getExposureCompensationRange().getLower();
        int maxIndex = exposureState.getExposureCompensationRange().getUpper();
        if (targetIndex < minIndex || targetIndex > maxIndex) {
            return;
        }
        activeCamera.getCameraControl().setExposureCompensationIndex(targetIndex)
                .addListener(this::syncExposureControls, ContextCompat.getMainExecutor(requireContext()));
        binding.tvCameraStatus.setText(getString(R.string.transaction_scan_camera_exposure_changed, targetIndex));
    }

    private void resetExposure() {
        if (binding == null || activeCamera == null) {
            return;
        }
        activeCamera.getCameraControl().setExposureCompensationIndex(0)
                .addListener(this::syncExposureControls, ContextCompat.getMainExecutor(requireContext()));
        binding.tvCameraStatus.setText(R.string.transaction_scan_camera_exposure_reset);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private void deliverCaptureResult(@NonNull File outputFile) {
        Bundle result = new Bundle();
        result.putString(RESULT_KEY_IMAGE_PATH, outputFile.getAbsolutePath());
        result.putString(RESULT_KEY_IMAGE_URI, Uri.fromFile(outputFile).toString());
        getParentFragmentManager().setFragmentResult(REQUEST_KEY_CAPTURED_IMAGE, result);

        if (binding == null) {
            return;
        }
        NavController navController = Navigation.findNavController(binding.getRoot());
        navController.navigateUp();
    }

    @Override
    public void onDestroyView() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        activeCamera = null;
        zoomGestureDetector = null;
        imageCapture = null;
        cameraProvider = null;
        super.onDestroyView();
        binding = null;
    }
}
