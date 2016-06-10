package com.itgungnir.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.itgungnir.SharedData;
import com.itgungnir.tools.DBHelper;
import com.itgungnir.tools.FaceppUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class RegisterActivity extends Activity implements View.OnClickListener {
    // 全局变量——控件
    private ImageView photo;
    private EditText inputname;
    private Button abort;
    private Button confirm;
    private RelativeLayout progress; // 进度条
    // 全局变量——Handler
    private Handler detectFaceHandler; // 检测完成后传入此Handler
    private Handler createPersonHandler;
    // 全局变量——其他
    private Bitmap photoBm;
    private Paint paint;
    private String person_name;
    private String face_id;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        initView();
        initData();
        initEvent();
    }

    private void initView() {
        photo = (ImageView) findViewById(R.id.find_register_iv_photo);
        inputname = (EditText) findViewById(R.id.find_register_et_inputname);
        abort = (Button) findViewById(R.id.find_register_btn_abort);
        confirm = (Button) findViewById(R.id.find_register_btn_confirm);
        progress = (RelativeLayout) findViewById(R.id.find_register_rvly_progress);
        inputname.setEnabled(false);
        abort.setEnabled(false);
        confirm.setEnabled(false);
    }

    private void initData() {
        paint = new Paint();
        progress.setVisibility(View.VISIBLE);
        // 初始化本页面中的所有Handler
        initHandlers();
        // 压缩图片（Face++ SDK最大容忍的图片大小是3M）并放到ImageView中
        String filePath = getIntent().getStringExtra("filePath");
        photoBm = SharedData.compressPicture(filePath);
        // 获取检测的结果，在图片上识别人脸，画方框标出人脸
        FaceppUtil.detectFace(RegisterActivity.this, photoBm, new FaceppUtil.Callback() {
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

    @SuppressLint("HandlerLeak")
    private void initHandlers() {
        // 检测人脸的Hanlder
        detectFaceHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case SharedData.OPERATION_SUCCESS:
                        JSONObject jsonObject = (JSONObject) msg.obj;
                        // 解析传回来的JSONObject对象，绘制人脸位置的框（在ptotoBm中绘制）
                        Bitmap bitmap = Bitmap.createBitmap(photoBm.getWidth(), photoBm.getHeight(), photoBm.getConfig());
                        Canvas canvas = new Canvas(bitmap);
                        canvas.drawBitmap(photoBm, 0, 0, null);
                        try {
                            JSONArray jsonArray = jsonObject.getJSONArray("face");
                            if (jsonArray == null) {
                                SharedData.popToast(RegisterActivity.this, "人脸识别失败，请重新拍照！");
                            } else {
                                if (jsonArray.length() == 0) {
                                    SharedData.popToast(RegisterActivity.this, "人脸识别失败，请重新拍照！");
                                } else {
                                    JSONObject face = jsonArray.getJSONObject(0); // 直接获取第一张脸
                                    // 获取face_id
                                    face_id = face.getString("face_id");
                                    // 获取人脸位置的JSONObject
                                    JSONObject pos = face.getJSONObject("position");
                                    // 获取脸中心点的X、Y坐标在总宽度/高度上占的比重
                                    float x = (float) pos.getJSONObject("center").getDouble("x");
                                    float y = (float) pos.getJSONObject("center").getDouble("y");
                                    // 获取脸的宽度和高度
                                    float w = (float) pos.getDouble("width");
                                    float h = (float) pos.getDouble("height");
                                    // 将百分比转化成实际的像素值
                                    x = x / 100 * bitmap.getWidth();
                                    y = y / 100 * bitmap.getHeight();
                                    w = w / 100 * bitmap.getWidth();
                                    h = h / 100 * bitmap.getHeight();
                                    // 在Canvas画布上画一个矩形标记出脸部的位置
                                    paint.setColor(0xFFFFFFFF);
                                    paint.setStrokeWidth(3);
                                    canvas.drawLine(x - w / 2, y - h / 2, x - w / 2, y + h / 2, paint);
                                    canvas.drawLine(x - w / 2, y - h / 2, x + w / 2, y - h / 2, paint);
                                    canvas.drawLine(x + w / 2, y - h / 2, x + w / 2, y + h / 2, paint);
                                    canvas.drawLine(x - w / 2, y + h / 2, x + w / 2, y + h / 2, paint);
                                    // 将绘制好的矩形添加到要显示的BitMap上
                                    photoBm = bitmap;
                                    photo.setImageBitmap(photoBm);
                                    // 重置控件状态
                                    inputname.setEnabled(true);
                                    confirm.setEnabled(true);
                                }
                            }
                        } catch (JSONException e) {
                            SharedData.popToast(RegisterActivity.this, "人脸识别失败，请重新拍照！");
                            abort.setEnabled(true);
                        }
                        photo.setImageBitmap(photoBm);
                        break;
                    case SharedData.OPERATION_ERROR:
                        String errorMsg = (String) msg.obj;
                        SharedData.popToast(RegisterActivity.this, errorMsg);
                        break;
                }
                progress.setVisibility(View.GONE);
                abort.setEnabled(true);
                super.handleMessage(msg);
            }
        };
        // 创建Person的Handler
        createPersonHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                // 2、如果添加person成功，则将用户信息添加到SQLite数据库中（存储face_id和person_name）
                switch (msg.what) {
                    case SharedData.OPERATION_SUCCESS:
                        JSONObject result = (JSONObject) msg.obj;
                        try {
                            int added_face = result.getInt("added_face");
                            String person_name_temp = result.getString("person_name");
                            if (added_face > 0 && person_name_temp.equals(person_name)) {
                                // 添加数据到SQLite数据库
                                DBHelper dbHelper = new DBHelper(RegisterActivity.this);
                                SQLiteDatabase db = dbHelper.getWritableDatabase();
                                db.execSQL("insert into person values('" + person_name + "','" + face_id + "')");
                                db.close();
                                // 跳转到“注册成功”的界面
                                Intent intent = new Intent();
                                intent.setClass(RegisterActivity.this, ResultActivity.class);
                                intent.putExtra("result", "注册成功，请返回首页登录！");
                                intent.putExtra("require", "none");
                                startActivity(intent);
                                FaceppUtil.deleteUselessPersons();
                                finish();
                            } else {
                                Toast.makeText(RegisterActivity.this, "创建用户失败，请重新拍照！", Toast.LENGTH_SHORT).show();
                                inputname.setEnabled(false);
                                abort.setEnabled(true);
                                confirm.setEnabled(false);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                    case SharedData.OPERATION_ERROR:
                        String errorMsg = (String) msg.obj;
                        Toast.makeText(RegisterActivity.this, "创建用户失败，请重新拍照！" + errorMsg, Toast.LENGTH_SHORT).show();
                        inputname.setEnabled(false);
                        abort.setEnabled(true);
                        confirm.setEnabled(false);
                        break;
                }
                progress.setVisibility(View.GONE);
                super.handleMessage(msg);
            }
        };
    }

    private void initEvent() {
        abort.setOnClickListener(this);
        confirm.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.find_register_btn_abort:
                Intent intent = new Intent();
                intent.setClass(RegisterActivity.this, CaptureActivity.class);
                intent.putExtra("function", "register");
                startActivity(intent);
                FaceppUtil.deleteUselessPersons();
                finish();
                break;
            case R.id.find_register_btn_confirm:
                person_name = inputname.getEditableText().toString().trim();
                if (person_name.equals("")) {
                    SharedData.popToast(RegisterActivity.this, "用户名不能为空！");
                } else {
                    progress.setVisibility(View.VISIBLE);
                    // 调用person/create添加一个person到云端（需要person_name和face_id）
                    FaceppUtil.createPerson(RegisterActivity.this, person_name, face_id, new FaceppUtil.Callback() {
                        @Override
                        public void success(Object result) {
                            Message msg = Message.obtain();
                            msg.what = SharedData.OPERATION_SUCCESS;
                            msg.obj = result;
                            createPersonHandler.sendMessage(msg);
                        }

                        @Override
                        public void error(String warningStr) {
                            Message msg = Message.obtain();
                            msg.what = SharedData.OPERATION_ERROR;
                            msg.obj = warningStr;
                            createPersonHandler.sendMessage(msg);
                        }
                    });
                }
                break;
        }
    }

    @Override
    public void onBackPressed() {
        FaceppUtil.deleteUselessPersons();
        SharedData.deleteFile(RegisterActivity.this, RegisterActivity.this.getFilesDir());
        super.onBackPressed();
    }
}