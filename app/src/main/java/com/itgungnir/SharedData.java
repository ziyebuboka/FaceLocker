package com.itgungnir;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

import com.itgungnir.activity.ResultActivity;

import java.io.File;

public class SharedData {

    public static String person_name; // 记录面部比较时的最相似的人的用户名
    public static double similarity; // 记录面部比较时的最相似的人的相似度

    /**
     * 拍照时倒计时的毫秒数
     */
    public static final int CAPTURE_LEFT_TIME = 10000;
    /**
     * 人脸识别的标准相似度
     */
    public static final double STANTARD_SIMILARITY = 60;
    /**
     * 在 Face++ 平台上的 API KEY
     */
    public static final String API_KEY = "2be72478722eb23f211187335d83e8ea";
    /**
     * 在 Face++ 平台上的 API SECRET
     */
    public static final String API_SECRET = "GjeR3oJhA5L74r5Pf0634P8GC0RiEITG ";
    /**
     * 访问第三方平台操作成功的校验码
     */
    public static final int OPERATION_SUCCESS = 0x111;
    /**
     * 访问第三方平台操作失败的校验码
     */
    public static final int OPERATION_ERROR = 0x112;
    /**
     * 图片压缩的目标大小
     */
    public static final float STANDARD_PICTURE_SIZE = 2048f;

    /**
     * 弹出Toast
     */
    public static void popToast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    /**
     * 压缩图片
     */
    public static Bitmap compressPicture(String filePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true; // 不加载，只是获取到图片
        BitmapFactory.decodeFile(filePath, options); // options中存储了图片的宽度和高度
        double ratio = Math.max(options.outWidth * 1.0d / STANDARD_PICTURE_SIZE, options.outHeight * 1.0d / STANDARD_PICTURE_SIZE);
        options.inSampleSize = (int) Math.ceil(ratio);
        options.inJustDecodeBounds = false;
        Bitmap photoBm = BitmapFactory.decodeFile(filePath, options);
        // 旋转图片（拍照保存后，照片会左转90度，因此需要右转90度）
        Matrix matrix = new Matrix();
        matrix.reset();
        matrix.postRotate(-90); // 顺时针转，如果想左转则需要设置为负数
        photoBm = Bitmap.createBitmap(photoBm, 0, 0, photoBm.getWidth(), photoBm.getHeight(), matrix, true);
        return photoBm;
    }

    /**
     * 删除文件
     */
    public static void deleteFile(Context context, File file) {
        if (file.exists()) { // 判断文件是否存在
            if (file.isFile()) { // 判断是否是文件
                file.delete(); // 删除
            } else if (file.isDirectory()) { // 否则如果它是一个目录
                File files[] = file.listFiles(); // 声明目录下所有的文件 files[];
                for (int i = 0; i < files.length; i++) { // 遍历目录下所有的文件
                    deleteFile(context, files[i]); // 把每个文件 用这个方法进行迭代
                }
            }
        } else {
            SharedData.popToast(context, "文件操作失败！");
        }
    }
}