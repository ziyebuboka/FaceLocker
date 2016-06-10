package com.itgungnir.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.itgungnir.SharedData;
import com.itgungnir.tools.DBHelper;
import com.itgungnir.tools.FaceppUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

public class ResultActivity extends Activity {
    // 全局变量——控件
    private TextView result;
    private Button back;
    private RelativeLayout progress;
    // 全局变量——Handler
    private Handler detectFaceHandler; // 检测完成后传入此Handler
    private Handler faceCompareDoneHandler; // 面部比较的Handler
    // 全局变量——其他
    private Bitmap photoBm;
    private String faceid_1;
    private String faceid_2;
    private String resultStr = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);
        initView();
        initData();
        initEvent();
    }

    private void initView() {
        result = (TextView) findViewById(R.id.find_result_tv_result);
        back = (Button) findViewById(R.id.find_result_btn_back);
        progress = (RelativeLayout) findViewById(R.id.find_result_rvly_progress);
    }

    /**
     * 从之前的Activity中获取操作的结果，显示在本Activity中的TextView中
     */
    private void initData() {
        String require = getIntent().getStringExtra("require");
        if (require.equals("none")) {
            resultStr = getIntent().getStringExtra("result");
            result.setText(resultStr);
        } else if (require.equals("compare")) {
            progress.setVisibility(View.VISIBLE);
            back.setEnabled(false);
            initHandlers();
            // 压缩图片（Face++ SDK最大容忍的图片大小是3M）并放到ImageView中
            String filePath = getIntent().getStringExtra("filePath");
            photoBm = SharedData.compressPicture(filePath);
            // 获取检测的结果，在图片上识别人脸，画方框标出人脸
            FaceppUtil.detectFace(ResultActivity.this, photoBm, new FaceppUtil.Callback() {
                @Override
                public void success(Object result) {
                    Message msg = Message.obtain();
                    msg.what = SharedData.OPERATION_SUCCESS;
                    msg.obj = result;
                    detectFaceHandler.sendMessage(msg);
                }

                @Override
                public void error(String warningStr) {
                    Message msg = Message.obtain();
                    msg.what = SharedData.OPERATION_ERROR;
                    msg.obj = warningStr;
                    detectFaceHandler.sendMessage(msg);
                }
            });
        }
    }

    @SuppressLint("HandlerLeak")
    private void initHandlers() {
        // 检测人靓，获取faceid_1
        detectFaceHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case SharedData.OPERATION_SUCCESS:
                        JSONObject jsonObject = (JSONObject) msg.obj;
                        try {
                            JSONArray jsonArray = jsonObject.getJSONArray("face");
                            if (jsonArray == null) {
                                resultStr = "人脸识别失败，请返回首页重新拍照！";
                            } else {
                                JSONObject face = jsonArray.getJSONObject(0); // 直接获取第一张脸
                                // 获取faceid_1
                                faceid_1 = face.getString("face_id");
                                // 从数据库中逐条取出数据，比较得到用户名
                                DBHelper helper = new DBHelper(ResultActivity.this);
                                SQLiteDatabase db = helper.getReadableDatabase();
                                Cursor cursor = db.query("person", null, null, null, null, null, null);
                                SharedData.person_name = "";
                                SharedData.similarity = 0;
                                int currentCount = 0;
                                boolean isLastOne = false;
                                while (cursor.moveToNext()) {
                                    currentCount++;
                                    if (currentCount >= cursor.getCount()) {
                                        isLastOne = true;
                                    }
                                    faceid_2 = cursor.getString(1);
                                    String person_name = cursor.getString(0);
                                    FaceppUtil.compareFace(ResultActivity.this, faceid_1, faceid_2, person_name, isLastOne, new FaceppUtil.Callback() {
                                        @Override
                                        public void success(Object result) {
                                            Message msg = Message.obtain();
                                            msg.what = SharedData.OPERATION_SUCCESS;
                                            msg.obj = result;
                                            faceCompareDoneHandler.sendMessage(msg);
                                        }

                                        @Override
                                        public void error(String warningStr) {
                                            Message msg = Message.obtain();
                                            msg.what = SharedData.OPERATION_ERROR;
                                            msg.obj = warningStr;
                                            faceCompareDoneHandler.sendMessage(msg);
                                        }
                                    });

                                }
                                cursor.close();
                                db.close();
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                            result.setText("人脸识别失败，请返回首页重新拍照！");
                            progress.setVisibility(View.GONE);
                        }
                        break;
                    case SharedData.OPERATION_ERROR:
                        result.setText("人脸识别失败，请返回首页重新拍照！");
                        progress.setVisibility(View.GONE);
                        break;
                }
                back.setEnabled(true);
                super.handleMessage(msg);
            }
        };
        // 将两张脸进行面部比较
        faceCompareDoneHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case SharedData.OPERATION_SUCCESS:
                        if (SharedData.similarity >= SharedData.STANTARD_SIMILARITY) {
                            resultStr = "欢迎回来，" + SharedData.person_name;
                        } else {
                            resultStr = "您还很陌生啊，赶紧去首页注册吧！";
                        }
                        break;
                    case SharedData.OPERATION_ERROR:
                        resultStr = "人脸识别失败，请返回首页重新拍照！";
                        break;
                }
                result.setText(resultStr);
                back.setEnabled(true);
                progress.setVisibility(View.GONE);
                super.handleMessage(msg);
            }
        };
    }

    private void initEvent() {
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FaceppUtil.deleteUselessPersons();
                Intent intent = new Intent(ResultActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }

    @Override
    public void onBackPressed() {
        FaceppUtil.deleteUselessPersons();
        SharedData.deleteFile(ResultActivity.this, ResultActivity.this.getFilesDir());
        super.onBackPressed();
    }
}