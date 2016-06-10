package com.itgungnir.activity;

import android.app.Activity;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import com.itgungnir.SharedData;
import com.itgungnir.tools.FaceppUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CaptureActivity extends Activity implements SurfaceHolder.Callback {
    private TextView tip; // 顶部显示倒计时的TextView
    private SurfaceView showfield; // 实时显示相机预览图的SurfaceView
    private Camera camera = null; // 系统相机（前置摄像头）
    private SurfaceHolder holder; // SurfaceView的辅助类
    private String function = ""; // 记录从MainActivity传来的该界面的功能标识符

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture);
        initView();
        initData();
        if (camera == null) {
            camera = getCamera(); // 初始化相机
            if (holder != null) {
                startPreview(camera, holder); // 在取景框showfield中显示Camera实时预览的图像
            }
        }
    }

    /**
     * 初始化控件
     */
    private void initView() {
        tip = (TextView) findViewById(R.id.find_capture_tv_tip);
        showfield = (SurfaceView) findViewById(R.id.find_capture_sv_showfield);
        holder = showfield.getHolder(); // 获得SrufaceHolder对象
        holder.addCallback(this); // 为SurfaceHolder设置回调
    }

    /**
     * 初始化控件中的数据
     */
    private void initData() {
        function = getIntent().getStringExtra("function");
        new TimeCounter(SharedData.CAPTURE_LEFT_TIME, 1000).start(); // 倒计时5秒，5秒后自动拍照
    }

    /**
     * 获取Camera对象（前置摄像头）
     */
    private Camera getCamera() {
        int cameraCount = 0;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras(); // 获取当前设备上的相机个数]
        int camIdx = 0;
        for (camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) { // 摄像头的方位，目前有两个分别为CAMERA_FACING_FRONT前置、CAMERA_FACING_BACK后置
                try {
                    camera = Camera.open(camIdx);
                    camera.setDisplayOrientation(90);
                } catch (RuntimeException e) {
                    camera = null;
                    e.printStackTrace();
                }
                break;
            }
        }
        if (camIdx == cameraCount) {
            SharedData.popToast(CaptureActivity.this, "抱歉，您的设备不支持前置摄像头");
            FaceppUtil.deleteUselessPersons();
            SharedData.deleteFile(CaptureActivity.this, CaptureActivity.this.getFilesDir());
            finish();
        }
        return camera;
    }

    /**
     * 在取景框showfield中显示Camera实时预览的图像
     */
    private void startPreview(Camera camera, SurfaceHolder holder) {
        try {
            camera.setPreviewDisplay(holder); // 为相机绑定预览工具
            camera.setDisplayOrientation(90); // 设置相机竖屏拍摄
            camera.startPreview(); // 设置相机开始预览，并将实时预览结果投放到绑定holder的SurfaceView(showfield)中
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        startPreview(camera, this.holder);
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        camera.stopPreview(); // 关闭相机
        startPreview(camera, this.holder);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    /**
     * 拍照
     */
    private void capture() {
        // 为相机设置参数
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPictureFormat(ImageFormat.JPEG); // 将拍照的模式设置为JPEG
        parameters.setPreviewSize(800, 400); // 设置拍得的图片的大小
        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO); // 设置自动对焦（前提是摄像机支持自动对焦）
        // 正式开始拍照：当完全对焦之后才会拍照
        camera.autoFocus(new Camera.AutoFocusCallback() {
            @Override
            public void onAutoFocus(boolean success, Camera camera) {
                if (success) {
                    camera.takePicture(null, null, new Camera.PictureCallback() {
                        @Override
                        public void onPictureTaken(byte[] data, Camera camera) {
                            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
                            String fileName = formatter.format(new Date()) + ".jpg";
                            File tmpFile = new File(CaptureActivity.this.getFilesDir(), fileName);
                            try {
                                FileOutputStream fos = new FileOutputStream(tmpFile);
                                fos.write(data);
                                fos.close();
                                Intent intent = new Intent();
                                if (function.equals("register")) {
                                    intent.setClass(CaptureActivity.this, RegisterActivity.class);
                                } else if (function.equals("login")) {
                                    intent.setClass(CaptureActivity.this, ResultActivity.class);
                                    intent.putExtra("require", "compare");
                                }
                                intent.putExtra("filePath", tmpFile.getAbsolutePath());
                                startActivity(intent);
                                FaceppUtil.deleteUselessPersons();
                                finish();
                            } catch (Exception e) {
                                e.printStackTrace();
                                SharedData.popToast(CaptureActivity.this, "文件读写失败！");
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * 释放相机资源
     */
    private void releaseCamera() {
        if (camera != null) {
            camera.setPreviewCallback(null); // 将相机的回调置空，取消SurfaceView与Camera的关联
            camera.stopPreview(); // 取消相机的取景功能
            camera.release(); // 释放相机所占用的资源
            camera = null;
        }
    }

    /**
     * 当Activity失去焦点时，释放相机资源
     */
    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();
    }

    /**
     * 倒计时工具类
     */
    class TimeCounter extends CountDownTimer {
        /**
         * @param millisInFuture    还剩多长时间（单位：毫秒）
         * @param countDownInterval 每次时间变化的间隔（单位：毫秒）
         */
        public TimeCounter(long millisInFuture, long countDownInterval) {
            super(millisInFuture, countDownInterval);
        }

        @Override
        public void onTick(long millisUntilFinished) {
            tip.setText("请调整摄像头位置，尽量提高拍摄亮度，" + millisUntilFinished / 1000 + " 秒后自动拍照");
        }

        @Override
        public void onFinish() {
            capture();
        }
    }

    @Override
    public void onBackPressed() {
        // NONE
    }
}