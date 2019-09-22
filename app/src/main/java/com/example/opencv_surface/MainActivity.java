package com.example.opencv_surface;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity implements CameraBridgeViewBase.CvCameraViewListener2 {
    private static final String TAG = "OpenCV::Activity";


    private static final Scalar BANANA_RECT_COLOR = new Scalar(0, 255, 0, 255);
    public static final int JAVA_DETECTOR = 0;
    private Mat mRgba;
    private Mat mGray;
    private File mCascadeFile;
    private CascadeClassifier mJavaDetectorBanana;
    private int mDetectorType = JAVA_DETECTOR;
    private float mRelativeBananaSize = 0.2f;
    private int mAbsoluteBananaSize = 0;
    private CameraBridgeViewBase mOpenCvCameraView;
    double xCenter = -1;
    double yCenter = -1;
    static {
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV successfully initializated!");
        } else {
            Log.i(TAG, "OpenCV initialization failed!");
        }
    }


    private BaseLoaderCallback mLoaderCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV Loaded Successfully");

                    try {
                        InputStream is = getResources().openRawResource(R.raw.cascade);
                        File cascadeDir = getDir("cascade", Context.MODE_PRIVATE);
                        mCascadeFile = new File(cascadeDir, "cascade.xml");
                        FileOutputStream os = new FileOutputStream(mCascadeFile);

                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        is.close();
                        os.close();

                        mJavaDetectorBanana = new CascadeClassifier(mCascadeFile.getAbsolutePath());
                        if (mJavaDetectorBanana.empty()) {
                            Log.e(TAG, "Failed to load cascade classifier");
                            mJavaDetectorBanana = null;
                        } else
                            Log.i(TAG, "Loaded cascade classifier from " + mCascadeFile.getAbsolutePath());

                        cascadeDir.delete();
                    } catch (IOException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Failed to load cascade. Exception thrown: " + e);
                    }

                    mOpenCvCameraView.setCameraIndex(0);
                    mOpenCvCameraView.enableView();
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };



    private FrameLayout aView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "called onCreate");
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP_MR1) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                // Give first an explanation, if needed.
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        Manifest.permission.CAMERA)) {
                } else {
                    ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.CAMERA},
                            1);
                }
            }
        }
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.HelloOpenCvView);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);
        aView = findViewById(R.id.View);

        ViewGroup.LayoutParams params = aView.getLayoutParams();
        params.height=720;
        params.width=720;
        aView.setLayoutParams(params);
    }
    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_3_0, this, mLoaderCallBack);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }
    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null) {
            mOpenCvCameraView.disableView();
        }
    }
    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mGray = new Mat();
        mRgba = new Mat();
    }

    public void onCameraViewStopped() {
        mGray.release();
        mRgba.release();
    }


    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        mGray = inputFrame.gray();
        /*mRgba = mRgba.t();
        Core.flip(mRgba,mRgba,1);
        mGray = mGray.t();
        Core.flip(mGray,mGray,1);*/

        if (mAbsoluteBananaSize == 0) {
            int height = mGray.rows();
            if (Math.round(height * mRelativeBananaSize) > 0) {
                mAbsoluteBananaSize = Math.round(height * mRelativeBananaSize);
            }
        }

        MatOfRect bananas = new MatOfRect();

        if (mDetectorType == JAVA_DETECTOR) {
            if (mJavaDetectorBanana != null) {
                mJavaDetectorBanana.detectMultiScale(mGray, bananas, 1.1, 2, 2, //TODO: objdetect.CV_HAAR_SCALE_IMAGE)
                        new Size(mAbsoluteBananaSize, mAbsoluteBananaSize), new Size());
            }
        } else {
            Log.e(TAG, "Detection method is not selected!");
        }

        Rect[] bananasArray = bananas.toArray();
        if (bananasArray.length > 0) {
            for (int i = 0; i < bananasArray.length; i++) {
                Imgproc.rectangle(mRgba, bananasArray[i].tl(), bananasArray[i].br(), BANANA_RECT_COLOR, 3);
                xCenter = (bananasArray[i].x + bananasArray[i].width + bananasArray[i].x) / 2;
                yCenter = (bananasArray[i].y + bananasArray[i].height + bananasArray[i].y) / 2;
                System.out.println(xCenter);
            }
        }

        return mRgba;
    }

}
