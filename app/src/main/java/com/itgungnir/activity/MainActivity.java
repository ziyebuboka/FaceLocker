package com.itgungnir.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.itgungnir.SharedData;
import com.itgungnir.tools.DBHelper;
import com.itgungnir.tools.FaceppUtil;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class MainActivity extends Activity implements View.OnClickListener {
    // 全局变量——控件
    private Button register; // 点击进行面部采样，结合用户输入的用户名信息，加入到数据库中
    private Button login; // 点击进行面部采样，判断是否注册过
    private RelativeLayout progress; // 进度条
    // 全局变量——Handler
    private Handler queryPersonsHandler; // 查询Person列表的Hanlder
    private Handler getFaceidHandler; // 获取Person的Faceid的Handler
    // 全局变量——其他
    private SQLiteDatabase db; // SQLite数据库

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        initData();
        initEvent();
    }

    /**
     * 初始化控件（通过ID找到控件）
     */
    private void initView() {
        register = (Button) findViewById(R.id.find_main_btn_register);
        login = (Button) findViewById(R.id.find_main_btn_login);
        progress = (RelativeLayout) findViewById(R.id.find_main_rvly_progress);
    }

    /**
     * 初始化数据（将当前Face++平台中的数据都加载到SQLite数据库中）
     */
    private void initData() {
        progress.setVisibility(View.VISIBLE);
        register.setEnabled(false);
        login.setEnabled(false);
        // 初始化Handler
        initHandler();
        // 初始化数据库，同时删除残留的数据（保证数据表中没有数据）
        DBHelper helper = new DBHelper(MainActivity.this);
        db = helper.getWritableDatabase();
        db.execSQL("delete from person");
        // 从FacePP平台中获取所有Person数据，便于结合后面的Faceid数据添加到数据库中
        FaceppUtil.queryPersons(MainActivity.this, new FaceppUtil.Callback() {
            @Override
            public void success(Object result) {
                Message message = Message.obtain();
                message.what = SharedData.OPERATION_SUCCESS;
                message.obj = result;
                queryPersonsHandler.sendMessage(message);
            }

            @Override
            public void error(String warningStr) {
                Message message = Message.obtain();
                message.what = SharedData.OPERATION_ERROR;
                message.obj = warningStr;
                queryPersonsHandler.sendMessage(message);
            }
        });
    }

    /**
     * 初始化Handler
     */
    @SuppressLint("HandlerLeak")
    private void initHandler() {
        // 查询Person列表的Hanlder
        queryPersonsHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case SharedData.OPERATION_SUCCESS:
                        try {
                            JSONArray persons = (JSONArray) msg.obj;
                            if(persons.length() == 0){
                                progress.setVisibility(View.GONE);
                                register.setEnabled(true);
                                login.setEnabled(true);
                            }else {
                                for (int i = 0; i < persons.length(); i++) {
                                    JSONObject person = persons.getJSONObject(i);
                                    String person_name = person.getString("person_name");
                                    FaceppUtil.getFaceid(MainActivity.this, person_name, new FaceppUtil.Callback() {
                                        @Override
                                        public void success(Object result) {
                                            Message message = Message.obtain();
                                            message.what = SharedData.OPERATION_SUCCESS;
                                            message.obj = result;
                                            getFaceidHandler.sendMessage(message);
                                        }

                                        @Override
                                        public void error(String warningStr) {
                                            Message message = Message.obtain();
                                            message.what = SharedData.OPERATION_ERROR;
                                            message.obj = warningStr;
                                            getFaceidHandler.sendMessage(message);
                                        }
                                    });
                                }
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                    case SharedData.OPERATION_ERROR:
                        progress.setVisibility(View.GONE);
                        SharedData.popToast(MainActivity.this, (String) msg.obj);
                        break;
                }
                super.handleMessage(msg);
            }
        };
        // 获取Person的Faceid的Handler
        getFaceidHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case SharedData.OPERATION_SUCCESS:
                        HashMap<String, String> result = (HashMap<String, String>) msg.obj;
                        db.execSQL("insert into person values('" + result.get("person_name") + "','" + result.get("face_id") + "')");
                        register.setEnabled(true);
                        login.setEnabled(true);
                        break;
                    case SharedData.OPERATION_ERROR:
                        SharedData.popToast(MainActivity.this, (String) msg.obj);
                        break;
                }
                progress.setVisibility(View.GONE);
                super.handleMessage(msg);
            }
        };
    }

    /**
     * 初始化控件的动作事件（操作控件触发的事件）
     */
    private void initEvent() {
        register.setOnClickListener(this);
        login.setOnClickListener(this);
    }

    /**
     * 实现自OnClickListener的控件点击事件的监听器，点击任意控件触发
     */
    @Override
    public void onClick(View v) {
        db.close();
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        switch (v.getId()) {
            case R.id.find_main_btn_register:
                intent.putExtra("function", "register");
                break;
            case R.id.find_main_btn_login:
                intent.putExtra("function", "login");
                break;
        }
        startActivity(intent);
        FaceppUtil.deleteUselessPersons();
        finish();
    }

    @Override
    public void onBackPressed() {
        FaceppUtil.deleteUselessPersons();
        SharedData.deleteFile(MainActivity.this, MainActivity.this.getFilesDir());
        super.onBackPressed();
    }
}