package com.itgungnir.tools;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.facepp.error.FaceppParseException;
import com.facepp.http.HttpRequests;
import com.facepp.http.PostParameters;
import com.itgungnir.SharedData;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;

public class FaceppUtil {

    // 回调接口，在返回的数据是任意Object时调用
    public interface Callback {
        // 方法执行成功时调用
        void success(Object result);

        // 方法执行失败时调用
        void error(String warningStr);
    }

    // 检测人脸
    public static void detectFace(final Activity activity, final Bitmap bm, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isNetworkAvailable(activity)) {
                        callback.error("当前网络不可用，请联网之后重试！");
                    } else {
                        // 初始化请求对象
                        HttpRequests requests = new HttpRequests(SharedData.API_KEY, SharedData.API_SECRET, true, true);
                        // 将Bitmap转换成字节数组
                        ByteArrayOutputStream stream = new ByteArrayOutputStream();
                        float scale = Math.min(1, Math.min(600f / bm.getWidth(), 600f / bm.getHeight()));
                        Matrix matrix = new Matrix();
                        matrix.postScale(scale, scale);
                        Bitmap bmSmall = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, false);
                        bmSmall.compress(Bitmap.CompressFormat.JPEG, 100, stream);
                        // 为post请求配置参数
                        byte[] bitmapArray = stream.toByteArray();
                        PostParameters parameters = new PostParameters();
                        parameters.setImg(bitmapArray);
                        JSONObject jsonObject = requests.detectionDetect(parameters);
                        // 处理返回结果，启用回调
                        callback.success(jsonObject);
                    }
                } catch (FaceppParseException e) {
                    e.printStackTrace();
                    callback.error("检测人脸失败，请重新拍照！");
                }
            }
        }).start();
    }

    // 创建Person
    public static void createPerson(final Activity activity, final String person_name, final String face_id, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isNetworkAvailable(activity)) {
                        callback.error("当前网络不可用，请联网之后重试！");
                    } else {
                        // 初始化请求对象
                        HttpRequests requests = new HttpRequests(SharedData.API_KEY, SharedData.API_SECRET, true, true);
                        // 为post请求配置参数
                        PostParameters parameters = new PostParameters();
                        parameters.setPersonName(person_name);
                        parameters.setFaceId(face_id);
                        parameters.setTag("usable");
                        JSONObject jsonObject = requests.personCreate(parameters);
                        // 处理返回结果，启用回调
                        callback.success(jsonObject);
                    }
                } catch (FaceppParseException e) {
                    e.printStackTrace();
                    callback.error("创建用户失败，请重试！");
                }
            }
        }).start();
    }

    // 比较两条面部数据的相似度
    public synchronized static void compareFace(final Activity activity, final String faceid_1, final String faceid_2, final String person_name, final boolean isLastOne, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isNetworkAvailable(activity)) {
                        callback.error("当前网络不可用，请联网之后重试！");
                    } else {
                        // 初始化请求对象
                        HttpRequests requests = new HttpRequests(SharedData.API_KEY, SharedData.API_SECRET, true, true);
                        // 为post请求配置参数
                        PostParameters parameters = new PostParameters();
                        parameters.setFaceId1(faceid_1);
                        parameters.setFaceId2(faceid_2);
                        JSONObject jsonObject = requests.recognitionCompare(parameters);
                        double similarity = jsonObject.getDouble("similarity");
                        if (similarity > SharedData.similarity) {
                            SharedData.similarity = similarity;
                            SharedData.person_name = person_name;
                        }
                        if (isLastOne) {
                            callback.success(jsonObject);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.error("人脸对比操作失败，请重试！");
                }
            }
        }).start();
    }

    // 查询APP中所有的Person数据
    public static void queryPersons(final Activity activity, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isNetworkAvailable(activity)) {
                        callback.error("当前网络不可用，请联网之后重试！");
                    } else {
                        // 初始化请求对象
                        HttpRequests requests = new HttpRequests(SharedData.API_KEY, SharedData.API_SECRET, true, true);
                        // 获取PersonList的JSONObject对象
                        JSONObject jsonObject = requests.infoGetPersonList();
                        JSONArray personArray = jsonObject.getJSONArray("person");
                        callback.success(personArray);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.error("从云端获取用户信息失败，请检查当前网络是否可用！");
                }
            }
        }).start();
    }

    // 查询Person的Faceid
    public synchronized static void getFaceid(final Activity activity, final String person_name, final Callback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (!isNetworkAvailable(activity)) {
                        callback.error("当前网络不可用，请联网之后重试！");
                    } else {
                        // 初始化请求对象
                        HttpRequests requests = new HttpRequests(SharedData.API_KEY, SharedData.API_SECRET, true, true);
                        // 在参数中添加personname
                        PostParameters parameters = new PostParameters();
                        parameters.setPersonName(person_name);
                        // 请求数据返回结果
                        JSONObject person = requests.personGetInfo(parameters);
                        JSONArray faces = person.getJSONArray("face");
                        if (faces != null) {
                            String face_id = faces.getJSONObject(0).getString("face_id");
                            HashMap<String, String> hashMap = new HashMap<String, String>();
                            hashMap.put("person_name", person_name);
                            hashMap.put("face_id", face_id);
                            callback.success(hashMap);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    callback.error("从云端获取用户面部信息失败，请稍后重试！");
                }
            }
        }).start();
    }

    // 删除没有用的Person数据
    public static void deleteUselessPersons() {
        try {
            // 初始化请求对象
            HttpRequests requests = new HttpRequests(SharedData.API_KEY, SharedData.API_SECRET, true, true);
            JSONObject jsonObject = requests.infoGetPersonList();
            JSONArray persons = jsonObject.getJSONArray("person");
            for (int i = 0; i < persons.length(); i++) {
                JSONObject person = persons.getJSONObject(i);
                if (person.getString("tag") == null) {
                    String person_id = persons.getJSONObject(i).getString("person_id");
                    PostParameters parameters = new PostParameters();
                    parameters.setPersonId(person_id);
                    requests.personDelete(parameters);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 检查当前网络是否可用
    public static boolean isNetworkAvailable(Activity activity) {
        Context context = activity.getApplicationContext();
        // 获取手机所有连接管理对象（包括对wi-fi,net等连接的管理）
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) {
            return false;
        } else { // 获取NetworkInfo对象
            NetworkInfo[] networkInfo = connectivityManager.getAllNetworkInfo();
            if (networkInfo != null && networkInfo.length > 0) {
                for (int i = 0; i < networkInfo.length; i++) { // 判断当前网络状态是否为连接状态
                    if (networkInfo[i].getState() == NetworkInfo.State.CONNECTED) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}